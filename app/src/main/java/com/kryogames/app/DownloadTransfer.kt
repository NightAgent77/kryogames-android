package com.kryogames.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

internal class TransferTicket {
    @Volatile var stopped: Boolean = false
}

internal class TransferStopped : Exception("Stopped")

internal data class TransferSnapshot(
    val received: Long,
    val total: Long,
    val bytesPerSecond: Long,
)

data class LiveTransfer(
    val id: String,
    val title: String,
    val received: Long,
    val total: Long,
    val bytesPerSecond: Long,
    val settling: Boolean = false,
) {
    val fraction: Float
        get() = when {
            settling -> 1f
            total <= 0L -> -1f
            else -> (received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

data class FinishedDownload(
    val id: String,
    val title: String,
    val bytes: Long,
    val finishedAt: Long,
)

internal fun bytesPerSecond(earlierNanos: Long, earlierBytes: Long, nowNanos: Long, nowBytes: Long): Long {
    val elapsed = nowNanos - earlierNanos
    if (elapsed <= 0L) return 0L
    val delta = (nowBytes - earlierBytes).coerceAtLeast(0L)
    return delta * 1_000_000_000L / elapsed
}

internal fun formatTransferSize(bytes: Long): String {
    if (bytes < 0L) return "0 B"
    val unit = 1024.0
    return when {
        bytes < unit -> "$bytes B"
        bytes < unit * unit -> String.format(Locale.US, "%.1f KB", bytes / unit)
        else -> String.format(Locale.US, "%.1f MB", bytes / (unit * unit))
    }
}

internal fun formatTransferAmount(received: Long, total: Long): String =
    if (total > 0L) "${formatTransferSize(received)} of ${formatTransferSize(total)}" else formatTransferSize(received)

internal fun formatTransferSpeed(bytesPerSecond: Long): String =
    if (bytesPerSecond <= 0L) "0 B/s" else "${formatTransferSize(bytesPerSecond)}/s"

internal fun formatDownloadedAt(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", locale)
    return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

internal fun folderSize(dir: File): Long =
    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

internal suspend fun downloadCatalogGame(
    context: Context,
    game: CatalogGame,
    ticket: TransferTicket,
    onProgress: (TransferSnapshot) -> Unit,
) {
    withContext(Dispatchers.IO) {
        if (ticket.stopped) throw TransferStopped()
        val staging = File(context.filesDir, "games/${game.id}.partial")
        staging.deleteRecursively()
        staging.mkdirs()
        val measured = measureCatalogBytes(game, ticket)
        var received = 0L
        val window = SpeedWindow()
        var lastUi = 0L
        suspend fun emit(total: Long, force: Boolean) {
            val now = System.nanoTime()
            if (!force && now - lastUi < 50_000_000L) return
            lastUi = now
            val snapshot = TransferSnapshot(received, total, window.push(now, received))
            withContext(Dispatchers.Main) { onProgress(snapshot) }
        }
        try {
            emit(measured, force = true)
            game.files.forEach { relative ->
                if (ticket.stopped) throw TransferStopped()
                val dest = File(staging, relative)
                dest.parentFile?.mkdirs()
                val connection = openGet("$CatalogOrigin/${game.id}/${encodePath(relative)}")
                try {
                    connection.inputStream.use { input ->
                        dest.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                if (ticket.stopped) throw TransferStopped()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                emit(measured, force = false)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                emit(measured, force = true)
            }
            if (!File(staging, game.entry).isFile) error("The download did not include ${game.entry}.")
            val ready = downloadedGameDir(context, game.id)
            ready.deleteRecursively()
            if (!staging.renameTo(ready)) {
                staging.copyRecursively(ready, overwrite = true)
                staging.deleteRecursively()
            }
            received = folderSize(ready)
            emit(if (measured > 0L) measured else received, force = true)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }
}

private class SpeedWindow {
    private val points = ArrayDeque<Pair<Long, Long>>()

    fun push(nowNanos: Long, received: Long): Long {
        points.addLast(nowNanos to received)
        while (points.size > 2 && nowNanos - points.first().first > 1_000_000_000L) points.removeFirst()
        val first = points.first()
        return bytesPerSecond(first.first, first.second, nowNanos, received)
    }
}

private fun measureCatalogBytes(game: CatalogGame, ticket: TransferTicket): Long {
    var sum = 0L
    for (relative in game.files) {
        if (ticket.stopped) throw TransferStopped()
        val length = headLength("$CatalogOrigin/${game.id}/${encodePath(relative)}")
        if (length < 0L) return -1L
        sum += length
    }
    return sum
}

private fun encodePath(relative: String): String =
    relative.split('/').joinToString("/") { Uri.encode(it) }

private fun headLength(url: String): Long {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "HEAD"
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 15_000
    }
    return try {
        val code = connection.responseCode
        if (code !in 200..299) -1L else connection.contentLengthLong
    } catch (_: Exception) {
        -1L
    } finally {
        connection.disconnect()
    }
}

private fun openGet(url: String): HttpURLConnection {
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
    return connection
}

private const val FinishedDownloadsKey = "finished_downloads"

internal fun loadFinishedDownloads(context: Context): List<FinishedDownload> {
    val raw = context.getSharedPreferences("kryo_settings", Context.MODE_PRIVATE).getString(FinishedDownloadsKey, null)
        ?: return emptyList()
    return decodeFinishedDownloads(raw).filter { downloadedGameDir(context, it.id).isDirectory }
}

internal fun rememberFinishedDownload(context: Context, item: FinishedDownload): List<FinishedDownload> {
    val next = listOf(item) + loadFinishedDownloads(context).filter { it.id != item.id }
    saveFinishedDownloads(context, next)
    return next
}

internal fun forgetFinishedDownload(context: Context, id: String): List<FinishedDownload> {
    val next = loadFinishedDownloads(context).filter { it.id != id }
    saveFinishedDownloads(context, next)
    return next
}

internal fun encodeFinishedDownloads(items: List<FinishedDownload>): String {
    val array = JSONArray()
    items.forEach { item ->
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("bytes", item.bytes)
                .put("finishedAt", item.finishedAt),
        )
    }
    return array.toString()
}

internal fun decodeFinishedDownloads(raw: String): List<FinishedDownload> {
    val array = JSONArray(raw)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.optString("id")
            if (!id.matches(Regex("[A-Za-z0-9._-]+"))) continue
            add(
                FinishedDownload(
                    id = id,
                    title = item.optString("title", id),
                    bytes = item.optLong("bytes"),
                    finishedAt = item.optLong("finishedAt"),
                ),
            )
        }
    }
}

private fun saveFinishedDownloads(context: Context, items: List<FinishedDownload>) {
    context.getSharedPreferences("kryo_settings", Context.MODE_PRIVATE)
        .edit()
        .putString(FinishedDownloadsKey, encodeFinishedDownloads(items))
        .apply()
}
