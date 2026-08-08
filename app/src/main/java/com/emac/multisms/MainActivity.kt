package com.emac.multisms

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emac.multisms.session.SessionManager
import com.emac.multisms.ui.MainViewModel
import com.emac.multisms.ui.components.AccountDialog
import com.emac.multisms.ui.components.BuyCreditsDialog
import com.emac.multisms.ui.screens.ConversationsScreen
import com.emac.multisms.ui.screens.ListsScreen
import com.emac.multisms.ui.screens.LoginScreen
import com.emac.multisms.ui.screens.MessageScreen
import com.emac.multisms.ui.screens.RegisterScreen
import com.emac.multisms.ui.screens.SendScreen
import com.emac.multisms.ui.screens.TextInputDialog
import com.emac.multisms.ui.theme.MultiSmsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        SessionManager.init(applicationContext)
        setContent {
            MultiSmsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Lists("Listes", Icons.AutoMirrored.Filled.List),
    Message("Messages", Icons.AutoMirrored.Filled.Message),
    Send("Envoi", Icons.Filled.Send),
    Conversations("Historique", Icons.Filled.Chat)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel = viewModel()) {
    val session by vm.session.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbar: (String) -> Unit = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } }

    if (!session.loggedIn) {
        var showRegister by remember { mutableStateOf(false) }
        Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { p ->
            Surface(Modifier.padding(p)) {
                if (showRegister) {
                    RegisterScreen(vm, snackbar, onGoLogin = { showRegister = false })
                } else {
                    LoginScreen(vm, snackbar, onGoRegister = { showRegister = true })
                }
            }
        }
        return
    }

    var tab by remember { mutableStateOf(Tab.Lists) }
    var showAccount by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBuy by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Permissions + rafraîchissement du solde à l'ouverture.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
        vm.refreshCredits()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("EmacSMS")
                        Text(
                            "Envoyés via le forfait SMS de ton téléphone",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { showAccount = true },
                        label = { Text("${session.credits} crédits") }
                    )
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Réglages") }
                    IconButton(onClick = { showAccount = true }) { Icon(Icons.Default.AccountCircle, "Compte") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(Modifier.padding(padding)) {
            when (tab) {
                Tab.Lists -> ListsScreen(vm, snackbar)
                Tab.Message -> MessageScreen(vm)
                Tab.Send -> SendScreen(vm, snackbar)
                Tab.Conversations -> ConversationsScreen(vm)
            }
        }
    }

    if (showAccount) {
        AccountDialog(
            account = session.account,
            credits = session.credits,
            onBuyCredits = { showAccount = false; showBuy = true },
            onLogout = { showAccount = false; vm.logout() },
            onDismiss = { showAccount = false }
        )
    }

    if (showBuy) {
        BuyCreditsDialog(
            onPick = { pack ->
                showBuy = false
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pack.url)))
            },
            onDismiss = { showBuy = false }
        )
    }

    if (showSettings) {
        TextInputDialog(
            title = "Adresse du serveur",
            label = "URL du backend",
            initial = SessionManager.serverUrl,
            confirm = "Enregistrer",
            onConfirm = { url -> SessionManager.serverUrl = url.trim(); showSettings = false; snackbar("Serveur enregistré") },
            onDismiss = { showSettings = false }
        )
    }
}
