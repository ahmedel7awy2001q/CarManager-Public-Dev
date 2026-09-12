package com.ahmed.carmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.ahmed.carmanager.ui.CarManagerApp
import com.ahmed.carmanager.ui.theme.CarManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        requestNotificationPermissionIfNeeded()
        val appearance = (application as CarManagerApplication).container.appearancePreferences
        setContent {
            val themeMode by appearance.themeMode.collectAsState()
            val accentPalette by appearance.accentPalette.collectAsState()
            CarManagerTheme(themeMode = themeMode, accentPalette = accentPalette) {
                val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                // CarManager is an Arabic-first product. Force the app chrome and navigation into
                // RTL even when the device language itself is English; individual numeric/Latin
                // fields still use Android's normal bidi text handling.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    // Explicit adjustResize exposes the IME inset; consuming it at the app root keeps
                    // dialogs/bottom sheets above the keyboard. Individual AppField instances still
                    // use BringIntoViewRequester to scroll the focused field into view.
                    CarManagerApp(modifier = Modifier.fillMaxSize().imePadding())
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 401)
        }
    }
}
