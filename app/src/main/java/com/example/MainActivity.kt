package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.RoverControlScreen
import com.example.ui.RoverViewModel
import com.example.ui.theme.RoverControlTheme

/**
 * Actividad principal de ESP32 Rover.
 * Habilita dibujo de borde a borde (edge-to-edge) e inyecta el tema oscuro
 * robótico junto con el ViewModel compartido hacia la cabina de control.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoverControlTheme {
                val roverViewModel: RoverViewModel = viewModel()
                RoverControlScreen(viewModel = roverViewModel)
            }
        }
    }
}
