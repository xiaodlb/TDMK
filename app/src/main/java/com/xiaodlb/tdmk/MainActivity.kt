package com.xiaodlb.tdmk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF333C45), onPrimary = Color.White,
                primaryContainer = Color(0xFFE4E8EB), onPrimaryContainer = Color(0xFF22292F),
                secondaryContainer = Color(0xFFE4E8EB), onSecondaryContainer = Color(0xFF22292F),
                background = Color(0xFFFAFAF9), surface = Color(0xFFFAFAF9),
                onSurface = Color(0xFF202327), onSurfaceVariant = Color(0xFF555B60),
            )) {
                NavigationShell()
            }
        }
    }
}
