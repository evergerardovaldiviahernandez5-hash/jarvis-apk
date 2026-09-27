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
    val chats: List<Chat> = emptyList(),
    val currentChatId: String? = null,
    val streaming: Boolean = false,
    val streamingText: String = "",
    val availableModels: List<File> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val modelsLoading: Boolean = false
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
            settings = SettingsRepository.load(ctx)
        )
        scanModels()
    }

    fun scanModels() {
        _state.value = _state.value.copy(modelsLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val dirs = listOf(
                File("/sdcard/Download"),
                File("/sdcard/Documents"),
                File("/sdcard/Android/data/com.jarvis.app/files"),
                getApplication<Application>().filesDir
            )
            val found = dirs.flatMap { d ->
                d.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }?.toList() ?: emptyList()
            }.distinctBy { it.absolutePath }
            _state.value = _state.value.copy(availableModels = found, modelsLoading = false)
        }
    }

    fun startServer(modelPath: String) {
        val file = File(modelPath)
        if (!file.exists()) {
            _state.value = _state.value.copy(serverError = "Archivo no encontrado")
            return
        }
        _state.value = _state.value.copy(serverStarting = true, serverError = null)
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

    fun stopServer() {
        server.stop()
        _state.value = _state.value.copy(serverReady = false, currentModel = null)
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
