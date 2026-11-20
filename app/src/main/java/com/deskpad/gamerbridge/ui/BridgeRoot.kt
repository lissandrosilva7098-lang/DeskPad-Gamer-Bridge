package com.deskpad.gamerbridge.ui

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Início", Icons.Filled.Home),
    SETTINGS("Configurações", Icons.Filled.Settings),
    PERMISSIONS("Permissões", Icons.Filled.Lock),
    DIAGNOSTICS("Diagnóstico", Icons.Filled.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeRoot(vm: MainViewModel, onRequestNotifications: () -> Unit, onOpen: (Intent) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.consumeMessage()
        snackbar.showSnackbar(m)
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(if (tab == Tab.HOME) "DeskPad Gamer Bridge" else tab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding: PaddingValues ->
        val m = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(state, vm, onGoToPermissions = { tab = Tab.PERMISSIONS }, modifier = m)
            Tab.SETTINGS -> SettingsScreen(state, vm, modifier = m)
            Tab.PERMISSIONS -> PermissionsScreen(state, onRequestNotifications, onOpen, modifier = m)
            Tab.DIAGNOSTICS -> DiagnosticsScreen(state, vm, modifier = m)
        }
    }
}
