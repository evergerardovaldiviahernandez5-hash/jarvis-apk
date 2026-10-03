package com.jarvis.app

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {

    private val vm: JarvisViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Tras pedir permisos clásicos, si estamos en Android 11+, abrimos ajustes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!StoragePermission.hasPermission(this)) {
                StoragePermission.openSettings(this)
            }
        }
        vm.refreshPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pedir permiso la primera vez
        if (!StoragePermission.hasPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StoragePermission.openSettings(this)
            } else {
                requestPermission.launch(StoragePermission.requiredPermissions())
            }
        }

        setContent {
            val state by vm.state.collectAsState()
            var showSettings by remember { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current

            // Detectar cuando el usuario vuelve de Ajustes
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        vm.refreshPermission()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            NovaTheme {
                Box(Modifier.fillMaxSize()) {
                    NovaApp(
                        state = state,
                        onNewChat = { vm.newChat() },
                        onOpenChat = { vm.openChat(it) },
                        onDeleteChat = { vm.deleteChat(it) },
                        onSend = { vm.sendMessage(it) },
                        onCancel = { vm.cancelStream() },
                        onStartServer = { vm.startServer(it) },
                        onStopServer = { vm.stopServer() },
                        onScanModels = { vm.scanModels() },
                        onSelectModel = { vm.selectModel(it) },
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                StoragePermission.openSettings(this@MainActivity)
                            } else {
                                requestPermission.launch(StoragePermission.requiredPermissions())
                            }
                        },
                        onOpenSettings = { showSettings = true },
                        onOpenCluster = { vm.discoverWorkers() },
                        onDiscoverWorkers = { vm.discoverWorkers() },
                        onStartCluster = { endpoints -> vm.startCluster(endpoints) },
                        onStartAsWorker = { vm.startAsWorker() },
                        onStopWorker = { vm.stopWorker() }
                    )

                    if (showSettings) {
                        SettingsWebView(
                            serverReady = state.serverReady,
                            onClose = { showSettings = false },
                            onPickModel = { vm.scanModels(); showSettings = false }
                        )
                    }
                }
                BackHandler(enabled = showSettings) { showSettings = false }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SettingsWebView(
    serverReady: Boolean,
    onClose: () -> Unit,
    onPickModel: () -> Unit
) {
    val p = NovaTheme.palette

    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (!serverReady) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Primero carga un modelo",
                    color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La configuración avanzada la sirve llama.cpp.\nNecesitas un modelo cargado para acceder.",
                    color = p.muted, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onPickModel,
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent)
                ) { Text("Buscar modelos") }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        webViewClient = WebViewClient()
                        loadUrl("http://127.0.0.1:8081")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp)
                .clip(CircleShape).background(p.bgElev)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, null, tint = p.text)
        }
    }
}
