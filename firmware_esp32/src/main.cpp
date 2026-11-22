/**
 * ============================================================================
 *  ESP32-WROOM-32 // ROVER-MK1 - Firmware PlatformIO (Bluetooth Classic SPP)
 * ============================================================================
 *  Descripción:
 *    Controlador de tracción diferencial para carrito robótico con motores TT,
 *    comunicado bidireccionalmente con la app Android "ESP32 Rover" mediante
 *    Bluetooth Classic (Serial Port Profile - SPP).
 *
 *  Características implementadas:
 *    1. Recepción de comandos diferenciales en formato:
 *         - "L:<izq>,R:<der>\n"  (formato por defecto de la app)
 *         - "M:<izq>,<der>\n"    (formato alternativo)
 *         - "<izq>,<der>\n"      (formato CSV)
 *       Rango esperado desde la app: -255..+255 (configurable en APP_MAX_PWM).
 *
 *    2. Generación de PWM por hardware mediante el periférico LEDC del ESP32:
 *         - 4 canales independientes (Canales 0, 1, 2 y 3) en 4 pines GPIO.
 *         - Resolución de 10 bits (rango de ciclo de trabajo: 0 a 1023).
 *         - Frecuencia de 2000 Hz (2 kHz), óptima para motores DC tipo TT
 *           con puentes H comunes (L298N, DRV8833, TB6612FNG, MX1508).
 *
 *    3. Telemetría de batería en tiempo real (ESP32 -> App Android):
 *         - Lectura calibrada en milivoltios por ADC1 (GPIO 34) con filtro EMA.
 *         - Envío periódico en formato "BAT:<porcentaje>,V:<voltaje>\n".
 *
 *    4. Seguridad (Failsafe):
 *         - Detención inmediata de los 4 canales PWM si se pierde el enlace
 *           Bluetooth o si expira el temporizador Watchdog de comunicación.
 * ============================================================================
 */

#include <Arduino.h>
#include <BluetoothSerial.h>
#include <esp_arduino_version.h>

// Verifica en tiempo de compilación que el chip tenga Bluetooth Classic habilitado
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error "Bluetooth Classic no está habilitado en la configuración de esta placa ESP32."
#endif

// ============================================================================
// 1. CONFIGURACIÓN DE HARDWARE Y PINES (ESP32-WROOM-32)
// ============================================================================

// Nombre visible en el escaneo Bluetooth de la app Android.
// Nota: La app destaca automáticamente nombres que contengan "ESP32" o "ROVER".
static constexpr const char* BT_DEVICE_NAME = "ESP32_ROVER";

// Pines PWM para el puente H (4 señales independientes: 2 por cada motor).
// Se eligen GPIO 25, 26, 27 y 14 porque son pines seguros de salida (no afectan
// el arranque/boot del ESP32 ni son pines de solo entrada).
//
// Conexión típica:
//   - L298N (con jumpers ENA/ENB puestos): IN1->25, IN2->26, IN3->27, IN4->14
//   - DRV8833 / MX1508:                    AIN1->25, AIN2->26, BIN1->27, BIN2->14
static constexpr uint8_t PIN_MOTOR_L_FWD = 25; // Canal 0: Motor Izquierdo Adelante
static constexpr uint8_t PIN_MOTOR_L_REV = 26; // Canal 1: Motor Izquierdo Reversa
static constexpr uint8_t PIN_MOTOR_R_FWD = 27; // Canal 2: Motor Derecho Adelante
static constexpr uint8_t PIN_MOTOR_R_REV = 14; // Canal 3: Motor Derecho Reversa

// LED integrado de la placa DevKit v1 (indicador visual de conexión BT)
static constexpr uint8_t PIN_STATUS_LED  = 2;

