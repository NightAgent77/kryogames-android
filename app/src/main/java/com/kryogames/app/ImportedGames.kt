package com.kryogames.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

internal data class FolderEntry(val relativePath: String, val directory: Boolean = false)

internal data class ChosenGame(val entryPath: String, val scriptPath: String)

internal data class StoredImport(
    val id: String,
    val treeUri: String,
    val title: String,
    val entryPath: String,
)

internal sealed interface ImportResult {
    data class Ready(val game: StoredImport) : ImportResult
    data class Failed(val message: String) : ImportResult
}

private val engineScripts = setOf(
    "q5.js",
    "q5.min.js",
    "q5play.js",
    "q5play.min.js",
    "peerjs.js",
    "peerjs.min.js",
    "box2d.deluxe.js",
    "box2d.js",
    "jquery.js",
    "jquery.min.js",
)

private val scriptSrc = Regex(
    """<script\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""",
    RegexOption.IGNORE_CASE,
)

internal fun isEngineScript(fileName: String): Boolean = fileName.lowercase(Locale.ROOT) in engineScripts

/** Collapses `.` and `..`. Returns null when the path would leave the chosen folder. */
internal fun normalizeRelative(path: String): String? {
    val parts = ArrayDeque<String>()
    for (segment in path.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> parts.add(segment)
        }
    }
    return parts.joinToString("/")
}

internal fun normalizeRequestPath(raw: String): String? {
    val decoded = percentDecode(raw.substringBefore('?').substringBefore('#')).trim().trimStart('/')
    val relative = if (decoded.isEmpty()) "index.html" else decoded
    return normalizeRelative(relative)?.ifEmpty { "index.html" }
}

internal fun percentDecode(raw: String): String {
    val bytes = ArrayList<Byte>()
    val out = StringBuilder()
    fun flush() {
        if (bytes.isNotEmpty()) {
            out.append(bytes.toByteArray().toString(Charsets.UTF_8))
            bytes.clear()
        }
    }
    var index = 0
    while (index < raw.length) {
        if (raw[index] == '%' && index + 2 < raw.length) {
            val value = raw.substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                bytes.add(value.toByte())
                index += 3
                continue
            }
        }
        flush()
        out.append(raw[index])
        index++
    }
    flush()
    return out.toString()
}

/**
 * Picks the HTML entry and the game script inside a folder the user selected.
 * Engine files such as q5.js stay in the folder so the page can load them, but the title comes from the game script.
 */
internal fun chooseGame(entries: List<FolderEntry>, readText: (String) -> String?): ChosenGame? {
    val files = entries.filter { !it.directory && it.relativePath.isNotBlank() }
    val byDirectory = files.groupBy { parentPath(it.relativePath) }
    return byDirectory.entries
        .sortedBy { it.key.count { char -> char == '/' } }
        .firstNotNullOfOrNull { (directory, children) ->
            val html = children
                .filter { it.relativePath.endsWith(".html", true) || it.relativePath.endsWith(".htm", true) }
                .minByOrNull { entry ->
                    val name = entry.relativePath.substringAfterLast('/').lowercase(Locale.ROOT)
                    when (name) {
                        "index.html" -> 0
                        "index.htm" -> 1
                        else -> 2
                    }
                } ?: return@firstNotNullOfOrNull null
            val scripts = children.filter {
                val name = it.relativePath.substringAfterLast('.').lowercase(Locale.ROOT)
                name == "js" || name == "mjs"
            }
            if (scripts.isEmpty()) return@firstNotNullOfOrNull null
            val htmlText = readText(html.relativePath).orEmpty()
            val available = scripts.map { it.relativePath }.toSet()
            val referenced = scriptSrc.findAll(htmlText).map { it.groupValues[1] }
                .mapNotNull { resolveScript(directory, it) }
                .filter { it in available }
                .filter { !isEngineScript(it.substringAfterLast('/')) }
                .lastOrNull()
            val nonLibrary = scripts.map { it.relativePath }.filter { !isEngineScript(it.substringAfterLast('/')) }
            val script = referenced
                ?: nonLibrary.singleOrNull()
                ?: scripts.map { it.relativePath }.singleOrNull()
                ?: return@firstNotNullOfOrNull null
            ChosenGame(html.relativePath, script)
        }
}

internal fun titleFromGameScript(source: String, scriptFileName: String): String {
    source.lineSequence().take(40).forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        val comment = when {
            line.startsWith("//") -> line.removePrefix("//").trim()
            line.startsWith("/*") -> line.removePrefix("/*").trim().removeSuffix("*/").trim()
            line.startsWith("*") -> line.removePrefix("*").trim()
            else -> return fallbackTitle(scriptFileName)
        }
        if (comment.isEmpty() || comment.startsWith("eslint") || comment.startsWith("@")) return@forEach
        val name = comment.split(Regex("""\s+[—–]\s+|\s+-\s+"""), limit = 2).first().trim()
        if (name.isNotEmpty() && name.length <= 60) return name
    }
    return fallbackTitle(scriptFileName)
}

internal fun upsertImport(current: List<StoredImport>, next: StoredImport): List<StoredImport> =
    current.filterNot { it.treeUri == next.treeUri } + next

