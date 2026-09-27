package com.jarvis.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {

    private val vm: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsState()
            var showSettings by remember { mutableStateOf(false) }

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
                        onOpenSettings = { showSettings = true }
                    )

                    if (showSettings) {
                        SettingsWebView(
                            serverReady = state.serverReady,
                            onClose = { showSettings = false },
                            onPickModel = { vm.scanModels() }
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
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (!serverReady) {
            // Si no hay servidor, no podemos cargar la WebUI de llama.cpp
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Primero carga un modelo",
                    color = p.text, fontSize = 18.spFallback(), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La configuración avanzada la sirve llama.cpp.\nNecesitas un modelo cargado para acceder.",
                    color = p.muted,
                    fontSize = 14.spFallback(),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Button(
                    onClick = { onPickModel(); onClose() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.accent)
                ) {
                    Text("Buscar modelos")
                }
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

        // Botón flotante de cerrar
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp)
                .size(44.dp).clip(CircleShape).background(p.bgElev)
                .clickableNoRipple(onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, null, tint = p.text)
        }
    }
}

// Helpers para no importar más cosas
private fun Int.spFallback() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp
)

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.foundation.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick
        )
    )
