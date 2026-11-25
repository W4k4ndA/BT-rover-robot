# 🤖 ESP32 Rover — Controlador Bluetooth Classic & Firmware PlatformIO

[![Platform](https://img.shields.io/badge/Android-Kotlin%20%2B%20Jetpack%20Compose-3DDC84?logo=android&logoColor=white)](#)
[![MCU](https://img.shields.io/badge/ESP32-WROOM--32%20%7C%20PlatformIO-E7352C?logo=espressif&logoColor=white)](#)
[![Bluetooth](https://img.shields.io/badge/Protocol-Bluetooth%20Classic%20SPP-0082FC?logo=bluetooth&logoColor=white)](#)
[![PWM](https://img.shields.io/badge/PWM-LEDC%202ch%20%7C%2010--bit%20%40%202kHz-00F0FF)](#)

Sistema completo de robótica móvil compuesto por una **aplicación móvil Android futurista** (estética neón y chasis metálico cepillado) y un **firmware C++ en PlatformIO para ESP32-WROOM-32** comunicados por **Bluetooth Classic (SPP / RFCOMM)**.

---

## 🎯 ¿En qué consiste el proyecto?

**ESP32 Rover** conecta un teléfono Android con un carrito robótico de tracción diferencial (2 motores DC tipo TT + puente H) sin necesidad de router Wi-Fi ni servidores externos:

- 📱 **App Android (`app/`)**:
  - **Joystick Virtual Diferencial**: Calcula en tiempo real la mezcla *Arcade Drive* de los motores izquierdo y derecho (`L:<izq>,R:<der>\n`).
    - ⬆️ **Arriba**: Ambos motores avanzan.
    - ⬇️ **Abajo**: Ambos motores retroceden.
    - ⬅️ / ➡️ **Izquierda / Derecha**: Giro diferencial suave o pivote sobre su propio eje.
  - **Estética Futurista Neón + Metálica**: Tipografía técnica (*Orbitron*, *Rajdhani*, *JetBrains Mono*), biseles en titanio cepillado, retícula táctica y retroalimentación háptica con retorno elástico al soltar.
  - **Botón de Conexión Bluetooth (`CONECTAR BT`)**: Gestiona permisos de Android 11/12+, lista dispositivos emparejados (destacando módulos ESP32) y escanea dispositivos cercanos.
  - **Indicador de Batería en Tiempo Real**: Barra neón segmentada que muestra la telemetría enviada por el ESP32 (`BAT:xx,V:x.xx`) junto al nivel de batería del teléfono.
  - **Consola ESP32, Calibración & Modos de Potencia**: Selector `50% ECO`, `75% SPORT`, `100% TURBO`, botón de paro de emergencia (**PARO**), visor TX/RX en vivo y calibración por software para **invertir el giro de cada motor** e **intercambiar el motor izquierdo/derecho** sin tocar el cableado.

- 🧠 **Firmware ESP32 PlatformIO (`firmware_esp32/`)**:
  - **Driver LEDC por Hardware**: 2 canales PWM independientes (`0` y `1`) para `ENA`/`ENB` a **2000 Hz** y **10 bits de resolución (`0..1023`)**, junto a 4 pines digitales (`IN1..IN4`) para controlar el sentido de giro de motores TT en puente H L298N.
  - **Compensación de arranque (*stiction*)**: Mapeo suave desde `TT_MIN_START_DUTY` hasta `1023` para vencer la fricción de los engranajes plásticos a baja velocidad.
  - **Telemetría de batería por `ADC1`**: Lectura calibrada en milivoltios con filtro exponencial (EMA) evitando conflictos con `ADC2` cuando Bluetooth está activo.
  - **Failsafe / Watchdog**: Detiene los canales PWM y desactiva las salidas de dirección al instante ante desconexión Bluetooth o si transcurren más de `1000 ms` sin recibir tramas en movimiento.

---

## 🛠️ ¿Para qué sirve?

- 🚀 Controlar de forma inalámbrica y de baja latencia robots móviles diferenciales con **ESP32-WROOM-32**.
- 🎓 Aprender e impartir talleres de **robótica educativa**, **electrónica de potencia (puentes H y PWM)** y **desarrollo Android + sistemas embebidos**.
- 🧪 Probar y depurar tramas serie por Bluetooth Classic SPP usando la consola TX/RX integrada sin herramientas externas.

---

## 🔌 Esquema de conexiones (Hardware)

| Función | Pin ESP32-WROOM-32 | Tipo / Canal LEDC | Pin Puente H (L298N) |
| :--- | :---: | :---: | :--- |
| 🛞 Motor Derecho Dirección A | `D13` (`GPIO 13`) | Digital `OUTPUT` | `IN1` |
| 🛞 Motor Derecho Dirección B | `D12` (`GPIO 12`) | Digital `OUTPUT` | `IN2` |
| ⚡ Motor Derecho Velocidad | `D26` (`GPIO 26`) | PWM Canal `0` (`LEDC`) | `ENA` *(retirar jumper)* |
| 🛞 Motor Izquierdo Dirección A | `D14` (`GPIO 14`) | Digital `OUTPUT` | `IN3` |
| 🛞 Motor Izquierdo Dirección B | `D27` (`GPIO 27`) | Digital `OUTPUT` | `IN4` |
| ⚡ Motor Izquierdo Velocidad | `D25` (`GPIO 25`) | PWM Canal `1` (`LEDC`) | `ENB` *(retirar jumper)* |
| 🔋 Sensor Batería (Divisor `100kΩ / 47kΩ`) | `D34` (`GPIO 34`) | `ADC1_CH6` | Punto medio del divisor resistivo |
| 💡 LED Estado Bluetooth | `D2` (`GPIO 2`) | Digital `OUTPUT` | LED integrado en la placa DevKit |

> \* **Nota para L298N:** Retira los *jumpers* de `ENA` y `ENB` para conectar allí `D26` y `D25` respectivamente. Recuerda **unir las tierras (GND)** de la batería de motores, el puente H y el ESP32. Si al programar el ESP32 por USB con `D12` conectado aparece un error de flasheo, desconecta `IN2` (`D12`) momentáneamente mientras cargas el código.

---

## 🚀 Cómo se usa (Paso a paso)

### Paso 1: Cargar el firmware en el ESP32 con PlatformIO
1. Abre la carpeta `firmware_esp32/` en **VS Code con PlatformIO** (o usa la CLI `pio`).
2. Conecta tu placa **ESP32-WROOM-32** por USB y ejecuta:
   ```bash
   cd firmware_esp32
   pio run --target upload
   pio device monitor
   ```
3. Verifica en el monitor serial que aparezca:
   `[BT] Listo. Empareja y conecta a "ESP32_ROVER"` y `[LEDC] 2 canales (ENA/ENB) @ 2000 Hz, 10 bits`.

### Paso 2: Compilar e instalar la App Android
1. Abre el proyecto raíz en Android Studio / AI Studio o compila vía Gradle:
   ```bash
   gradle :app:assembleDebug
   ```
2. Instala el APK en tu teléfono Android.

### Paso 3: Emparejar y conducir
1. En el teléfono, pulsa **CONECTAR BT** en la esquina superior derecha.
2. Concede los permisos de Bluetooth y activa el adaptador si se solicita.
3. Pulsa **ESCANEAR** (o selecciona `ESP32_ROVER` en dispositivos vinculados).
4. Cuando el LED cambie a **verde neón**, desliza el **joystick virtual** para conducir y observa en tiempo real el consumo PWM de cada motor y el nivel de batería del robot.

---

## 📡 Protocolo de comunicación SPP

- **App ➡️ ESP32 (Comandos de motor ~22 Hz + Keepalive 250 ms):**
  - Por defecto: `L:<izq>,R:<der>\n` (ej. `L:210,R:255`, `L:-180,R:180`, `L:0,R:0`).
  - Formatos alternativos configurables en la pestaña **ESP32**: `M:<izq>,<der>`, `<izq>,<der>`, `L<izq> R<der>`.
  - Configuración Bluetooth persistente (NVS): `NAME:<nuevo_nombre>\n` (guarda el nombre y reinicia el ESP32) y `NAME_RESET\n` (restaura `ESP32_ROVER`).
- **ESP32 ➡️ App (Telemetría cada 1000 ms):**
  - `BAT:<porcentaje>,V:<voltaje>\n` (ej. `BAT:84,V:7.92`).
  - Confirmación de renombrado: `NAME_OK:<nombre>\n` o `NAME_ERR:<detalle>\n`.

---

## 🧪 Ejercicios y prácticas recomendadas

Una vez armado el robot y en funcionamiento, puedes realizar estas prácticas progresivas:

1. ⚖️ **Calibración de zona muerta y arranque (*Stiction*):**
   - Modifica `TT_MIN_START_DUTY` en `firmware_esp32/src/main.cpp` (entre `0` y `350`) y observa cómo cambia la suavidad del arranque lento usando el modo **50% ECO** de la app.
2. 🔄 **Corrección de deriva mecánica (Trimming):**
   - Si un motor TT gira ligeramente más rápido que el otro al ir recto (`L:255,R:255`), agrega un factor de compensación (por ejemplo `0.95f`) en `applyMotorCommands()` para que el carrito siga una línea recta perfecta.
3. 🚧 **Sensor ultrasónico anticolisión (HC-SR04):**
   - Añade un HC-SR04 al ESP32 y bloquea el avance (`leftSpeed > 0 && rightSpeed > 0`) si detecta un obstáculo a menos de `20 cm`, enviando una alerta por SerialBT hacia el visor RX de la app.
4. ⚡ **Perfilado de descarga de batería bajo carga:**
   - Registra la caída de voltaje reportada en `V:x.xx` cuando el carrito pasa de reposo a **100% TURBO** y ajusta el parámetro `alpha` del filtro exponencial EMA (`filteredBatteryVolts`) para equilibrar rapidez y estabilidad de lectura.
5. 🏁 **Circuito de slalom y control proporcional:**
   - Coloca 5 conos en el suelo y cronometra el recorrido comparando el modo **50% ECO**, **75% SPORT** y **100% TURBO**, midiendo cómo influye el límite de potencia en la precisión de las curvas cerradas.
6. 🔦 **Luces de freno y reversa automáticas:**
   - Conecta LEDs traseros a dos pines extra del ESP32 que se enciendan automáticamente cuando `currentLeftCmd < 0 && currentRightCmd < 0` (reversa) o cuando ambos pasen de movimiento a `0` (freno).
7. 🧭 **Control de rumbo en lazo cerrado con IMU (MPU6050):**
   - Integra un giroscopio MPU6050 por I2C y un controlador PID sencillo en el ESP32 para mantener el ángulo recto aunque el suelo tenga desniveles cuando la app envíe `joyX = 0`.