// Pin ADC1 para medir el voltaje de la batería mediante un divisor resistivo.
// IMPORTANTE: Usamos GPIO 34 (ADC1_CH6) y NO pines de ADC2 (GPIO 0,2,4,12..15,25..27),
// ya que en el ESP32 el controlador ADC2 entra en conflicto con el radio Wi-Fi/BT.
static constexpr uint8_t PIN_BATTERY_ADC = 34;

// ============================================================================
// 2. CONFIGURACIÓN DEL DRIVER LEDC (PWM POR HARDWARE)
// ============================================================================

// Canales LEDC asignados (el ESP32 dispone de 16 canales de PWM por hardware: 0..15)
static constexpr uint8_t LEDC_CH_L_FWD = 0;
static constexpr uint8_t LEDC_CH_L_REV = 1;
static constexpr uint8_t LEDC_CH_R_FWD = 2;
static constexpr uint8_t LEDC_CH_R_REV = 3;

// Frecuencia PWM de 2000 Hz (2 kHz):
//   - Los motores TT tienen escobillas e inductancia moderada.
//   - Frecuencias muy bajas (<200 Hz) producen vibración y tironeo ("cogging").
//   - Frecuencias muy altas (>20 kHz) aumentan las pérdidas por conmutación en
//     drivers económicos como el L298N y reducen el torque a bajas velocidades.
//   - 2000 Hz ofrece arranque suave, buen torque y compatibilidad total con
//     la resolución de 10 bits (límite teórico del reloj APB 80MHz / 1024 = 78.1 kHz).
static constexpr uint32_t PWM_FREQ_HZ = 2000;

// Resolución de 10 bits -> valores válidos de duty cycle de 0 a 1023 (2^10 - 1)
static constexpr uint8_t  PWM_RESOLUTION_BITS = 10;
static constexpr uint16_t PWM_MAX_DUTY        = (1U << PWM_RESOLUTION_BITS) - 1U; // 1023

// Escala máxima enviada por la app Android (por defecto ±255; cambia a 100 si
// seleccionas ±100 en la consola de protocolo de la app).
static constexpr int APP_MAX_PWM = 255;

// Compensación mínima de arranque (stiction) para motores TT en escala 10 bits.
// Por debajo de ~250/1023 muchos motores TT no logran vencer la fricción interna
// de los engranajes plásticos. Si deseas salida puramente lineal, ponlo en 0.
static constexpr uint16_t TT_MIN_START_DUTY = 220;

// ============================================================================
// 3. CONFIGURACIÓN DE TELEMETRÍA DE BATERÍA Y SEGURIDAD (FAILSAFE)
// ============================================================================

// Divisor de tensión para batería 2S LiPo / Li-Ion (6.4 V descargada .. 8.4 V llena):
//   V_bat ---> [ R1 = 100 kOhm ] ---> GPIO 34 ---> [ R2 = 47 kOhm ] ---> GND
//   Factor = (R1 + R2) / R2 = (100 + 47) / 47 = 3.1276
//   Con 8.4V en batería, en el GPIO 34 habrá 2.68V (seguro, < 3.3V).
static constexpr float DIVIDER_R1_KOHM = 100.0f;
static constexpr float DIVIDER_R2_KOHM = 47.0f;
static constexpr float BATTERY_DIVIDER_FACTOR =
    (DIVIDER_R1_KOHM + DIVIDER_R2_KOHM) / DIVIDER_R2_KOHM;

// Umbrales de voltaje para cálculo de porcentaje (ajusta según tu paquete: 2S vs 3S)
static constexpr float BATTERY_MIN_VOLTS = 6.00f; // 0% (3.0V por celda en 2S)
static constexpr float BATTERY_MAX_VOLTS = 8.40f; // 100% (4.2V por celda en 2S)

// Intervalo de envío de telemetría hacia la app móvil (ms)
static constexpr unsigned long TELEMETRY_INTERVAL_MS = 1000UL;

