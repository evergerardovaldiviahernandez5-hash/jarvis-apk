package com.jarvis.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun NovaApp(
    state: UiState,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onStartServer: (String) -> Unit,
    onStopServer: () -> Unit,
    onScanModels: () -> Unit,
    onSelectModel: (File) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = NovaTheme.palette
    var sidebarOpen by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(palette.bg)) {
        Row(Modifier.fillMaxSize()) {
            if (sidebarOpen || isWideScreen()) {
                Sidebar(
                    state = state,
                    onNewChat = { onNewChat(); sidebarOpen = false },
                    onOpenChat = { onOpenChat(it); sidebarOpen = false },
                    onDeleteChat = onDeleteChat,
                    onOpenSettings = { onOpenSettings(); sidebarOpen = false },
                    modifier = Modifier.width(272.dp).fillMaxHeight()
                )
            }
            MainContent(
                state = state,
                onMenuClick = { sidebarOpen = !sidebarOpen },
                onSend = onSend,
                onCancel = onCancel,
                onStartServer = onStartServer,
                onStopServer = onStopServer,
                onScanModels = onScanModels,
                onSelectModel = onSelectModel,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onOpenModelPicker = { showModelPicker = true },
                modifier = Modifier.weight(1f)
            )
        }

        if (showModelPicker) {
            ModelPickerModal(
                state = state,
                onSelect = { file -> onSelectModel(file); showModelPicker = false },
                onScan = { onScanModels() },
                onStop = { onStopServer(); showModelPicker = false },
                onDismiss = { showModelPicker = false }
            )
        }

        AnimatedVisibility(visible = sidebarOpen && !isWideScreen()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable { sidebarOpen = false }
            )
        }
    }
}

@Composable
private fun isWideScreen(): Boolean {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    return cfg.screenWidthDp >= 860
}

/* =====================================================
   SIDEBAR
   ===================================================== */
@Composable
fun Sidebar(
    state: UiState,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = NovaTheme.palette
    Column(modifier.background(p.sidebar).border(1.dp, p.borderSoft)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(p.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Nova", color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(11.dp)).background(p.bgElev)
                .border(1.dp, p.border, RoundedCornerShape(11.dp))
                .clickable { onNewChat() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = p.text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nueva conversación", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "RECIENTES", color = p.muted, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (state.chats.isEmpty()) {
                item {
                    Text(
                        "Aún no hay conversaciones.\nEmpieza escribiendo abajo.",
                        color = p.muted, fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp, 16.dp)
                    )
                }
            }
            items(state.chats, key = { it.id }) { chat ->
                ChatItem(
                    chat = chat,
                    active = chat.id == state.currentChatId,
                    onClick = { onOpenChat(chat.id) },
                    onDelete = { onDeleteChat(chat.id) }
                )
            }
        }

        Divider(color = p.borderSoft)

        Row(
            Modifier.fillMaxWidth().clickable { onOpenSettings() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Settings, null, tint = p.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Configuración", color = p.textSoft, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val ready = state.serverReady
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (ready) p.accent.copy(alpha = 0.12f) else p.border)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    if (ready) "Listo" else "Sin modelo",
                    color = if (ready) p.accent else p.muted,
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ChatItem(
    chat: Chat, active: Boolean,
    onClick: () -> Unit, onDelete: () -> Unit
) {
    val p = NovaTheme.palette
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (active) p.bgElev else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            chat.title.ifBlank { "Sin título" },
            color = if (active) p.text else p.textSoft,
            fontSize = 13.5.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, null, tint = p.muted, modifier = Modifier.size(14.dp))
        }
    }
}

/* =====================================================
   MAIN
   ===================================================== */
@Composable
fun MainContent(
    state: UiState,
    onMenuClick: () -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onStartServer: (String) -> Unit,
    onStopServer: () -> Unit,
    onScanModels: () -> Unit,
    onSelectModel: (File) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = NovaTheme.palette
    Column(modifier.fillMaxHeight().background(p.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isWideScreen()) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.Menu, null, tint = p.textSoft)
                }
            } else { Spacer(Modifier.width(4.dp)) }
            Text(
                state.chats.find { it.id == state.currentChatId }?.title ?: "Nueva conversación",
                color = p.textSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            ModelChip(state, onOpenModelPicker)
        }

        // Banners
        AnimatedVisibility(visible = !state.hasStoragePermission) {
            PermissionBanner(onRequestPermission)
        }
        AnimatedVisibility(visible = state.hasStoragePermission && !state.serverReady && !state.serverStarting) {
            ServerBanner(state, onStartServer, onScanModels, onOpenModelPicker)
        }
        AnimatedVisibility(visible = state.serverStarting) {
            StartingBanner(state)
        }
        AnimatedVisibility(visible = state.serverError != null && !state.serverStarting && state.hasStoragePermission) {
            ErrorBanner(state.serverError ?: "", onOpenSettings)
        }

        Box(Modifier.weight(1f)) {
            if (state.chats.find { it.id == state.currentChatId }?.messages.isNullOrEmpty() && !state.streaming) {
                WelcomeScreen(onPick = onSend, modifier = Modifier.fillMaxSize())
            } else {
                MessagesList(state)
            }
        }

        Composer(
            streaming = state.streaming,
            enabled = state.serverReady,
            onSend = onSend,
            onCancel = onCancel
        )
    }
}

