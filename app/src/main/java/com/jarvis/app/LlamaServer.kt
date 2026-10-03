package com.jarvis.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Socket
import java.util.concurrent.TimeUnit

class LlamaServer(private val context: Context) {
    private var process: Process? = null

    // ============================================================
    // Llama-server (modo normal)
    // ============================================================
    fun start(modelPath: String, ctxSize: Int, threads: Int, port: Int = 8081): Boolean {
        stop()
        return try {
            val bin = File(context.applicationInfo.nativeLibraryDir, "libllama_server.so")
            if (!bin.exists()) { Log.e(TAG, "Binario no encontrado"); return false }
            val cmd = listOf(
                bin.absolutePath, "-m", modelPath,
                "--port", "$port", "--host", "127.0.0.1",
                "--ctx-size", "$ctxSize", "--threads", "$threads"
            )
            Log.i(TAG, "Ejecutando: ${cmd.joinToString(" ")}")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            pb.directory(context.filesDir)
            pb.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
            process = pb.start()
            Thread {
                try { process!!.inputStream.bufferedReader().forEachLine { Log.d(TAG, it) } }
                catch (_: Exception) {}
            }.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error arrancando", e); false
        }
    }

    // ============================================================
    // Llama-server en modo clúster (con RPC)
    // ============================================================
    fun startWithRpc(
        modelPath: String,
        rpcEndpoints: List<String>,
        ctxSize: Int,
        threads: Int,
        port: Int = 8081
    ): Boolean {
        stop()
        return try {
            val bin = File(context.applicationInfo.nativeLibraryDir, "libllama_server.so")
            val cmd = mutableListOf(
                bin.absolutePath, "-m", modelPath,
                "--port", "$port", "--host", "127.0.0.1",
                "--ctx-size", "$ctxSize", "--threads", "$threads"
            )
            if (rpcEndpoints.isNotEmpty()) {
                cmd.add("--rpc")
                cmd.add(rpcEndpoints.joinToString(","))
            }
            Log.i(TAG, "Ejecutando clúster: ${cmd.joinToString(" ")}")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            pb.directory(context.filesDir)
            pb.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
            process = pb.start()
            Thread {
                try { process!!.inputStream.bufferedReader().forEachLine { Log.d(TAG, it) } }
                catch (_: Exception) {}
            }.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error arrancando clúster", e); false
        }
    }

    // ============================================================
    // RPC Worker (este dispositivo aporta CPU)
    // ============================================================
    fun startRpcWorker(port: Int = 50052): Boolean {
        stop()
        return try {
            val bin = File(context.applicationInfo.nativeLibraryDir, "librpc_server.so")
            if (!bin.exists()) {
                Log.e(TAG, "librpc_server.so no encontrado")
                return false
            }
            val cmd = listOf(
                bin.absolutePath,
                "-p", port.toString(),
                "-H", "0.0.0.0"
            )
            Log.i(TAG, "RPC worker: ${cmd.joinToString(" ")}")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            pb.directory(context.filesDir)
            pb.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
            process = pb.start()
            Thread {
                try { process!!.inputStream.bufferedReader().forEachLine { Log.d(TAG, "[RPC] $it") } }
                catch (_: Exception) {}
            }.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error arrancando RPC worker", e); false
        }
    }

    fun stop() {
        val p = process ?: return
        process = null
        p.destroy()
        try { if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly() }
        catch (_: Exception) { p.destroyForcibly() }
    }

    companion object {
        private const val TAG = "LlamaServer"

        fun waitForPort(port: Int, timeoutMs: Long): Boolean {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                try { Socket("127.0.0.1", port).close(); return true }
                catch (_: Exception) { Thread.sleep(300) }
            }
            return false
        }

        fun waitForHealth(port: Int, timeoutMs: Long): Boolean {
            val client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    val req = Request.Builder().url("http://127.0.0.1:$port/health").build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return true
                    }
                } catch (_: Exception) {}
                Thread.sleep(500)
            }
            return false
        }
    }
}

class LlamaClient(private val baseUrl: String = "http://127.0.0.1:8081") {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    fun streamCompletion(
        messages: List<Message>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        val msgArr = JSONArray()
        if (systemPrompt.isNotBlank()) {
            msgArr.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.forEach { m ->
            msgArr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject()
            .put("messages", msgArr)
            .put("temperature", temperature.toDouble())
            .put("max_tokens", maxTokens)
            .put("stream", true)
            .toString()
        val req = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("HTTP ${res.code}")
            val src = res.body?.source() ?: throw RuntimeException("Sin cuerpo")
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                try {
                    val obj = JSONObject(data)
                    val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val deltaObj = choice.optJSONObject("delta") ?: continue
                    if (deltaObj.isNull("content")) continue
                    val delta = deltaObj.optString("content", "")
                    if (delta.isEmpty() || delta == "null") continue
                    emit(delta)
                } catch (_: Exception) {}
            }
        }
    }.flowOn(Dispatchers.IO)
}