// Tiempo máximo sin recibir tramas en movimiento antes de activar paro de seguridad.
// La app reenvía keepalive cada 250 ms mientras el joystick está activo, por lo que
// 1000 ms garantiza que una pérdida abrupta de señal detenga el robot al instante.
static constexpr unsigned long FAILSAFE_TIMEOUT_MS = 1000UL;

// ============================================================================
// 4. VARIABLES GLOBALES DE ESTADO
// ============================================================================

BluetoothSerial SerialBT;

String rxBuffer;
int currentLeftCmd  = 0;
int currentRightCmd = 0;

unsigned long lastCommandMs   = 0;
unsigned long lastTelemetryMs = 0;
float filteredBatteryVolts    = 0.0f;
bool  batteryFilterInitialized = false;

// ============================================================================
// 5. CAPA DE ABSTRACCIÓN LEDC (COMPATIBLE CON ARDUINO-ESP32 v2.x Y v3.x)
// ============================================================================

/**
 * Inicializa un canal PWM LEDC y lo asocia a un pin GPIO específico.
 * Se incluye soporte condicional para Arduino-ESP32 v2.x (PlatformIO estándar)
 * y v3.x (ESP-IDF 5.x), ya que la firma de la API cambió en la versión 3.0.
 */
void setupLedcChannel(uint8_t pin, uint8_t channel, uint32_t freqHz, uint8_t resolutionBits) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcAttachChannel(pin, freqHz, resolutionBits, channel);
#else
    ledcSetup(channel, freqHz, resolutionBits);
    ledcAttachPin(pin, channel);
#endif
}

/**
 * Escribe el ciclo de trabajo (0..1023 en 10 bits) en el canal/pin indicado.
 */
void writeLedcDuty(uint8_t pin, uint8_t channel, uint16_t duty) {
    const uint16_t safeDuty = (duty > PWM_MAX_DUTY) ? PWM_MAX_DUTY : duty;
#if ESP_ARDUINO_VERSION_MAJOR >= 3
    // En Core 3.x ledcWrite recibe el número de pin; ledcWriteChannel usa el canal.
    ledcWriteChannel(channel, safeDuty);
    (void)pin;
#else
    ledcWrite(channel, safeDuty);
    (void)pin;
#endif
}

// ============================================================================
// 6. CONTROL DE MOTORES TT (CONVERSIÓN DE ESCALA Y SENTIDO DE GIRO)
// ============================================================================

/**
 * Convierte la magnitud de velocidad enviada por la app (0..APP_MAX_PWM)
 * a la resolución de 10 bits del periférico LEDC (0..1023).
 *
 * Si speedMag == 0, devuelve 0 exacto para detener el motor sin zumbido.
 * Si speedMag > 0, mapea suavemente desde TT_MIN_START_DUTY hasta 1023
 * para garantizar que el motor TT arranque incluso con inclinaciones suaves
 * del joystick.
 */
uint16_t mapAppSpeedTo10BitDuty(int speedMag) {
    if (speedMag <= 0) {
        return 0;
    }
    const int clamped = (speedMag > APP_MAX_PWM) ? APP_MAX_PWM : speedMag;
    const uint32_t span = PWM_MAX_DUTY - TT_MIN_START_DUTY;
    const uint16_t duty = static_cast<uint16_t>(
        TT_MIN_START_DUTY + ((static_cast<uint32_t>(clamped) * span) / APP_MAX_PWM)
    );
    return (duty > PWM_MAX_DUTY) ? PWM_MAX_DUTY : duty;
}

/**
 * Aplica velocidad con signo a un par de canales LEDC (Adelante / Reversa).
 * Nunca activa ambos canales simultáneamente para evitar cortocircuitos o
 * frenado indeseado en el puente H.
 */
