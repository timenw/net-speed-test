package com.netspeedtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.netspeedtest.ui.MainScreen
import com.netspeedtest.ui.theme.NetSpeedTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetSpeedTestTheme {
                MainScreen()
            }
        }
    }
}
