package com.yachiqo.spotgo

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.yachiqo.spotgo.presentation.SpotGoApp
import com.yachiqo.spotgo.data.remote.SpotGoApi
import com.yachiqo.spotgo.data.remote.FcmDeviceRegistration
import com.yachiqo.spotgo.locale.AppLocale
import com.yachiqo.spotgo.ui.theme.SpotGoTheme

class MainActivity : ComponentActivity() {
    private val localNetworkPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                R.string.local_network_permission_required,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpotGoApi.initialize(applicationContext)
        FcmDeviceRegistration.initialize(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (
            Build.VERSION.SDK_INT >= 37 &&
            SpotGoApi.requiresLocalNetworkPermission() &&
            checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionRequest.launch("android.permission.ACCESS_LOCAL_NETWORK")
        }
        setContent {
            SpotGoTheme { SpotGoApp() }
        }
    }
}