@Composable
private fun ModelChip(state: UiState, onClick: () -> Unit) {
    val p = NovaTheme.palette
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(p.bgElev)
            .border(1.dp, p.border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (state.serverReady) p.success else p.muted)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            state.currentModel?.take(22) ?: "Elegir modelo",
            color = p.muted, fontSize = 12.5.sp
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ArrowDropDown, null, tint = p.muted, modifier = Modifier.size(16.dp))
    }
}

/* =====================================================
   PERMISSION BANNER
   ===================================================== */
@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    val p = NovaTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.accent.copy(alpha = 0.10f))
            .border(1.dp, p.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = p.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Permiso de archivos necesario",
                color = p.accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Nova necesita leer tus modelos .gguf. Toca el botón y activa \"Permitir acceso a todos los archivos\".",
            color = p.textSoft, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Dar permiso", fontSize = 13.sp)
        }
    }
}

/* =====================================================
   SERVER BANNER
   ===================================================== */
@Composable
private fun ServerBanner(
    state: UiState,
    onStart: (String) -> Unit,
    onScan: () -> Unit,
    onOpenPicker: () -> Unit
) {
    val p = NovaTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.bgElev)
            .border(1.dp, p.border, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = p.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.modelsLoading) "Buscando modelos…"
                else if (state.availableModels.isEmpty()) "No hay modelos .gguf"
                else "${state.availableModels.size} modelo(s) encontrado(s)",
                color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (state.modelsLoading) {
                CircularProgressIndicator(Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(containerColor = p.border),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, tint = p.text, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Buscar", color = p.text, fontSize = 12.sp)
            }
            if (state.availableModels.isNotEmpty()) {
                Button(
                    onClick = onOpenPicker,
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Elegir modelo", fontSize = 12.sp)
                }
            }
        }
        if (state.availableModels.isEmpty() && !state.modelsLoading) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Copia tus .gguf a /sdcard/Download/ o cualquier carpeta y pulsa Buscar.",
                color = p.muted, fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun StartingBanner(state: UiState) {
    val p = NovaTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(12.dp)
            .clip(RoundedCornerShape(14.dp)).background(p.bgElev)
            .border(1.dp, p.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = p.accent, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Cargando modelo…", color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(
                state.currentModel ?: "Iniciando servidor",
                color = p.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ErrorBanner(msg: String, onOpenSettings: () -> Unit) {
    val p = NovaTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.accent.copy(alpha = 0.08f))
            .border(1.dp, p.accent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, null, tint = p.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(msg, color = p.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenSettings) {
            Text("Ver", color = p.accent, fontSize = 12.sp)
        }
    }
}

/* =====================================================
   MODEL PICKER
   ===================================================== */
@Composable
private fun ModelPickerModal(
    state: UiState,
    onSelect: (File) -> Unit,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = NovaTheme.palette
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(p.bgElev)
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Modelos disponibles",
                    color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onScan) {
                    Icon(Icons.Filled.Refresh, null, tint = p.muted)
                }
                if (state.serverReady) {
                    TextButton(onClick = onStop) {
                        Text("Detener", color = p.accent, fontSize = 13.sp)
                    }
                }
            }
            Divider(color = p.borderSoft)
            Spacer(Modifier.height(8.dp))

            if (state.modelsLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accent)
                }
            } else if (state.availableModels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FolderOff, null, tint = p.muted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No se encontraron modelos .gguf", color = p.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Copia tus archivos a /sdcard/Download/",
                            color = p.muted, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onScan,
                            colors = ButtonDefaults.buttonColors(containerColor = p.accent)
                        ) { Text("Buscar de nuevo") }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.availableModels, key = { it.absolutePath }) { file ->
                        val isCurrent = state.currentModelPath == file.absolutePath
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) p.accent.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable { onSelect(file) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Description,
                                null,
                                tint = if (isCurrent) p.accent else p.muted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${"%.0f".format(file.length() / 1_000_000f)} MB · ${file.parentFile?.absolutePath?.replace("/storage/emulated/0/", "") ?: ""}",
                                    color = p.muted, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent) {
                                Icon(Icons.Filled.CheckCircle, null, tint = p.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Divider(color = p.borderSoft, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

/* =====================================================
   WELCOME / MESSAGES / COMPOSER (idénticos a antes)
   ===================================================== */
@Composable
private fun WelcomeScreen(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = NovaTheme.palette
    val suggestions = listOf(
        "Explícame qué es una API REST y dame un ejemplo práctico en JavaScript.",
        "Escribe una función en Python que lea un CSV y devuelva estadísticas descriptivas.",
        "Ayúdame a redactar un correo profesional para solicitar un aumento de sueldo.",
        "Dame 5 ideas creativas para un proyecto personal de fin de semana."
    )
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(p.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("¿En qué puedo ayudarte?", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Escribe lo que necesites: código, análisis, redacción o cualquier pregunta.",
            color = p.muted, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(32.dp))
        suggestions.forEach { s ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.bgElev)
                    .border(1.dp, p.border, RoundedCornerShape(14.dp))
                    .clickable { onPick(s) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s, color = p.textSoft, fontSize = 13.5.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ArrowForward, null, tint = p.muted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun MessagesList(state: UiState) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val chat = state.chats.find { it.id == state.currentChatId }

    LaunchedEffect(chat?.messages?.size, state.streamingText) {
        val total = (chat?.messages?.size ?: 0) + if (state.streaming) 1 else 0
        if (total > 0) scope.launch { listState.animateScrollToItem(total - 1) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        chat?.messages?.forEach { msg ->
            item(key = "${chat.id}-${msg.role}-${msg.content.hashCode()}") {
                if (msg.role == "user") UserMessage(msg.content)
                else AiMessage(msg.content, false)
                Spacer(Modifier.height(12.dp))
            }
        }
        if (state.streaming) {
            item(key = "streaming") {
                AiMessage(state.streamingText.ifEmpty { "…" }, true)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun UserMessage(text: String) {
    val p = NovaTheme.palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(p.bubble)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, color = p.text, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun AiMessage(text: String, streaming: Boolean) {
    val p = NovaTheme.palette
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(9.dp)).background(p.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MarkdownText(text)
            if (!streaming && text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row {
                    Row(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .clickable { clipboard.setText(AnnotatedString(text)) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = p.muted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copiar", color = p.muted, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(text: String) {
    val p = NovaTheme.palette
    val blocks = remember(text) { parseMarkdown(text) }
    Column {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Header -> {
                    Spacer(Modifier.height(if (block.level <= 2) 10.dp else 6.dp))
                    Text(
                        block.text, color = p.text,
                        fontSize = when (block.level) {
                            1 -> 22.sp; 2 -> 19.sp; 3 -> 17.sp; else -> 15.sp
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.Para -> {
                    Text(
                        inlineFormat(block.text, p.codeBg, p.accent),
                        color = p.textSoft, fontSize = 15.5.sp, lineHeight = 25.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
                is MdBlock.Bullets -> {
                    Column(Modifier.padding(start = 4.dp)) {
                        block.items.forEach { item ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("•", color = p.muted, fontSize = 15.sp,
                                    modifier = Modifier.padding(end = 10.dp))
                                Text(
                                    inlineFormat(item, p.codeBg, p.accent),
                                    color = p.textSoft, fontSize = 15.sp, lineHeight = 24.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                is MdBlock.Code -> {
                    CodeBlock(block.lang, block.code)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val p = NovaTheme.palette
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(p.codeBg)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().background(p.bubble)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                lang.ifBlank { "text" }.uppercase(),
                color = p.muted, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp, modifier = Modifier.weight(1f)
            )
            Row(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(4.dp, 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ContentCopy, null, tint = p.muted, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copiar", color = p.muted, fontSize = 11.sp)
            }
        }
        Text(
            code, color = p.textSoft, fontFamily = FontFamily.Monospace,
            fontSize = 13.sp, lineHeight = 20.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp)
        )
    }
}

@Composable
private fun Composer(
    streaming: Boolean,
    enabled: Boolean,
    onSend: (String) -> Unit,
    onCancel: () -> Unit
) {
    val p = NovaTheme.palette
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(p.bgElev)
                .border(1.dp, p.border, RoundedCornerShape(20.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = p.text, fontSize = 15.sp, lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.weight(1f).padding(10.dp, 6.dp).heightIn(min = 24.dp, max = 160.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text("Escribe un mensaje…", color = p.muted, fontSize = 15.sp)
                    }
                    inner()
                }
            )
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (streaming || enabled) p.accent else p.border)
                    .clickable(enabled = streaming || enabled) {
                        if (streaming) onCancel()
                        else {
                            val t = text.trim()
                            if (t.isNotEmpty()) { onSend(t); text = "" }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (streaming) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                    null, tint = Color.White, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
