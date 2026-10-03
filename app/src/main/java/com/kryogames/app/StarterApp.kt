package com.kryogames.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface Overlay {
    data class Options(val id: String) : Overlay
    data class Message(val title: String, val body: String) : Overlay
    data object Browser : Overlay
}

/** Demo host. Replace its dataset/callbacks with your repository, not the layout. */
@Composable
fun StarterApp(controllerActions: Flow<ControllerAction>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val online = rememberDeviceOnline()
    var favoriteIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var aspectName by rememberSaveable { mutableStateOf(loadAspect(context)?.name) }
    var imported by remember { mutableStateOf(loadStoredImports(context)) }
    val games = remember(favoriteIds, imported) {
        (DemoGames.all + imported.map { it.asGame() }).map { it.copy(favorite = it.id in favoriteIds) }
    }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    fun importChosenFolder(dir: File) {
        overlay = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { importDirectory(dir) }
            when (result) {
                is ImportResult.Ready -> {
                    val next = upsertImport(imported, result.game)
                    imported = next
                    saveStoredImports(context, next)
                }
                is ImportResult.Failed -> overlay = Overlay.Message("Add a game", result.message)
            }
        }
    }
    fun play(game: Game) {
        overlay = null
        val error = launchGame(context, game)
        if (error != null) overlay = Overlay.Message("Launch setup", error)
    }
    BackHandler(enabled = overlay != null) { overlay = null }
    Box(Modifier.fillMaxSize()) {
        LibraryScreen(games = games, controllerActions = controllerActions, modalOpen = overlay != null,
            online = online,
            aspect = aspectName?.let { runCatching { DisplayAspect.valueOf(it) }.getOrNull() },
            onPlay = ::play,
            onOptions = { overlay = Overlay.Options(it.id) },
            onNotifications = { overlay = Overlay.Message("Notifications", "You're all caught up. Connect this action to your notifications repository.") },
            onProfile = { overlay = Overlay.Message("Kidxpr", "Your profile area. Replace the demo name and avatar with your authenticated user.") },
            onToggleFavorite = { game ->
                favoriteIds = if (game.favorite) favoriteIds - game.id else favoriteIds + game.id
            },
            onAddGame = { overlay = Overlay.Browser },
            onAspect = { chosen ->
                aspectName = chosen.name
                saveAspect(context, chosen)
            },
            onSidebarAction = { name ->
                overlay = when (name) {
                    "Downloads" -> Overlay.Message(
                        "Downloads",
                        "Installed demo entries:\n" + games.filter { it.installed }.joinToString("\n") { it.title }.ifBlank { "Nothing installed yet." },
                    )
                    "Log out" -> Overlay.Message("Log out", "Sign-in isn't connected on this device yet.")
                    else -> Overlay.Message(name, "This section is ready for a later update.")
                }
            })
        overlay?.let { current ->
            when (current) {
                is Overlay.Message -> ModalPanel(current.title, current.body,
                    actions = listOf("Close" to { overlay = null }), onDismiss = { overlay = null })
                Overlay.Browser -> FolderBrowser(
                    onChoose = ::importChosenFolder,
                    onDismiss = { overlay = null },
                )
                is Overlay.Options -> {
                    val game = games.first { it.id == current.id }
                    ModalPanel(game.title, if (game.platform == GamePlatform.WEB) "Web game" else "Android game",
                        actions = listOf(
                            "Play" to { play(game) },
                            (if (game.favorite) "Remove from favorites" else "Add to favorites") to {
                                favoriteIds = if (game.favorite) favoriteIds - game.id else favoriteIds + game.id
                            },
                            "Close" to { overlay = null },
                        ), onDismiss = { overlay = null })
                }
            }
        }
    }
}

