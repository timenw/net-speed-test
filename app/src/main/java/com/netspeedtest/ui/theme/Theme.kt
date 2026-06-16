package com.netspeedtest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    onPrimary = White,
    primaryContainer = Blue100,
    secondary = Teal500,
    onSecondary = White,
    secondaryContainer = Teal50,
    background = Grey100,
    surface = White,
    onBackground = Grey900,
    onSurface = Grey900,
    error = Red500,
    onError = White
)

@Composable
fun NetSpeedTestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