void driveSingleMotor(int signedSpeed,
                      uint8_t pinFwd, uint8_t chFwd,
                      uint8_t pinRev, uint8_t chRev) {
    if (signedSpeed > 0) {
        const uint16_t duty = mapAppSpeedTo10BitDuty(signedSpeed);
        writeLedcDuty(pinRev, chRev, 0);
        writeLedcDuty(pinFwd, chFwd, duty);
    } else if (signedSpeed < 0) {
        const uint16_t duty = mapAppSpeedTo10BitDuty(-signedSpeed);
        writeLedcDuty(pinFwd, chFwd, 0);
        writeLedcDuty(pinRev, chRev, duty);
    } else {
        writeLedcDuty(pinFwd, chFwd, 0);
        writeLedcDuty(pinRev, chRev, 0);
    }
}

/**
 * Limita y aplica las velocidades recibidas a ambos motores del robot.
 * Nota: la marca temporal del watchdog (lastCommandMs) no se actualiza aquí,
 * sino en processBluetoothInput() al recibir una trama válida desde la app,
 * para evitar que stopAllMotors() reinicie el temporizador del failsafe.
 */
void applyMotorCommands(int leftSpeed, int rightSpeed) {
    currentLeftCmd  = constrain(leftSpeed, -APP_MAX_PWM, APP_MAX_PWM);
    currentRightCmd = constrain(rightSpeed, -APP_MAX_PWM, APP_MAX_PWM);

    driveSingleMotor(currentLeftCmd,  PIN_MOTOR_L_FWD, LEDC_CH_L_FWD, PIN_MOTOR_L_REV, LEDC_CH_L_REV);
    driveSingleMotor(currentRightCmd, PIN_MOTOR_R_FWD, LEDC_CH_R_FWD, PIN_MOTOR_R_REV, LEDC_CH_R_REV);
}

/**
 * Detiene inmediatamente los 4 canales PWM (usado en PARO, desconexión o watchdog).
 */
void stopAllMotors() {
    applyMotorCommands(0, 0);
}

// ============================================================================
// 7. PARSER DE COMANDOS BLUETOOTH CLASSIC
// ============================================================================

/**
 * Analiza una línea recibida desde la app móvil y extrae las velocidades L y R.
 * Soporta todos los presets configurables en la app Android:
 *   1) "L:180,R:240"   (preset por defecto L_R_COLON)
 *   2) "M:180,240"     (preset M_COMMA)
 *   3) "180,240"       (preset CSV)
 *   4) "L180 R240"     (preset SPACE_L_R)
 *
 * Devuelve true si la trama fue válida y se pudieron extraer ambos enteros.
 */
bool parseMotorCommandLine(const String& rawLine, int& outLeft, int& outRight) {
    String line = rawLine;
    line.trim();
    if (line.length() == 0) {
        return false;
    }

    // Caso 1: Formato por defecto "L:<izq>,R:<der>"
    const int lColonIdx = line.indexOf("L:");
    const int rColonIdx = line.indexOf(",R:");
    if (lColonIdx >= 0 && rColonIdx > lColonIdx) {
        outLeft  = line.substring(lColonIdx + 2, rColonIdx).toInt();
        outRight = line.substring(rColonIdx + 3).toInt();
        return true;
    }

    // Caso 2: Formato "M:<izq>,<der>"
    if (line.startsWith("M:")) {
        const int commaIdx = line.indexOf(',', 2);
        if (commaIdx > 2) {
            outLeft  = line.substring(2, commaIdx).toInt();
            outRight = line.substring(commaIdx + 1).toInt();
            return true;
        }
    }

    // Caso 3: Formato "L<izq> R<der>"
    if (line.startsWith("L")) {
        const int rSpaceIdx = line.indexOf(" R");
        if (rSpaceIdx > 1) {
            outLeft  = line.substring(1, rSpaceIdx).toInt();
            outRight = line.substring(rSpaceIdx + 2).toInt();
            return true;
        }
    }

    // Caso 4: Formato CSV simple "<izq>,<der>"
    const int commaIdx = line.indexOf(',');
    if (commaIdx > 0) {
        outLeft  = line.substring(0, commaIdx).toInt();
        outRight = line.substring(commaIdx + 1).toInt();
        return true;
    }

    return false;
}

