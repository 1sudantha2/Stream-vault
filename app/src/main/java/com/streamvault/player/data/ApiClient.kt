package com.streamvault.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class ApiException(message: String) : Exception(message)

/** Small dependency-free client for the API used by web/templates/gallery.html and watch.html. */
object ApiClient {
    suspend fun videos(server: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val root = JSONArray(get(normalize(server) + "/api/videos"))
        buildList(root.length()) { for (i in 0 until root.length()) add(parseVideo(root.getJSONObject(i), server)) }
    }

    suspend fun video(server: String, id: String): VideoItem = withContext(Dispatchers.IO) {
        parseVideo(JSONObject(get("${normalize(server)}/api/videos/${encodePath(id)}")), server)
    }

    fun streamUrl(server: String, path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return normalize(server) + "/" + path.trimStart('/')
    }

    private fun parseVideo(json: JSONObject, server: String): VideoItem {
        val path = json.optString("fmp4_path").ifBlank {
            json.optString("stream_path").ifBlank { json.optString("url") }
        }.ifBlank { null }
        val thumbnail = json.optString("thumbnail").ifBlank { null }?.let { streamUrl(server, it) }
        return VideoItem(
            id = json.optString("id"),
            title = json.optString("title", "Untitled video"),
            durationSeconds = if (json.has("duration") && !json.isNull("duration")) json.optDouble("duration") else null,
            sizeBytes = if (json.has("size_bytes") && !json.isNull("size_bytes")) json.optLong("size_bytes") else null,
            createdAt = json.optString("created_at").ifBlank { null },
            thumbnail = thumbnail,
            streamPath = path
        )
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            useCaches = true
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw ApiException("Server returned HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(e.message ?: "Could not reach the server")
        } finally {
            connection.disconnect()
        }
    }

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    fun normalize(value: String): String {
        val trimmed = value.trim().removeSuffix("/")
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    }
}
