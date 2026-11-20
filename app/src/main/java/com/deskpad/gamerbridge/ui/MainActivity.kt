package com.deskpad.gamerbridge.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.deskpad.gamerbridge.platform.Permissions

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) open(Permissions.notificationSettings(this))
        vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GamerBridgeTheme {
                BridgeRoot(
                    vm = vm,
                    onRequestNotifications = ::requestNotifications,
                    onOpen = ::open,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // as permissões podem ter mudado nas configurações do Android
        vm.refresh()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            open(Permissions.notificationSettings(this))
        }
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Permissions.appDetails(this))
            } catch (e2: ActivityNotFoundException) {
                vm.say("Não encontrei essa tela de configuração neste aparelho.")
            }
        }
    }
}
