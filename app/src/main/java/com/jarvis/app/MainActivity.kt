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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (!serverReady) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Primero carga un modelo",
                    color = p.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La configuración avanzada la sirve llama.cpp.\nNecesitas un modelo cargado para acceder.",
                    color = p.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onPickModel(); onClose() },
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent)
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

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(p.bgElev)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, null, tint = p.text)
        }
    }
}