internal fun StoredImport.asGame(): Game = Game(
    id = id,
    title = title,
    cover = 0,
    platform = GamePlatform.WEB,
    description = "Added from a folder on this device.",
    installed = true,
    contentTreeUri = treeUri,
    contentEntry = entryPath,
    genre = "Local",
    tags = listOf("Web", "Local"),
)

internal fun importTree(context: Context, treeUri: Uri): ImportResult {
    val documents = TreeDocuments(context, treeUri)
    val entries = documents.listFiles()
    if (entries.isEmpty()) {
        return ImportResult.Failed("Couldn't read that folder. Pick the folder that holds the game.")
    }
    val chosen = chooseGame(entries) { path -> documents.readText(path, 200_000) }
        ?: return ImportResult.Failed("That folder needs an HTML file and a JavaScript file together.")
    val script = documents.readText(chosen.scriptPath, 16_000).orEmpty()
    val title = titleFromGameScript(script, chosen.scriptPath.substringAfterLast('/'))
    val id = "local-" + treeUri.toString().hashCode().toUInt().toString(16)
    return ImportResult.Ready(StoredImport(id, treeUri.toString(), title, chosen.entryPath))
}

internal fun loadStoredImports(context: Context): List<StoredImport> {
    val raw = context.getSharedPreferences(ImportPrefs, Context.MODE_PRIVATE).getString(ImportsKey, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    StoredImport(
                        id = item.getString("id"),
                        treeUri = item.getString("uri"),
                        title = item.getString("title"),
                        entryPath = item.getString("entry"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun saveStoredImports(context: Context, games: List<StoredImport>) {
    val array = JSONArray()
    games.forEach { game ->
        array.put(
            JSONObject()
                .put("id", game.id)
                .put("uri", game.treeUri)
                .put("title", game.title)
                .put("entry", game.entryPath),
        )
    }
    context.getSharedPreferences(ImportPrefs, Context.MODE_PRIVATE).edit().putString(ImportsKey, array.toString()).apply()
}

internal fun canReadTree(context: Context, treeUri: String): Boolean {
    val uri = Uri.parse(treeUri)
    if (uri.scheme == "file") {
        val path = uri.path ?: return false
        val dir = File(path)
        return dir.isDirectory && dir.list() != null
    }
    return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
}

internal fun canBrowseDeviceFolders(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

/** Volumes the folder browser can open. Unreadable roots are left out. */
internal fun storageRoots(): List<File> {
    val found = LinkedHashMap<String, File>()
    fun add(file: File?) {
        val dir = file ?: return
        if (dir.list() == null) return
        val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        found.putIfAbsent(key, dir)
    }
    add(Environment.getExternalStorageDirectory())
    File("/storage").listFiles()?.forEach { volume ->
        if (volume.name == "self" || volume.name == "emulated") return@forEach
        add(volume)
    }
    return found.values.sortedBy { it.name.lowercase(Locale.ROOT) }
}

internal fun childDirectories(dir: File): List<File> =
    dir.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory && !it.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase(Locale.ROOT) }
        ?.toList()
        ?: emptyList()

/** Parent folder inside a storage volume, or null when the browser should show the volume list. */
internal fun folderAbove(dir: File): File? {
    val parent = dir.parentFile ?: return null
    val path = parent.absolutePath.trimEnd('/')
    if (path.isEmpty() || path == "/" || path == "/storage" || path == "/storage/emulated") return null
    return parent
}

internal fun folderLabel(dir: File): String {
    val path = dir.absolutePath.trimEnd('/')
    if (path.endsWith("/emulated/0") || path == "/sdcard") return "Internal storage"
    return dir.name.ifBlank { path }
}

internal fun importDirectory(root: File): ImportResult {
    val rootCanon = runCatching { root.canonicalFile }.getOrNull()
        ?: return ImportResult.Failed("Couldn't read that folder. Pick the folder that holds the game.")
    if (rootCanon.list() == null) {
        return ImportResult.Failed("Couldn't read that folder. Pick the folder that holds the game.")
    }
    val entries = ArrayList<FolderEntry>()
    val queue = ArrayDeque<File>()
    queue.add(rootCanon)
    var seen = 0
    while (queue.isNotEmpty() && seen < 500) {
        val dir = queue.removeFirst()
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (child.name.startsWith(".") || child.name == "node_modules") continue
            val canonical = runCatching { child.canonicalFile }.getOrNull() ?: continue
            val relative = runCatching {
                rootCanon.toPath().relativize(canonical.toPath()).toString().replace('\\', '/')
            }.getOrNull() ?: continue
            if (relative.isEmpty() || relative == "." || relative.startsWith("..")) continue
            seen++
            if (canonical.isDirectory) {
                if (relative.count { it == '/' } < 4) queue.add(canonical)
            } else {
                entries.add(FolderEntry(relative))
            }
        }
    }
    if (entries.isEmpty()) {
        return ImportResult.Failed("Couldn't read that folder. Pick the folder that holds the game.")
    }
    val chosen = chooseGame(entries) { path ->
        val file = File(rootCanon, path)
        if (!file.isFile) null else runCatching { file.bufferedReader().use { it.readText().take(200_000) } }.getOrNull()
    } ?: return ImportResult.Failed("That folder needs an HTML file and a JavaScript file together.")
    val scriptFile = File(rootCanon, chosen.scriptPath)
    val script = runCatching { scriptFile.bufferedReader().use { it.readText().take(16_000) } }.getOrNull().orEmpty()
    val title = titleFromGameScript(script, chosen.scriptPath.substringAfterLast('/'))
    val uri = rootCanon.toURI().toString()
    val id = "local-" + uri.hashCode().toUInt().toString(16)
    return ImportResult.Ready(StoredImport(id, uri, title, chosen.entryPath))
}

internal class DirectoryPathHandler(root: File) : WebViewAssetLoader.PathHandler {
    private val rootPath: String = runCatching { root.canonicalFile.path }.getOrDefault(root.absolutePath)

    override fun handle(path: String): WebResourceResponse? {
        val relative = normalizeRequestPath(path) ?: return null
        val file = File(rootPath, relative)
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val childPath = canonical.path
        if (childPath != rootPath && !childPath.startsWith(rootPath + File.separator)) return null
        if (!canonical.isFile) return null
        val stream = runCatching { canonical.inputStream() }.getOrNull() ?: return null
        val mime = mimeFor(relative)
        val encoding = if (mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json") {
            "utf-8"
        } else {
            null
        }
        return WebResourceResponse(mime, encoding, stream)
    }
}

internal class TreePathHandler(context: Context, treeUri: Uri) : WebViewAssetLoader.PathHandler {
    private val documents = TreeDocuments(context, treeUri)

    override fun handle(path: String): WebResourceResponse? {
        val relative = normalizeRequestPath(path) ?: return null
        val uri = documents.uriFor(relative) ?: return null
        val stream = documents.open(uri) ?: return null
        val mime = mimeFor(relative)
        val encoding = if (mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json") {
            "utf-8"
        } else {
            null
        }
        return WebResourceResponse(mime, encoding, stream)
    }
}

private const val ImportPrefs = "kryo_library"
private const val ImportsKey = "imported_games"

private fun parentPath(relativePath: String): String = relativePath.substringBeforeLast('/', "")

private fun resolveScript(directory: String, src: String): String? {
    val clean = src.substringBefore('?').substringBefore('#').trim()
    if (clean.isEmpty() || clean.startsWith("//") || clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("data:")) {
        return null
    }
    val combined = if (clean.startsWith("/")) clean.trimStart('/') else if (directory.isEmpty()) clean else "$directory/$clean"
    return normalizeRelative(combined)
}

private fun fallbackTitle(scriptFileName: String): String {
    val base = scriptFileName.substringAfterLast('/').substringBeforeLast('.')
    return base.split(Regex("[-_]+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
        }
        .ifBlank { "Local game" }
}

private fun mimeFor(path: String): String = when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
    "html", "htm" -> "text/html"
    "js", "mjs" -> "application/javascript"
    "css" -> "text/css"
    "json" -> "application/json"
    "wasm" -> "application/wasm"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "webp" -> "image/webp"
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    "mp4" -> "video/mp4"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}

private data class ChildDoc(val id: String, val name: String, val directory: Boolean)

internal class TreeDocuments(private val context: Context, private val treeUri: Uri) {
    private val children = HashMap<String, List<ChildDoc>>()

    fun listFiles(): List<FolderEntry> = runCatching {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val found = ArrayList<FolderEntry>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootId to "")
        var seen = 0
        while (queue.isNotEmpty() && seen < 500) {
            val (parentId, relative) = queue.removeFirst()
            for (child in loadChildren(parentId)) {
                seen++
                val path = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                if (child.directory) {
                    val depth = path.count { it == '/' }
                    if (depth < 4 && child.name != "node_modules" && !child.name.startsWith(".")) queue.add(child.id to path)
                } else {
                    found.add(FolderEntry(path))
                }
            }
        }
        found
    }.getOrDefault(emptyList())

    fun readText(relativePath: String, maxChars: Int): String? {
        val uri = uriFor(relativePath) ?: return null
        return open(uri)?.bufferedReader()?.use { reader ->
            val buffer = CharArray(maxChars)
            val count = reader.read(buffer)
            if (count <= 0) "" else String(buffer, 0, count)
        }
    }

    fun open(uri: Uri) = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    fun uriFor(relativePath: String): Uri? {
        val relative = normalizeRelative(relativePath) ?: return null
        if (relative.isEmpty()) return null
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        var parentId = rootId
        for (segment in relative.split('/')) {
            val match = loadChildren(parentId).firstOrNull { it.name == segment }
                ?: loadChildren(parentId).firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: return null
            parentId = match.id
        }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
    }

    private fun loadChildren(parentId: String): List<ChildDoc> = synchronized(children) {
        children.getOrPut(parentId) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn) ?: continue
                        add(
                            ChildDoc(
                                id = cursor.getString(idColumn),
                                name = name,
                                directory = cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR,
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }
    }
}
