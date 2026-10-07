package com.kryogames.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class CatalogGame(
    val id: String,
    val title: String,
    val type: String,
    val entry: String,
    val files: List<String>,
)

internal const val CatalogOrigin = "https://pub-e379ba287a9f4d8ba4cdbd6b6095cb6c.r2.dev"

internal suspend fun fetchCatalog(): List<CatalogGame> = withContext(Dispatchers.IO) {
    val raw = httpGet("$CatalogOrigin/catalog.json").bufferedReader().use { it.readText() }
    parseCatalog(raw)
}

internal fun parseCatalog(raw: String): List<CatalogGame> {
    val games = JSONObject(raw).getJSONArray("games")
    return buildList {
        for (index in 0 until games.length()) {
            val item = games.getJSONObject(index)
            val id = item.getString("id")
            if (!id.matches(Regex("[A-Za-z0-9._-]+"))) continue
            val listed = item.getJSONArray("files")
            val files = buildList {
                for (fileIndex in 0 until listed.length()) {
                    val path = normalizeRelative(listed.getString(fileIndex)) ?: continue
                    if (path.isNotEmpty()) add(path)
                }
            }
            val entry = normalizeRelative(item.optString("entry", "index.html"))?.ifEmpty { null } ?: "index.html"
            add(CatalogGame(id, item.getString("title"), item.optString("type", ""), entry, files))
        }
    }
}

internal fun downloadedGameDir(context: Context, id: String): File = File(context.filesDir, "games/$id")

internal fun CatalogGame.asLibraryGame(context: Context): Game {
    val dir = downloadedGameDir(context, id)
    val ready = File(dir, entry).isFile
    return Game(
        id = id,
        title = title,
        cover = 0,
        platform = GamePlatform.WEB,
        description = type,
        installed = ready,
        genre = type,
        contentTreeUri = if (ready) Uri.fromFile(dir).toString() else null,
        contentEntry = if (ready) entry else null,
    )
}

internal fun deleteDownloadedGame(context: Context, id: String): Boolean {
    if (!id.matches(Regex("[A-Za-z0-9._-]+"))) return false
    val dir = downloadedGameDir(context, id)
    if (!dir.isDirectory) return false
    dir.deleteRecursively()
    return true
}

private fun httpGet(url: String): java.io.InputStream {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
    }
    val code = connection.responseCode
    if (code !in 200..299) {
        connection.disconnect()
        error("Couldn't download $url ($code).")
    }
    return connection.inputStream
}
