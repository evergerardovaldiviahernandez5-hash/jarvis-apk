package com.jarvis.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val serverReady: Boolean = false,
    val serverStarting: Boolean = false,
    val serverError: String? = null,
    val currentModel: String? = null,
    val currentModelPath: String? = null,
    val chats: List<Chat> = emptyList(),
    val currentChatId: String? = null,
    val streaming: Boolean = false,
    val streamingText: String = "",
    val availableModels: List<File> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val modelsLoading: Boolean = false,
    val hasStoragePermission: Boolean = false
)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val server = LlamaServer(app.applicationContext)
    private val client = LlamaClient()
    private var streamJob: Job? = null

    init {
        val ctx = app.applicationContext
        _state.value = _state.value.copy(
            chats = ChatRepository.load(ctx),
            settings = SettingsRepository.load(ctx),
            hasStoragePermission = StoragePermission.hasPermission(ctx)
        )
        if (_state.value.hasStoragePermission) scanModels()
    }

    fun refreshPermission() {
        val ctx = getApplication<Application>()
        val has = StoragePermission.hasPermission(ctx)
        _state.value = _state.value.copy(hasStoragePermission = has)
        if (has && _state.value.availableModels.isEmpty()) scanModels()
    }

    fun scanModels() {
        if (!_state.value.hasStoragePermission) {
            _state.value = _state.value.copy(
                serverError = "Necesitas dar permiso de acceso a archivos",
                modelsLoading = false
            )
            return
        }
        _state.value = _state.value.copy(modelsLoading = true, serverError = null)
        viewModelScope.launch(Dispatchers.IO) {
            // /sdcard, /storage/self/primary y /storage/emulated/0 son el MISMO
            // sitio vía symlink. Escaneamos solo el real para no duplicar.
            val roots = listOf(
                File("/storage/emulated/0/Download"),
                File("/storage/emulated/0/Documents"),
                getApplication<Application>().filesDir
            ).filter { it.exists() }

            val found = mutableListOf<File>()
            val seen = mutableSetOf<String>()
            val skipDirs = setOf("Android", ".thumbnails", ".cache", ".trash")

            for (root in roots) {
                scanRecursive(root, found, seen, skipDirs, 6, 0)
            }

            val list = found.sortedByDescending { it.length() }

            _state.value = _state.value.copy(
                availableModels = list,
                modelsLoading = false,
                serverError = if (list.isEmpty())
                    "No se encontraron modelos .gguf. Ponlos en /sdcard/Download/"
                else null
            )
        }
    }

    private fun scanRecursive(
        dir: File,
        out: MutableList<File>,
        seen: MutableSet<String>,
        skip: Set<String>,
        maxDepth: Int,
        depth: Int
    ) {
        if (depth > maxDepth) return
        val files = try { dir.listFiles() ?: return } catch (_: Exception) { return }
        for (f in files) {
            if (f.isDirectory) {
                if (f.name in skip || f.name.startsWith(".")) continue
                if (!f.canRead()) continue
                scanRecursive(f, out, seen, skip, maxDepth, depth + 1)
            } else if (f.isFile && f.name.lowercase().endsWith(".gguf")) {
                // Dedup por canonicalPath (resuelve symlinks)
                val key = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
                if (seen.add(key)) out.add(f)
            }
        }
    }

    fun startServer(modelPath: String) {
        val file = File(modelPath)
        if (!file.exists()) {
            _state.value = _state.value.copy(serverError = "Archivo no encontrado: $modelPath")
            return
        }
        _state.value = _state.value.copy(
            serverStarting = true,
            serverError = null,
            currentModelPath = modelPath
        )
        viewModelScope.launch {
            val s = _state.value.settings
            val ok = withContext(Dispatchers.IO) {
                server.start(modelPath, s.contextSize, s.threads) &&
                    LlamaServer.waitForPort(8081, 180_000)
            }
            _state.value = _state.value.copy(
                serverReady = ok,
                serverStarting = false,
                serverError = if (ok) null else "No se pudo arrancar el servidor. Revisa el modelo.",
                currentModel = file.name
            )
        }
    }

    fun selectModel(file: File) {
        // Cambiar de modelo: parar el actual y arrancar el nuevo
        server.stop()
        _state.value = _state.value.copy(serverReady = false, currentModel = null)
        startServer(file.absolutePath)
    }

    fun stopServer() {
        server.stop()
        _state.value = _state.value.copy(serverReady = false, currentModel = null, currentModelPath = null)
    }

    fun newChat() { _state.value = _state.value.copy(currentChatId = null) }
    fun openChat(id: String) { _state.value = _state.value.copy(currentChatId = id) }

    fun deleteChat(id: String) {
        val updated = _state.value.chats.filter { it.id != id }
        ChatRepository.save(getApplication(), updated)
        _state.value = _state.value.copy(
            chats = updated,
            currentChatId = if (_state.value.currentChatId == id) null else _state.value.currentChatId
        )
    }

    fun sendMessage(text: String) {
        val s = _state.value
        if (s.streaming || !s.serverReady || text.isBlank()) return

        val ctx = getApplication<Application>()
        var chats = s.chats
        var chat = chats.find { it.id == s.currentChatId }
        if (chat == null) {
            chat = Chat(
                id = System.currentTimeMillis().toString(36) + (0..99).random(),
                title = text.take(48).replace("\n", " "),
                messages = emptyList(),
                createdAt = System.currentTimeMillis()
            )
            chats = listOf(chat) + chats
        }
        val chatId = chat.id
        val withUser = chat.copy(messages = chat.messages + Message("user", text))
        chats = chats.map { if (it.id == chatId) withUser else it }
        ChatRepository.save(ctx, chats)
        _state.value = _state.value.copy(
            chats = chats,
            currentChatId = chatId,
            streaming = true,
            streamingText = ""
        )

        streamJob = viewModelScope.launch {
            val acc = StringBuilder()
            try {
                client.streamCompletion(
                    withUser.messages,
                    s.settings.systemPrompt,
                    s.settings.temperature,
                    s.settings.maxTokens
                ).collect { delta ->
                    acc.append(delta)
                    _state.value = _state.value.copy(streamingText = acc.toString())
                }
                val withAssistant = withUser.copy(
                    messages = withUser.messages + Message("assistant", acc.toString())
                )
                val finalChats = _state.value.chats.map { if (it.id == chatId) withAssistant else it }
                ChatRepository.save(ctx, finalChats)
                _state.value = _state.value.copy(
                    chats = finalChats,
                    streaming = false,
                    streamingText = ""
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    streaming = false,
                    streamingText = "",
                    serverError = e.message ?: "Error desconocido"
                )
            }
        }
    }

    fun cancelStream() {
        streamJob?.cancel()
        _state.value = _state.value.copy(streaming = false, streamingText = "")
    }

    fun updateSettings(newSettings: AppSettings) {
        SettingsRepository.save(getApplication(), newSettings)
        _state.value = _state.value.copy(settings = newSettings)
    }

    override fun onCleared() {
        super.onCleared()
        server.stop()
    }
}