/**
 * Lee caracteres disponibles del buffer SPP sin bloquear el loop principal.
 */
void processBluetoothInput() {
    while (SerialBT.available() > 0) {
        const char c = static_cast<char>(SerialBT.read());
        if (c == '\r') {
            continue;
        }
        if (c == '\n') {
            int leftVal  = 0;
            int rightVal = 0;
            if (parseMotorCommandLine(rxBuffer, leftVal, rightVal)) {
                applyMotorCommands(leftVal, rightVal);
                lastCommandMs = millis();
            }
            rxBuffer = "";
        } else {
            // Protección contra desbordamiento de buffer por tramas corruptas
            if (rxBuffer.length() < 48) {
                rxBuffer += c;
            } else {
                rxBuffer = "";
            }
        }
    }
}

// ============================================================================
// 8. MEDICIÓN Y ENVÍO DE TELEMETRÍA DE BATERÍA EN TIEMPO REAL
// ============================================================================

/**
 * Lee el pin ADC1 calibrado de fábrica en milivoltios, aplica el factor del
 * divisor resistivo y suaviza picos de corriente de los motores usando un
 * filtro paso-bajo exponencial (EMA).
 */
float readFilteredBatteryVoltage() {
    // Promediamos 8 muestras rápidas para reducir ruido eléctrico del PWM
    uint32_t sumMilliVolts = 0;
    constexpr int SAMPLES = 8;
    for (int i = 0; i < SAMPLES; ++i) {
        sumMilliVolts += analogReadMilliVolts(PIN_BATTERY_ADC);
    }
    const float pinVolts = (static_cast<float>(sumMilliVolts) / SAMPLES) / 1000.0f;
    const float instantBatVolts = pinVolts * BATTERY_DIVIDER_FACTOR;

    if (!batteryFilterInitialized) {
        filteredBatteryVolts = instantBatVolts;
        batteryFilterInitialized = true;
    } else {
        // alpha = 0.20: responde en pocos segundos ante descarga real, ignorando
        // caídas instantáneas de milisegundos al arrancar los motores.
        filteredBatteryVolts = (0.20f * instantBatVolts) + (0.80f * filteredBatteryVolts);
    }
    return filteredBatteryVolts;
}

/**
 * Convierte el voltaje medido a porcentaje [0..100] de forma lineal acotada.
 */
int computeBatteryPercentage(float volts) {
    if (volts <= BATTERY_MIN_VOLTS) return 0;
    if (volts >= BATTERY_MAX_VOLTS) return 100;
    const float ratio = (volts - BATTERY_MIN_VOLTS) / (BATTERY_MAX_VOLTS - BATTERY_MIN_VOLTS);
    return constrain(static_cast<int>(ratio * 100.0f + 0.5f), 0, 100);
}

/**
 * Envía la cadena "BAT:<pct>,V:<volts>\n" que la app Android analiza en tiempo real
 * para actualizar el indicador segmentado de batería y la consola RX.
 */
void sendBatteryTelemetryIfDue(unsigned long nowMs) {
    if (nowMs - lastTelemetryMs < TELEMETRY_INTERVAL_MS) {
        return;
    }
    lastTelemetryMs = nowMs;

    const float volts = readFilteredBatteryVoltage();
    const int pct     = computeBatteryPercentage(volts);

    // Formato exacto esperado por TelemetryParser.kt en la app Android
    char telemetryMsg[40];
    snprintf(telemetryMsg, sizeof(telemetryMsg), "BAT:%d,V:%.2f\n", pct, volts);

    if (SerialBT.hasClient()) {
        SerialBT.print(telemetryMsg);
    }

    // Eco por USB para diagnóstico rápido sin necesidad de conectar el teléfono
    Serial.printf("[TELEMETRIA] %s", telemetryMsg);
}

