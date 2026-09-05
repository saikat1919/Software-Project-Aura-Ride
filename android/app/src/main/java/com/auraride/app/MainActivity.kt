package com.auraride.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.auraride.app.ui.AuraNav
import com.auraride.app.ui.theme.AuraRideTheme

// FragmentActivity (still a ComponentActivity) so BiometricPrompt has a host for §17.1 signing.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraRideTheme {
                // targetSdk 35+ draws edge-to-edge; keep content out of the system bars.
                Surface(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        AuraNav()
                    }
                }
            }
        }
    }
}
