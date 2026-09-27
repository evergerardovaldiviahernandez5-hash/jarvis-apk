package com.jarvis.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var overlay: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var pickButton: Button
    private lateinit var llmServer: LlamaServer
    private val scope = CoroutineScope(Dispatchers.Main)

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) copyAndStart(uri)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        overlay = findViewById(R.id.overlay)
        loading = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        pickButton = findViewById(R.id.pickModelBtn)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
        }
        webView.webViewClient = WebViewClient()

        llmServer = LlamaServer(this)

        pickButton.setOnClickListener {
            pickModel.launch(arrayOf("*/*"))
        }

        // Auto-arranque si ya hay un modelo guardado
        val saved = ModelManager.getSavedModel(this)
        if (saved != null) {
            startServer(saved.absolutePath)
        }
    }

    private fun copyAndStart(uri: Uri) {
        scope.launch {
            statusText.text = "Copiando modelo..."
            loading.visibility = View.VISIBLE
            pickButton.visibility = View.GONE

            val dest = withContext(Dispatchers.IO) {
                ModelManager.copyToInternal(this@MainActivity, uri)
            }

            if (dest == null) {
                loading.visibility = View.GONE
                pickButton.visibility = View.VISIBLE
                statusText.text = "Error copiando el modelo"
                return@launch
            }
            startServer(dest.absolutePath)
        }
    }

    private fun startServer(modelPath: String) {
        scope.launch {
            statusText.text = "Iniciando Jarvis..."
            loading.visibility = View.VISIBLE
            pickButton.visibility = View.GONE

            val ok = withContext(Dispatchers.IO) {
                llmServer.start(modelPath) &&
                    LlamaServer.waitForPort(8081, 180_000)
            }

            if (ok) {
                statusText.text = "Listo"
                overlay.visibility = View.GONE
                webView.visibility = View.VISIBLE
                webView.loadUrl("http://127.0.0.1:8081")
            } else {
                loading.visibility = View.GONE
                pickButton.visibility = View.VISIBLE
                statusText.text = "Error al iniciar. Prueba otro modelo .gguf"
            }
        }
    }

    override fun onDestroy() {
        llmServer.stop()
        super.onDestroy()
    }
}