// ============================================================================
// 9. SETUP Y LOOP PRINCIPAL
// ============================================================================

void setup() {
    Serial.begin(115200);
    delay(200);
    Serial.println("\n=== INICIANDO ESP32 ROVER-MK1 (PlatformIO) ===");

    pinMode(PIN_STATUS_LED, OUTPUT);
    digitalWrite(PIN_STATUS_LED, LOW);

    // Configurar atenuación ADC a 11 dB (rango lineal hasta ~3.1V en el pin)
    analogReadResolution(12);
    analogSetPinAttenuation(PIN_BATTERY_ADC, ADC_11db);

    // Inicializar los 4 canales LEDC en los 4 pines a 2000 Hz y 10 bits (0..1023)
    setupLedcChannel(PIN_MOTOR_L_FWD, LEDC_CH_L_FWD, PWM_FREQ_HZ, PWM_RESOLUTION_BITS);
    setupLedcChannel(PIN_MOTOR_L_REV, LEDC_CH_L_REV, PWM_FREQ_HZ, PWM_RESOLUTION_BITS);
    setupLedcChannel(PIN_MOTOR_R_FWD, LEDC_CH_R_FWD, PWM_FREQ_HZ, PWM_RESOLUTION_BITS);
    setupLedcChannel(PIN_MOTOR_R_REV, LEDC_CH_R_REV, PWM_FREQ_HZ, PWM_RESOLUTION_BITS);

    // Garantizar motores completamente detenidos antes de abrir el enlace Bluetooth
    stopAllMotors();

    // Iniciar servidor Bluetooth Classic SPP
    if (!SerialBT.begin(BT_DEVICE_NAME)) {
        Serial.println("[ERROR] Fallo al inicializar Bluetooth Classic SPP.");
    } else {
        Serial.printf("[BT] Listo. Empareja y conecta a \"%s\"\n", BT_DEVICE_NAME);
        Serial.printf("[LEDC] 4 canales @ %lu Hz, %u bits (duty max = %u)\n",
                      static_cast<unsigned long>(PWM_FREQ_HZ),
                      PWM_RESOLUTION_BITS,
                      PWM_MAX_DUTY);
    }

    lastCommandMs   = millis();
    lastTelemetryMs = millis();
}

void loop() {
    const unsigned long nowMs = millis();
    const bool clientConnected = SerialBT.hasClient();

    // Indicador visual de estado en el LED integrado de la placa
    digitalWrite(PIN_STATUS_LED, clientConnected ? HIGH : LOW);

    if (clientConnected) {
        // 1. Procesar comandos entrantes de dirección y velocidad desde la app
        processBluetoothInput();

        // 2. Watchdog de seguridad: si el robot estaba en movimiento y se dejó de
        //    recibir tramas durante > FAILSAFE_TIMEOUT_MS, detener motores.
        const bool isMoving = (currentLeftCmd != 0) || (currentRightCmd != 0);
        if (isMoving && (nowMs - lastCommandMs > FAILSAFE_TIMEOUT_MS)) {
            Serial.println("[FAILSAFE] Timeout de comandos BT -> Deteniendo motores.");
            stopAllMotors();
        }

        // 3. Enviar telemetría de batería en tiempo real hacia la app Android
        sendBatteryTelemetryIfDue(nowMs);
    } else {
        // Failsafe inmediato ante desconexión física o pérdida del cliente BT
        if (currentLeftCmd != 0 || currentRightCmd != 0) {
            Serial.println("[FAILSAFE] Cliente BT desconectado -> Deteniendo motores.");
            stopAllMotors();
        }
        rxBuffer = "";
        sendBatteryTelemetryIfDue(nowMs);
    }

    // Breve pausa de 2 ms para ceder tiempo al stack FreeRTOS / Bluetooth
    delay(2);
}
