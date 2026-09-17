package com.streamvault.player.data

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Mirrors admin.html: POST jobs/cancel and receive job_update/job_done over /ws. */
class AdminClient(private val server: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null

    fun connect(onUpdate: (DownloadJob, Boolean) -> Unit, onError: (String) -> Unit) {
        val normalized = ApiClient.normalize(server)
        if (normalized.isBlank()) return
        val wsUrl = normalized.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/ws"
        socket = http.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        val msg = JSONObject(text)
                        val type = msg.optString("type")
                        val data = msg.optJSONObject("data") ?: return@runCatching
                        val json = if (type == "job_done") data.optJSONObject("job") ?: data else data
                        val job = parseJob(json)
                        if (type == "job_update" || type == "job_done") {
                            main.post { onUpdate(job, type == "job_done") }
                        }
                    }.onFailure { main.post { onError(it.message ?: "Invalid server update") } }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    main.post { onError(t.message ?: "Admin connection lost") }
                }
            }
        )
    }

    fun addJob(url: String, title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val body = JSONObject().apply { put("url", url); put("title", title) }
            .toString().toRequestBody(JSON)
        request("${ApiClient.normalize(server)}/api/jobs", "POST", body, onSuccess, onError)
    }

    fun cancelJob(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        request("${ApiClient.normalize(server)}/api/jobs/$id/cancel", "POST", null, onSuccess, onError)
    }

    fun close() {
        socket?.close(1000, "screen closed")
        socket = null
        http.dispatcher.cancelAll()
    }

    private fun request(url: String, method: String, body: okhttp3.RequestBody?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (url.startsWith("/")) { onError("Set a backend server URL first"); return }
        val request = Request.Builder().url(url).method(method, body).build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = main.post { onError(e.message ?: "Request failed") }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) main.post { onSuccess() }
                    else main.post { onError("Server returned HTTP ${it.code}") }
                }
            }
        })
    }

    private fun parseJob(json: JSONObject): DownloadJob = DownloadJob(
        id = json.optString("id"),
        title = json.optString("title", json.optString("id", "Download")),
        url = json.optString("url"),
        status = json.optString("status", "pending"),
        downloadPercent = json.optDouble("download_pct", 0.0).toFloat(),
        transcodePercent = json.optDouble("transcode_pct", 0.0).toFloat(),
        downloadedBytes = json.optLong("downloaded_bytes", 0L),
        totalBytes = json.optLong("total_bytes", 0L),
        downloadSpeed = json.optLong("download_speed", 0L),
        etaSeconds = json.optLong("download_eta", 0L),
        error = json.optString("error").ifBlank { null },
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