/** In-tree modal: retains Activity controller mappings and disables background focus. */
@Composable
private fun ModalPanel(title: String, body: String, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val focus = remember(title, actions.size) { List(actions.size) { FocusRequester() } }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.96f) }
    LaunchedEffect(title) {
        launch {
            alpha.animateTo(1f, tween(180))
        }
        launch {
            scale.animateTo(1f, tween(200))
        }
        withFrameNanos { }
        focus.firstOrNull()?.requestFocus()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color.Black.copy(alpha = 0.75f * alpha.value))
        .pointerInput(Unit) { detectTapGestures { onDismiss() } }, contentAlignment = Alignment.Center) {
        Column(Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }.widthIn(max = 440.dp).fillMaxWidth(0.84f).heightIn(max = 460.dp)
            .background(KryoColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, KryoColors.Border, RoundedCornerShape(14.dp))
            // Consume taps inside the panel instead of dismissing the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(title, color = KryoColors.Text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(body, color = KryoColors.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            actions.forEachIndexed { i, (label, callback) ->
                FocusBox(Modifier.fillMaxWidth().height(44.dp).focusRequester(focus[i]).focusProperties {
                    up = focus.getOrElse(i - 1) { FocusRequester.Cancel }
                    down = focus.getOrElse(i + 1) { FocusRequester.Cancel }
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }, label, outlined = true, onClick = callback) {
                    Text(label, color = KryoColors.Text, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private const val SettingsPrefs = "kryo_settings"
private const val AspectKey = "aspect"

@Composable
private fun rememberDeviceOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(deviceHasInternet(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) return@DisposableEffect onDispose { }
        val main = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { online = deviceHasInternet(context) } }
            override fun onLost(network: Network) { main.post { online = deviceHasInternet(context) } }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                main.post { online = deviceHasInternet(context) }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback, main) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun deviceHasInternet(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

internal fun loadAspect(context: Context): DisplayAspect? =
    when (context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE).getString(AspectKey, null)) {
        DisplayAspect.CLASSIC.name -> DisplayAspect.CLASSIC
        DisplayAspect.WIDESCREEN.name -> DisplayAspect.WIDESCREEN
        else -> null
    }

internal fun saveAspect(context: Context, aspect: DisplayAspect) {
    context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE).edit().putString(AspectKey, aspect.name).apply()
}

/** Returns a visible error instead of silently failing on unconfigured demo games. */
fun launchGame(context: Context, game: Game): String? {
    val tree = game.contentTreeUri
    val entry = game.contentEntry
    if (tree != null && entry != null) {
        if (!canReadTree(context, tree)) return "KryoGames no longer has access to ${game.title}. Add that folder again."
        val parsed = Uri.parse(tree)
        val launch = Intent(context, WebGameActivity::class.java)
            .putExtra(WebGameActivity.EXTRA_ENTRY, entry)
            .putExtra(WebGameActivity.EXTRA_TITLE, game.title)
        if (parsed.scheme == "file") {
            val path = parsed.path ?: return "KryoGames no longer has access to ${game.title}. Add that folder again."
            launch.putExtra(WebGameActivity.EXTRA_DIRECTORY, path)
        } else {
            launch.putExtra(WebGameActivity.EXTRA_TREE, tree)
        }
        context.startActivity(launch)
        return null
    }
    return when (game.platform) {
    GamePlatform.WEB -> {
        val path = game.webAssetPath
        if (path == null) "Set webAssetPath for ${game.title} to a local assets folder entry point."
        else {
            context.startActivity(Intent(context, WebGameActivity::class.java)
                .putExtra(WebGameActivity.EXTRA_ASSET, path).putExtra(WebGameActivity.EXTRA_TITLE, game.title))
            null
        }
    }
    GamePlatform.ANDROID -> {
        val packageName = game.androidPackage
        if (packageName == null) "Set androidPackage for ${game.title} to the installed Android game's package name."
        else {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) "${game.title} is not installed or does not expose a launcher activity."
            else try { context.startActivity(intent); null }
            catch (_: ActivityNotFoundException) { "${game.title} could not be opened." }
            catch (_: SecurityException) { "Android did not allow this game to be opened." }
        }
    }
}
}
