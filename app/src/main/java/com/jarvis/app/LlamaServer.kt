package com.jarvis.app

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import java.util.concurrent.TimeUnit

class LlamaServer(private val context: Context) {

    private var process: Process? = null

    fun start(modelPath: String, port: Int = 8081): Boolean {
        stop()
        return try {
            val binary = File(context.applicationInfo.nativeLibraryDir, "libllama_server.so")
            if (!binary.exists()) {
                Log.e(TAG, "Binario no encontrado en ${binary.absolutePath}")
                return false
            }

            val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(8)

            val cmd = listOf(
                binary.absolutePath,
                "-m", modelPath,
                "--port", port.toString(),
                "--host", "127.0.0.1",
                "--ctx-size", "4096",
                "--threads", threads.toString()
            )
            Log.i(TAG, "Ejecutando: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            pb.directory(context.filesDir)
            pb.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir

            process = pb.start()

            Thread {
                try {
                    BufferedReader(InputStreamReader(process!!.inputStream)).use { r ->
                        r.forEachLine { Log.d(TAG, it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error leyendo log", e)
                }
            }.start()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error arrancando servidor", e)
            false
        }
    }

    fun stop() {
        val p = process
        process = null
        if (p == null) return
        p.destroy()
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly()
            }
        } catch (e: Exception) {
            p.destroyForcibly()
        }
    }

    companion object {
        private const val TAG = "LlamaServer"

        fun waitForPort(port: Int, timeoutMs: Long): Boolean {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    Socket("127.0.0.1", port).use { }
                    return true
                } catch (e: Exception) {
                    Thread.sleep(500)
                }
            }
            return false
        }
    }
}
