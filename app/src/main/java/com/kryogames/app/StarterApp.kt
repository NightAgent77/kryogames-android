package com.kryogames.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

/** API 33 action. The installed platform stub does not declare this constant. */
private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

private sealed interface Overlay {
    data class Options(val id: String) : Overlay
    data class Message(val title: String, val body: String) : Overlay
    data object Browser : Overlay
    data object FilePrompt : Overlay
}

/** Demo host. Replace its dataset/callbacks with your repository, not the layout. */
@Composable
fun StarterApp(controllerActions: Flow<ControllerAction>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val online = rememberDeviceOnline()
    var favoriteIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var removedIds by rememberSaveable { mutableStateOf(loadRemovedGameIds(context).toList()) }
    var aspectName by rememberSaveable { mutableStateOf(loadAspect(context)?.name) }
    var appearanceName by rememberSaveable { mutableStateOf(loadAppearance(context).name) }
    var imported by remember { mutableStateOf(loadStoredImports(context)) }
    val games = remember(favoriteIds, imported, removedIds) {
        (DemoGames.all.filter { it.id !in removedIds } + imported.map { it.asGame() })
            .map { it.copy(favorite = it.id in favoriteIds) }
    }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var awaitingControls by remember { mutableStateOf(false) }
    val addGame = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val kept = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!kept) {
            overlay = Overlay.Message("Add a game", "Android didn't allow access to that folder.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { importTree(context, uri) }
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
    fun openFileApp() {
        overlay = null
        try {
            addGame.launch(null)
        } catch (_: ActivityNotFoundException) {
            overlay = Overlay.Browser
        }
    }
    fun requestFileControls() {
        awaitingControls = true
        overlay = null
        val component = ComponentName(context, FilePickerControls::class.java)
        val details = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        try {
            context.startActivity(details)
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
    }
    val activity = context.findActivity()
    DisposableEffect(activity, awaitingControls) {
        if (activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME || !awaitingControls) return@LifecycleEventObserver
            awaitingControls = false
            if (fileControlsEnabled(context)) openFileApp() else overlay = Overlay.FilePrompt
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
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
    fun removeFromLibrary(game: Game) {
        favoriteIds = favoriteIds - game.id
        val stored = imported.firstOrNull { it.id == game.id }
        if (stored != null) {
            val next = imported.filterNot { it.id == game.id }
            imported = next
            saveStoredImports(context, next)
            releaseStoredImport(context, stored)
            return
        }
        val nextRemoved = (removedIds + game.id).distinct()
        removedIds = nextRemoved
        saveRemovedGameIds(context, nextRemoved.toSet())
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
            onDelete = ::removeFromLibrary,
            onAddGame = {
                if (fileControlsEnabled(context) || skipFilePrompt(context)) openFileApp()
                else overlay = Overlay.FilePrompt
            },
            onAspect = { chosen ->
                aspectName = chosen.name
                saveAspect(context, chosen)
            },
            appearance = runCatching { KryoAppearance.valueOf(appearanceName) }.getOrDefault(KryoAppearance.DARK),
            onAppearance = { chosen ->
                appearanceName = chosen.name
                KryoThemeState.appearance = chosen
                saveAppearance(context, chosen)
            },
            onExit = { activity?.finishAndRemoveTask() },
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
                Overlay.FilePrompt -> ModalPanel(
                    "File app",
                    "Android's file app opens so you can choose a game folder with the controller. Turn on KryoGames file controls once if the stick or A button does nothing there. The D-pad works either way.",
                    actions = listOf(
                        "Open file app" to {
                            rememberSkipFilePrompt(context)
                            openFileApp()
                        },
                        "Turn on controls" to { requestFileControls() },
                    ),
                    onDismiss = { overlay = null },
                )
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
        focus.firstOrNull()?.bringIntoFocus()
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

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val SettingsPrefs = "kryo_settings"
private const val AspectKey = "aspect"
private const val AppearanceKey = "appearance"

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

internal fun loadAppearance(context: Context): KryoAppearance =
    when (context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE).getString(AppearanceKey, null)) {
        KryoAppearance.LIGHT.name -> KryoAppearance.LIGHT
        else -> KryoAppearance.DARK
    }

internal fun saveAppearance(context: Context, appearance: KryoAppearance) {
    context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE).edit().putString(AppearanceKey, appearance.name).apply()
}

private const val RemovedGamesKey = "removed_games"

internal fun loadRemovedGameIds(context: Context): Set<String> =
    context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE)
        .getStringSet(RemovedGamesKey, emptySet())
        .orEmpty()

internal fun saveRemovedGameIds(context: Context, ids: Set<String>) {
    context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(RemovedGamesKey, HashSet(ids))
        .commit()
}

private const val SkipFilePromptKey = "skip_file_prompt"

internal fun skipFilePrompt(context: Context): Boolean =
    context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE).getBoolean(SkipFilePromptKey, false)

internal fun rememberSkipFilePrompt(context: Context) {
    context.getSharedPreferences(SettingsPrefs, Context.MODE_PRIVATE).edit().putBoolean(SkipFilePromptKey, true).apply()
}

internal fun fileControlsEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val component = ComponentName(context, FilePickerControls::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
        val service = info.resolveInfo?.serviceInfo ?: return@any false
        service.packageName == component.packageName && service.name == component.className
    }
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
