package com.ssu.mp.justsnapit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssu.mp.justsnapit.ui.theme.Lime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme(
                    primary = Lime, onPrimary = androidx.compose.ui.graphics.Color.Black,
                    background = androidx.compose.ui.graphics.Color.Black,
                    surface = androidx.compose.ui.graphics.Color(0xFF242424)
                )
            ) { SnapApp() }
        }
    }
}
