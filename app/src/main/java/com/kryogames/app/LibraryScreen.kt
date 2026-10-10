package com.kryogames.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal val LocalUiEnabled = compositionLocalOf { true }
private val LocalFrosted = compositionLocalOf { false }
internal data class Metrics(val scale: Float) {
    fun d(value: Int): Dp = (value * scale).dp
    fun t(value: Int, minimum: Int = 11): TextUnit = (value * scale).coerceAtLeast(minimum.toFloat()).sp
}

private const val MotionIn = 220
private const val MotionOut = 160
private val MotionEase = FastOutSlowInEasing
private fun <T> motionIn() = tween<T>(MotionIn, easing = MotionEase)
private fun <T> motionOut() = tween<T>(MotionOut, easing = MotionEase)

private enum class SearchPhase { Closed, Highlighted, Editing }

private enum class ShellSlot { MENU, DISCOVER, LIBRARY, FAVORITES, SEARCH, BELL, PROFILE, GRID, RAIL, PAGE }

/** Play, Info, Delete. */
private const val ChoiceLast = 2

private enum class NavCue { Move, Back, Limit }

private fun NavCue.play() {
    when (this) {
        NavCue.Move -> UiCue.move()
        NavCue.Back -> UiCue.back()
        NavCue.Limit -> UiCue.limit()
    }
}

private val HeaderSlots = listOf(
    ShellSlot.MENU, ShellSlot.DISCOVER, ShellSlot.LIBRARY, ShellSlot.FAVORITES,
    ShellSlot.SEARCH, ShellSlot.BELL, ShellSlot.PROFILE,
)

private val HeaderSections = listOf("Discover", "Library", "Favorites")

private fun FocusRequester.requestSafe(): Boolean = try {
    requestFocus()
} catch (_: IllegalStateException) {
    false
}

internal suspend fun FocusRequester.bringIntoFocus() {
    repeat(8) {
        withFrameNanos { }
        if (requestSafe()) return
        delay(16)
    }
}

/** Avoids the ColumnScope AnimatedVisibility overload when this sits in a nested Box. */
@Composable
private fun OverlayVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: androidx.compose.animation.EnterTransition = fadeIn(),
    exit: androidx.compose.animation.ExitTransition = fadeOut(),
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit, content = { content() })
}

internal data class PopupPlace(val x: Float, val y: Float, val placeOnLeft: Boolean)

/** Pins a compact menu to the side of a game card, flipping sides when the edge is too close. */
internal fun placeGameMenu(
    anchorLeft: Float,
    anchorTop: Float,
    anchorRight: Float,
    anchorBottom: Float,
    frameWidth: Float,
    frameHeight: Float,
    menuWidth: Float,
    menuHeight: Float,
    gap: Float = 8f,
    margin: Float = 8f,
): PopupPlace {
    val spaceRight = frameWidth - anchorRight - gap - margin
    val spaceLeft = anchorLeft - gap - margin
    val placeOnLeft = spaceRight < menuWidth && spaceLeft >= menuWidth
    val rawX = if (placeOnLeft) anchorLeft - gap - menuWidth else anchorRight + gap
    val maxX = (frameWidth - menuWidth - margin).coerceAtLeast(margin)
    val x = rawX.coerceIn(margin, maxX)
    val minY = margin
    val maxY = (frameHeight - menuHeight - margin).coerceAtLeast(minY)
    val y = ((anchorTop + anchorBottom) / 2f - menuHeight / 2f).coerceIn(minY, maxY)
    return PopupPlace(x, y, placeOnLeft)
}

/** Public integration entry point. Data and launch callbacks stay outside the UI. */
@Composable
fun LibraryScreen(
    games: List<Game>,
    discoverGames: List<Game> = emptyList(),
    controllerActions: Flow<ControllerAction> = emptyFlow(),
    username: String = "Kidxpr",
    online: Boolean = true,
    modalOpen: Boolean = false,
    onPlay: (Game) -> Unit,
    onOptions: (Game) -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onSidebarAction: (String) -> Unit,
    aspect: DisplayAspect? = null,
    onAspect: (DisplayAspect) -> Unit = {},
    appearance: KryoAppearance = KryoAppearance.DARK,
    onAppearance: (KryoAppearance) -> Unit = {},
    onToggleFavorite: (Game) -> Unit = {},
    onDelete: (Game) -> Unit = {},
    onInstall: (Game) -> Unit = {},
    installingId: String? = null,
    liveTransfer: LiveTransfer? = null,
    finishedDownloads: List<FinishedDownload> = emptyList(),
    onStopDownload: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onAddGame: () -> Unit = {},
    onExit: () -> Unit = {},
    signedIn: Boolean = true,
    avatar: String? = null,
    friends: FriendsBoardState = FriendsBoardState(),
    onFriendsQuery: (String) -> Unit = {},
    onFriendAdd: (PlayerProfile) -> Unit = {},
    onFriendAccept: (FriendRequest) -> Unit = {},
    onFriendDecline: (FriendRequest) -> Unit = {},
    onFriendRemove: (FriendEntry) -> Unit = {},
) {
    var sidebarOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.ALL.name) }
    var section by rememberSaveable { mutableStateOf("Library") }
    var lastFocusedGameId by rememberSaveable { mutableStateOf(games.firstOrNull()?.id) }
    var focusOnAdd by rememberSaveable { mutableStateOf(false) }
    var actionGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingInfo by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var profileMenuOpen by rememberSaveable { mutableStateOf(false) }
    var addMenuOpen by rememberSaveable { mutableStateOf(false) }
    var searchPhaseName by rememberSaveable { mutableStateOf(SearchPhase.Closed.name) }
    var rootHasFocus by remember { mutableStateOf(false) }
    var slotName by rememberSaveable { mutableStateOf(ShellSlot.GRID.name) }
    var gridIndex by rememberSaveable { mutableIntStateOf(0) }
    var railIndex by rememberSaveable { mutableIntStateOf(0) }
    var choiceRow by rememberSaveable { mutableIntStateOf(0) }
    var menuIndex by rememberSaveable { mutableIntStateOf(0) }
    val slot = runCatching { ShellSlot.valueOf(slotName) }.getOrDefault(ShellSlot.GRID)
    val layoutColumns = remember { intArrayOf(4) }
    val resolvedAspect = remember { mutableStateOf(DisplayAspect.WIDESCREEN) }
    val railFocus = remember { List(5) { FocusRequester() } }
    val sectionFocus = remember { List(HeaderSections.size) { FocusRequester() } }
    val addFocus = remember { FocusRequester() }
    val searchButtonFocus = remember { FocusRequester() }
    var firstFocusPlaced by remember { mutableStateOf(false) }
    val searchPhase = SearchPhase.valueOf(searchPhaseName)
    val filter = LibraryFilter.valueOf(filterName)
    val offersAddSlot = section != "Discover"
    val visible = remember(games, discoverGames, filter, section, query) {
        val source = if (section == "Discover") discoverGames else games
        val effective = if (section == "Favorites") LibraryFilter.FAVORITES else filter
        filterGames(source, effective, query)
    }
    val ids = visible.map { it.id }
    val cardFocus = remember(ids) { ids.associateWith { FocusRequester() } }
    val menuFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val friendsAnchor = remember { FocusRequester() }
    LaunchedEffect(section, query) {
        if (section == "Friends") onFriendsQuery(query)
    }
    val gridState = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val selectedGame = games.firstOrNull { it.id == lastFocusedGameId } ?: visible.firstOrNull()
    val actionGame = discoverGames.firstOrNull { it.id == actionGameId } ?: games.firstOrNull { it.id == actionGameId }
    val choiceLast = if (actionGame?.installed == true) ChoiceLast else 1
    val anchors = remember { mutableStateMapOf<String, Rect>() }
    var frameCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val shellBlocked = profileMenuOpen || addMenuOpen || actionGameId != null || showingInfo || modalOpen
    val soundContext = LocalContext.current.applicationContext
    DisposableEffect(soundContext) {
        UiCue.attach()
        onDispose { }
    }
    val controllerHost = LocalContext.current.findControllerActivity()
    DisposableEffect(Unit) {
        UiCue.attach()
        onDispose { }
    }
    SideEffect {
        if (gridIndex > visible.size) gridIndex = visible.size
        if (slot == ShellSlot.GRID) {
            visible.getOrNull(gridIndex)?.let { if (lastFocusedGameId != it.id) lastFocusedGameId = it.id }
            val onAdd = gridIndex >= visible.size
            if (focusOnAdd != onAdd) focusOnAdd = onAdd
        }
        controllerHost?.libraryOwnsDirections = !modalOpen
    }

    fun frameRect(coords: LayoutCoordinates): Rect? {
        val frame = frameCoordinates ?: return null
        if (!frame.isAttached || !coords.isAttached) return null
        val pos = frame.localPositionOf(coords, Offset.Zero)
        return Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
    }
    fun rememberAnchor(key: String, coords: LayoutCoordinates) {
        val rect = frameRect(coords) ?: return
        if (anchors[key] != rect) anchors[key] = rect
    }
    fun openGameChoices(game: Game) {
        profileMenuOpen = false
        addMenuOpen = false
        if (slotName == ShellSlot.PAGE.name) slotName = ShellSlot.GRID.name
        settingsOpen = false
        actionGameId = game.id
        showingInfo = false
        lastFocusedGameId = game.id
        val index = visible.indexOfFirst { it.id == game.id }
        if (index >= 0) {
            gridIndex = index
            focusOnAdd = false
        }
        choiceRow = 0
        menuIndex = 0
    }
    fun openInfo(game: Game) {
        lastFocusedGameId = game.id
        actionGameId = game.id
        showingInfo = true
        menuIndex = 0
    }
    fun settingsStartIndex() = if (resolvedAspect.value == DisplayAspect.CLASSIC) 1 else 0
    fun closeChoices() {
        actionGameId = null
        showingInfo = false
    }
    fun closeSettings() {
        settingsOpen = false
        if (slotName == ShellSlot.PAGE.name) slotName = ShellSlot.GRID.name
    }
    fun openSettings() {
        profileMenuOpen = false
        addMenuOpen = false
        closeChoices()
        sidebarOpen = false
        settingsOpen = true
        menuIndex = settingsStartIndex()
        slotName = ShellSlot.PAGE.name
    }
    fun toggleSidebar() {
        sidebarOpen = !sidebarOpen
        if (sidebarOpen) {
            railIndex = when {
                settingsOpen -> 3
                section == "Friends" -> 1
                section == "Downloads" -> 2
                else -> 0
            }
            slotName = ShellSlot.RAIL.name
        } else if (slotName == ShellSlot.RAIL.name) {
            slotName = ShellSlot.MENU.name
        }
    }
    fun applySection(next: String) {
        if (settingsOpen) closeSettings()
        section = next
        profileMenuOpen = false
        closeChoices()
        gridIndex = 0
        if (next == "Favorites") filterName = LibraryFilter.FAVORITES.name
        else if (next == "Library" || next == "Discover") filterName = LibraryFilter.ALL.name
        if (next == "Discover") onOpenDiscover()
        if (next == "Friends") {
            query = ""
            if (slotName == ShellSlot.GRID.name) slotName = ShellSlot.LIBRARY.name
        }
    }
    fun openDownloads() {
        section = "Downloads"
        profileMenuOpen = false
        closeChoices()
        closeSettings()
        sidebarOpen = false
        menuIndex = 0
        slotName = ShellSlot.PAGE.name
    }
    fun closeSearch() {
        searchPhaseName = SearchPhase.Closed.name
        query = ""
        keyboard?.hide()
    }
    fun toggleSearch() {
        if (searchPhase == SearchPhase.Closed) {
            if (settingsOpen) closeSettings()
            searchPhaseName = SearchPhase.Highlighted.name
        } else closeSearch()
    }
    fun activateSearch() {
        if (searchPhase == SearchPhase.Highlighted) searchPhaseName = SearchPhase.Editing.name
    }
    fun sectionSlot(): ShellSlot = when (section) {
        "Discover" -> ShellSlot.DISCOVER
        "Favorites" -> ShellSlot.FAVORITES
        else -> ShellSlot.LIBRARY
    }
    val restoreFocus = rememberUpdatedState {
        when {
            section == "Downloads" -> true
            section == "Friends" -> menuFocus.requestSafe()
            searchPhase != SearchPhase.Closed -> searchFocus.requestSafe()
            focusOnAdd || ids.isEmpty() -> addFocus.requestSafe() || menuFocus.requestSafe()
            else -> {
                val target = cardFocus[lastFocusedGameId] ?: ids.firstOrNull()?.let { cardFocus[it] }
                target?.requestSafe() == true || menuFocus.requestSafe()
            }
        }
        Unit
    }
    val rootFocusedNow by rememberUpdatedState(rootHasFocus)
    val blockedNow by rememberUpdatedState(shellBlocked)

    fun moveSettings(dx: Int, dy: Int): NavCue {
        if (dx < 0 && menuIndex == 1) {
            menuIndex = 0
            return NavCue.Move
        }
        if (dx > 0 && menuIndex == 0) {
            menuIndex = 1
            return NavCue.Move
        }
        if (menuIndex == 2 && dx < 0) {
            if (appearance == KryoAppearance.DARK) return NavCue.Limit
            onAppearance(KryoAppearance.DARK)
            return NavCue.Move
        }
        if (menuIndex == 2 && dx > 0) {
            if (appearance == KryoAppearance.LIGHT) return NavCue.Limit
            onAppearance(KryoAppearance.LIGHT)
            return NavCue.Move
        }
        if (dy > 0 && menuIndex < 2) {
            menuIndex = 2
            return NavCue.Move
        }
        if (dy > 0 && menuIndex == 2) {
            menuIndex = 3
            return NavCue.Move
        }
        if (dy < 0 && menuIndex == 3) {
            menuIndex = 2
            return NavCue.Move
        }
        if (dy < 0 && menuIndex == 2) {
            menuIndex = if (resolvedAspect.value == DisplayAspect.CLASSIC) 1 else 0
            return NavCue.Move
        }
        if (dy < 0 && (menuIndex == 0 || menuIndex == 1)) {
            slotName = sectionSlot().name
            return NavCue.Move
        }
        if (dx < 0 && (menuIndex == 0 || menuIndex == 3)) {
            slotName = if (sidebarOpen) ShellSlot.RAIL.name else ShellSlot.MENU.name
            return NavCue.Move
        }
        return NavCue.Limit
    }
    fun moveCursor(dx: Int, dy: Int): NavCue? {
        if (modalOpen) return null
        if (searchPhase != SearchPhase.Closed) return NavCue.Limit
        if (showingInfo) {
            return when {
                dx > 0 && menuIndex == 0 -> {
                    menuIndex = 1
                    NavCue.Move
                }
                dx < 0 && menuIndex == 1 -> {
                    menuIndex = 0
                    NavCue.Move
                }
                dy < 0 -> {
                    closeChoices()
                    NavCue.Back
                }
                else -> NavCue.Limit
            }
        }
        if (settingsOpen && slot == ShellSlot.PAGE) return moveSettings(dx, dy)
        if (section == "Downloads" && slot == ShellSlot.PAGE) return NavCue.Limit
        if (profileMenuOpen) {
            return when {
                dy < 0 && menuIndex > 0 -> {
                    menuIndex--
                    NavCue.Move
                }
                dy > 0 && menuIndex < 1 -> {
                    menuIndex++
                    NavCue.Move
                }
                else -> NavCue.Limit
            }
        }
        if (addMenuOpen) return NavCue.Limit
        if (actionGameId != null) {
            if (dy == 0) return NavCue.Limit
            val next = (choiceRow + dy).coerceIn(0, choiceLast)
            return if (next == choiceRow) NavCue.Limit else {
                choiceRow = next
                NavCue.Move
            }
        }
        return when (slot) {
            ShellSlot.GRID -> {
                val count = visible.size + if (offersAddSlot) 1 else 0
                val current = gridIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
                val next = gridNeighbor(current, count, layoutColumns[0].coerceAtLeast(1), dx, dy)
                when {
                    next != null -> {
                        gridIndex = next
                        NavCue.Move
                    }
                    dy < 0 -> {
                        slotName = sectionSlot().name
                        NavCue.Move
                    }
                    dx < 0 && sidebarOpen -> {
                        slotName = ShellSlot.RAIL.name
                        NavCue.Move
                    }
                    dx < 0 -> {
                        slotName = ShellSlot.MENU.name
                        NavCue.Move
                    }
                    else -> NavCue.Limit
                }
            }
            ShellSlot.RAIL -> when {
                dy < 0 && railIndex > 0 -> {
                    railIndex--
                    NavCue.Move
                }
                dy > 0 && railIndex < 4 -> {
                    railIndex++
                    NavCue.Move
                }
                dx > 0 -> {
                    slotName = if (settingsOpen) ShellSlot.PAGE.name else ShellSlot.GRID.name
                    NavCue.Move
                }
                else -> NavCue.Limit
            }
            ShellSlot.PAGE -> {
                slotName = ShellSlot.GRID.name
                NavCue.Move
            }
            else -> {
                val index = HeaderSlots.indexOf(slot)
                if (dx != 0 && index >= 0) {
                    val next = (index + dx).coerceIn(0, HeaderSlots.lastIndex)
                    if (next != index) {
                        slotName = HeaderSlots[next].name
                        NavCue.Move
                    } else if (dx < 0 && sidebarOpen && slot == ShellSlot.MENU) {
                        slotName = ShellSlot.RAIL.name
                        NavCue.Move
                    } else {
                        NavCue.Limit
                    }
                } else if (dy > 0 && settingsOpen) {
                    slotName = ShellSlot.PAGE.name
                    NavCue.Move
                } else if (dy > 0 && section == "Downloads") {
                    slotName = ShellSlot.PAGE.name
                    NavCue.Move
                } else if (dy > 0 && section != "Friends") {
                    slotName = ShellSlot.GRID.name
                    NavCue.Move
                } else {
                    NavCue.Limit
                }
            }
        }
    }
    fun confirmCursor(): Boolean {
        if (modalOpen) return false
        if (searchPhase == SearchPhase.Highlighted) {
            activateSearch()
            return true
        }
        if (searchPhase == SearchPhase.Editing) return false
        if (showingInfo) {
            val game = actionGame ?: return false
            when (menuIndex) {
                0 -> if (game.installed) {
                    closeChoices()
                    onPlay(game)
                } else if (installingId != game.id) {
                    showingInfo = false
                    onInstall(game)
                }
                else -> onToggleFavorite(game)
            }
            return true
        }
        if (settingsOpen && slot == ShellSlot.PAGE) {
            when (menuIndex) {
                0 -> onAspect(DisplayAspect.WIDESCREEN)
                1 -> onAspect(DisplayAspect.CLASSIC)
                2 -> onAppearance(if (appearance == KryoAppearance.DARK) KryoAppearance.LIGHT else KryoAppearance.DARK)
                else -> closeSettings()
            }
            return true
        }
        if (profileMenuOpen) {
            when (menuIndex) {
                0 -> {
                    profileMenuOpen = false
                    onProfile()
                }
                else -> {
                    profileMenuOpen = false
                    onSidebarAction(if (signedIn) "Log out" else "Create account")
                }
            }
            return true
        }
        if (addMenuOpen) {
            addMenuOpen = false
            onAddGame()
            return true
        }
        if (section == "Downloads" && slot == ShellSlot.PAGE && !settingsOpen) {
            val transfer = liveTransfer
            if (transfer != null && !transfer.settling) onStopDownload()
            return true
        }
        if (actionGameId != null) {
            val game = actionGame ?: return false
            when (choiceRow) {
                0 -> if (game.installed) {
                    closeChoices()
                    onPlay(game)
                } else if (installingId != game.id) onInstall(game)
                1 -> openInfo(game)
                else -> if (game.installed) {
                    closeChoices()
                    onDelete(game)
                }
            }
            return true
        }
        when (slot) {
            ShellSlot.MENU -> toggleSidebar()
            ShellSlot.DISCOVER -> applySection("Discover")
            ShellSlot.LIBRARY -> applySection("Library")
            ShellSlot.FAVORITES -> applySection("Favorites")
            ShellSlot.SEARCH -> if (searchPhase == SearchPhase.Closed) toggleSearch() else return false
            ShellSlot.BELL -> onNotifications()
            ShellSlot.PROFILE -> {
                profileMenuOpen = !profileMenuOpen
                if (profileMenuOpen) menuIndex = 0
            }
            ShellSlot.GRID -> {
                if (offersAddSlot && gridIndex >= visible.size) {
                    profileMenuOpen = false
                    closeSettings()
                    closeChoices()
                    addMenuOpen = true
                    menuIndex = 0
                } else visible.getOrNull(gridIndex)?.let(::openGameChoices) ?: return false
            }
            ShellSlot.RAIL -> when (railIndex) {
                0 -> {
                    applySection("Library")
                    sidebarOpen = false
                    slotName = ShellSlot.GRID.name
                }
                1 -> {
                    section = "Friends"
                    profileMenuOpen = false
                    closeChoices()
                    closeSettings()
                    sidebarOpen = false
                    slotName = ShellSlot.LIBRARY.name
                }
                2 -> openDownloads()
                3 -> openSettings()
                else -> onExit()
            }
            ShellSlot.PAGE -> return false
        }
        return true
    }
    val handleAction by rememberUpdatedState<(ControllerAction) -> Unit> { action ->
        when (action) {
            ControllerAction.LEFT -> moveCursor(-1, 0)?.play()
            ControllerAction.RIGHT -> moveCursor(1, 0)?.play()
            ControllerAction.UP -> moveCursor(0, -1)?.play()
            ControllerAction.DOWN -> moveCursor(0, 1)?.play()
            ControllerAction.CONFIRM -> if (confirmCursor()) UiCue.select()
            else -> Unit
        }
        if (showingInfo && action != ControllerAction.LEFT && action != ControllerAction.RIGHT &&
            action != ControllerAction.UP && action != ControllerAction.DOWN && action != ControllerAction.CONFIRM
        ) closeChoices()
        if (!modalOpen && action == ControllerAction.SEARCH) {
            toggleSearch()
            UiCue.select()
        } else if (!modalOpen && actionGameId == null && !profileMenuOpen && !addMenuOpen && searchPhase == SearchPhase.Closed) when (action) {
            ControllerAction.MENU -> {
                toggleSidebar()
                UiCue.select()
            }
            ControllerAction.OPTIONS -> {
                val game = selectedGame
                if (game == null) {
                    UiCue.limit()
                    return@rememberUpdatedState
                }
                openGameChoices(game)
                UiCue.select()
            }
            ControllerAction.PREVIOUS_TAB -> {
                val index = HeaderSections.indexOf(section)
                if (index > 0) {
                    applySection(HeaderSections[index - 1])
                    UiCue.move()
                } else UiCue.limit()
            }
            ControllerAction.NEXT_TAB -> {
                val index = HeaderSections.indexOf(section)
                if (index in 0 until HeaderSections.lastIndex) {
                    applySection(HeaderSections[index + 1])
                    UiCue.move()
                } else UiCue.limit()
            }
            else -> Unit
        }
    }
    LaunchedEffect(controllerActions) { controllerActions.collect { handleAction(it) } }
    LaunchedEffect(gridIndex, slotName, shellBlocked, ids) {
        if (shellBlocked || slot != ShellSlot.GRID) return@LaunchedEffect
        val game = visible.getOrNull(gridIndex)
        if (game != null) cardFocus[game.id]?.bringIntoFocus()
        else if (offersAddSlot) addFocus.bringIntoFocus()
    }
    LaunchedEffect(searchPhase) {
        when (searchPhase) {
            SearchPhase.Highlighted -> keyboard?.hide()
            SearchPhase.Editing -> {
                searchFocus.bringIntoFocus()
                keyboard?.show()
            }
            SearchPhase.Closed -> keyboard?.hide()
        }
    }
    LaunchedEffect(ids) {
        if (!modalOpen && !shellBlocked && !firstFocusPlaced && section != "Friends") {
            if (ids.isNotEmpty()) gridState.scrollToItem(0)
            if (ids.isNotEmpty()) cardFocus[ids.first()]?.bringIntoFocus()
            else addFocus.bringIntoFocus()
            firstFocusPlaced = true
        }
    }
    var railWasOpen by remember { mutableStateOf(sidebarOpen) }
    var shellWasBlocked by remember { mutableStateOf(shellBlocked) }
    LaunchedEffect(sidebarOpen, shellBlocked) {
        val sidebarClosed = railWasOpen && !sidebarOpen
        val unblocked = shellWasBlocked && !shellBlocked
        railWasOpen = sidebarOpen
        shellWasBlocked = shellBlocked
        if (shellBlocked || (!sidebarClosed && !unblocked)) return@LaunchedEffect
        delay(180)
        repeat(4) {
            restoreFocus.value.invoke()
            delay(40)
        }
    }
    LaunchedEffect(rootHasFocus, firstFocusPlaced) {
        if (rootHasFocus || !firstFocusPlaced) return@LaunchedEffect
        repeat(5) {
            delay(50)
            if (rootFocusedNow || blockedNow) return@LaunchedEffect
            restoreFocus.value.invoke()
        }
    }
    BackHandler(enabled = !modalOpen && (settingsOpen || profileMenuOpen || addMenuOpen || actionGameId != null || section == "Friends" || section == "Downloads" || searchPhase != SearchPhase.Closed || sidebarOpen)) {
        when {
            addMenuOpen -> addMenuOpen = false
            profileMenuOpen -> profileMenuOpen = false
            actionGameId != null -> closeChoices()
            searchPhase != SearchPhase.Closed -> closeSearch()
            sidebarOpen -> {
                sidebarOpen = false
                if (slotName == ShellSlot.RAIL.name) slotName = ShellSlot.MENU.name
            }
            settingsOpen -> closeSettings()
            section == "Friends" || section == "Downloads" -> {
                applySection("Library")
                slotName = ShellSlot.GRID.name
            }
        }
        UiCue.back()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(KryoColors.Letterbox)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        val resolved = aspect ?: DisplayAspect.closest(maxWidth.value, maxHeight.value)
        val frameW: Dp
        val frameH: Dp
        if (aspect == null) {
            frameW = maxWidth
            frameH = maxHeight
        } else if (maxHeight.value > 0f && maxWidth / maxHeight > aspect.ratio) {
            frameH = maxHeight
            frameW = maxHeight * aspect.ratio
        } else {
            frameW = maxWidth
            frameH = if (aspect.ratio == 0f) maxHeight else maxWidth / aspect.ratio
        }
        val animatedW by animateDpAsState(frameW, motionIn(), label = "frameWidth")
        val animatedH by animateDpAsState(frameH, motionIn(), label = "frameHeight")
        val frameColor by androidx.compose.animation.animateColorAsState(
            KryoColors.Background,
            tween(MotionIn, easing = MotionEase),
            label = "frameColor",
        )
        Box(
            Modifier.size(animatedW, animatedH).align(Alignment.Center)
                .clipToBounds()
                .background(frameColor)
                .onGloballyPositioned { frameCoordinates = it }
                .onFocusChanged { rootHasFocus = it.hasFocus },
        ) {
            var washCover by remember { mutableStateOf<Game?>(null) }
            val infoGame = if (showingInfo) actionGame else null
            if (infoGame != null) washCover = infoGame
            OverlayVisibility(
                visible = showingInfo && washCover != null,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(420)),
                exit = fadeOut(tween(420)),
            ) {
                washCover?.let { game ->
                    if (game.cover != 0) {
                        Image(
                            painterResource(game.cover),
                            null,
                            Modifier.fillMaxSize().blur(24.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (game.cover != 0) 0.25f else 0.45f)))
                }
            }
            val designW = if (resolved == DisplayAspect.CLASSIC) 960f else 1100f
            val designH = if (resolved == DisplayAspect.CLASSIC) 720f else 688f
            val m = Metrics(min(animatedW.value / designW, animatedH.value / designH).coerceIn(0.8f, 1.35f))
            val chromeEnabled = !modalOpen && actionGameId == null && !profileMenuOpen && !addMenuOpen
            val shellBlur by animateDpAsState(
                when {
                    sidebarOpen -> 18.dp
                    profileMenuOpen || (actionGameId != null && !showingInfo) -> 20.dp
                    else -> 0.dp
                },
                motionIn(),
                label = "shellBlur",
            )
            val panelWidth = (animatedW * 0.30f).coerceAtLeast(m.d(220)).coerceAtMost(animatedW * 0.48f)
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    Modifier.fillMaxSize().then(if (shellBlur >= 0.5.dp) Modifier.blur(shellBlur) else Modifier),
                ) {
                        val narrow = maxWidth < 720.dp && resolved != DisplayAspect.CLASSIC
                        val availableGridWidth = maxWidth - m.d(38)
                        val columns = when {
                            availableGridWidth >= 720.dp -> 5
                            availableGridWidth >= 560.dp -> 4
                            availableGridWidth >= 420.dp -> 3
                            else -> 2
                        }
                        layoutColumns[0] = columns
                        resolvedAspect.value = resolved
                        val friendsVisible = section == "Friends" && !settingsOpen && !showingInfo
                        val libraryDown = when {
                            friendsVisible -> friendsAnchor
                            section == "Friends" -> menuFocus
                            visible.isNotEmpty() -> cardFocus.getValue(visible.first().id)
                            offersAddSlot -> addFocus
                            else -> sectionFocus[HeaderSections.indexOf(section).coerceAtLeast(0)]
                        }
                        val slotCount = visible.size + if (offersAddSlot) 1 else 0
                        fun slotTarget(index: Int): FocusRequester? = when {
                            index !in 0 until slotCount -> null
                            offersAddSlot && index == visible.size -> addFocus
                            else -> cardFocus[visible[index].id]
                        }
                        fun slotLink(index: Int, dx: Int, dy: Int): FocusRequester {
                            val next = gridNeighbor(index, slotCount, columns, dx, dy)
                            slotTarget(next ?: -1)?.let { return it }
                            return when {
                                dy < 0 -> sectionFocus[HeaderSections.indexOf(section).coerceAtLeast(0)]
                                dx < 0 && sidebarOpen -> railFocus[0]
                                dx < 0 -> menuFocus
                                else -> FocusRequester.Default
                            }
                        }
                        CompositionLocalProvider(LocalUiEnabled provides chromeEnabled, LocalFrosted provides showingInfo) {
                        val trayReserve by animateDpAsState(if (liveTransfer != null) m.d(92) else 0.dp, tween(200), label = "trayReserve")
                        Column(Modifier.fillMaxSize().padding(start = m.d(18), end = m.d(20), top = m.d(20), bottom = trayReserve)) {
                            Header(
                                m = m,
                                narrow = narrow,
                                section = section,
                                query = query,
                                username = username,
                                avatar = avatar,
                                searchPhase = searchPhase,
                                searchFocus = searchFocus,
                                searchButtonFocus = searchButtonFocus,
                                menuFocus = menuFocus,
                                sectionFocus = sectionFocus,
                                downTarget = libraryDown,
                                cursor = slot,
                                searchArmed = searchPhase != SearchPhase.Closed,
                                onSection = ::applySection,
                                onQuery = { query = it },
                                onToggleMenu = { toggleSidebar() },
                                onNotifications = onNotifications,
                                onProfile = {
                                    profileMenuOpen = !profileMenuOpen
                                    if (profileMenuOpen) menuIndex = 0
                                },
                                onProfilePlaced = { rememberAnchor("profile", it) },
                                onToggleSearch = { toggleSearch() },
                            )
                            if (!showingInfo && (settingsOpen || (section != "Friends" && section != "Downloads"))) {
                                Spacer(Modifier.height(m.d(16)))
                                Box(Modifier.padding(start = m.d(8))) {
                                    val heading = if (settingsOpen) "Settings" else section
                                    Crossfade(heading, animationSpec = tween(MotionIn, easing = MotionEase), label = "title") { Title(it, m) }
                                }
                                Spacer(Modifier.height(m.d(12)))
                            } else {
                                Spacer(Modifier.height(m.d(6)))
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                OverlayVisibility(
                                    visible = section != "Friends" && section != "Downloads" && !showingInfo && !settingsOpen,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { -it / 16 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { -it / 16 },
                                ) {
                                    Column(Modifier.fillMaxSize()) {
                                        if (visible.isEmpty()) {
                                            Text(
                                                if (section == "Discover") "No games to download right now." else "No games match your search or filter.",
                                                color = KryoColors.Muted,
                                                fontSize = m.t(15),
                                                modifier = Modifier.padding(start = m.d(8), bottom = m.d(12)),
                                            )
                                        }
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            state = gridState,
                                            modifier = Modifier.fillMaxSize().testTag("game_grid"),
                                            horizontalArrangement = Arrangement.spacedBy(m.d(12)),
                                            verticalArrangement = Arrangement.spacedBy(m.d(12)),
                                            contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, m.d(16)),
                                        ) {
                                            itemsIndexed(visible, key = { _, game -> game.id }) { index, game ->
                                                GameCard(
                                                    game = game,
                                                    m = m,
                                                    modifier = Modifier
                                                        .onGloballyPositioned { rememberAnchor(game.id, it) }
                                                        .focusRequester(cardFocus.getValue(game.id))
                                                        .focusProperties {
                                                            left = slotLink(index, -1, 0)
                                                            right = slotLink(index, 1, 0)
                                                            up = slotLink(index, 0, -1)
                                                            down = slotLink(index, 0, 1)
                                                        },
                                                    pinned = game.id == actionGameId,
                                                    highlighted = slot == ShellSlot.GRID && actionGameId == null && index == gridIndex,
                                                    onFocused = {
                                                        lastFocusedGameId = game.id
                                                        focusOnAdd = false
                                                    },
                                                    onSelect = { openGameChoices(game) },
                                                )
                                            }
                                            if (offersAddSlot) item(key = "add-slot") {
                                                val index = visible.size
                                                AddGameSlot(
                                                    m = m,
                                                    modifier = Modifier
                                                        .onGloballyPositioned { rememberAnchor("add-game", it) }
                                                        .focusRequester(addFocus)
                                                        .focusProperties {
                                                            left = slotLink(index, -1, 0)
                                                            right = slotLink(index, 1, 0)
                                                            up = slotLink(index, 0, -1)
                                                            down = slotLink(index, 0, 1)
                                                        }
                                                        .testTag("add_game"),
                                                    highlighted = slot == ShellSlot.GRID && actionGameId == null && index == gridIndex,
                                                    onFocused = { focusOnAdd = true },
                                                    onClick = {
                                                        profileMenuOpen = false
                                                        settingsOpen = false
                                                        closeChoices()
                                                        addMenuOpen = !addMenuOpen
                                                        if (addMenuOpen) menuIndex = 0
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                OverlayVisibility(
                                    visible = section == "Downloads" && !settingsOpen && !showingInfo,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { it / 16 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { it / 16 },
                                ) {
                                    DownloadsPane(
                                        m = m,
                                        transfer = liveTransfer,
                                        finished = finishedDownloads,
                                        stopHighlighted = slot == ShellSlot.PAGE,
                                        onStop = onStopDownload,
                                    )
                                }
                                OverlayVisibility(
                                    visible = section == "Friends" && !settingsOpen && !showingInfo,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { it / 16 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { it / 16 },
                                ) {
                                    FriendsBoard(
                                        m = m,
                                        signedIn = signedIn,
                                        state = friends,
                                        query = query,
                                        anchor = friendsAnchor,
                                        onSignIn = onProfile,
                                        onOpenSearch = { toggleSearch() },
                                        onAdd = onFriendAdd,
                                        onAccept = onFriendAccept,
                                        onDecline = onFriendDecline,
                                        onRemove = onFriendRemove,
                                    )
                                }
                                OverlayVisibility(
                                    visible = settingsOpen && !showingInfo,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { it / 16 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { it / 16 },
                                ) {
                                    CompositionLocalProvider(LocalUiEnabled provides !modalOpen) {
                                        SettingsPage(
                                            m = m,
                                            aspect = resolved,
                                            appearance = appearance,
                                            selectedIndex = if (slot == ShellSlot.PAGE) menuIndex else -1,
                                            onAspect = onAspect,
                                            onAppearance = onAppearance,
                                            onDismiss = { settingsOpen = false },
                                        )
                                    }
                                }
                                OverlayVisibility(
                                    visible = showingInfo && actionGame != null,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { it / 8 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { it / 10 },
                                ) {
                                    actionGame?.let { game ->
                                        CompositionLocalProvider(LocalUiEnabled provides !modalOpen) {
                                            GameInfoView(
                                                game = game,
                                                m = m,
                                                classic = resolved == DisplayAspect.CLASSIC,
                                                selection = menuIndex,
                                                onPlay = {
                                                    if (game.installed) {
                                                        closeChoices()
                                                        onPlay(game)
                                                    } else {
                                                        showingInfo = false
                                                        onInstall(game)
                                                    }
                                                },
                                                onToggleFavorite = { onToggleFavorite(game) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                }
                AnimatedVisibility(
                    visible = sidebarOpen,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(motionIn()),
                    exit = fadeOut(motionOut()),
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    sidebarOpen = false
                                    if (slotName == ShellSlot.RAIL.name) slotName = ShellSlot.MENU.name
                                    UiCue.back()
                                }
                            },
                    )
                }
                Sidebar(
                    open = sidebarOpen,
                    width = panelWidth,
                    m = m,
                    section = section,
                    settingsOpen = settingsOpen,
                    focus = railFocus,
                    railCursor = if (slot == ShellSlot.RAIL) railIndex else -1,
                    onHome = {
                        applySection("Library")
                        sidebarOpen = false
                    },
                    onFriends = {
                        section = "Friends"
                        profileMenuOpen = false
                        closeChoices()
                        sidebarOpen = false
                    },
                    onDownloads = { openDownloads() },
                    onSettings = { openSettings() },
                    onExit = onExit,
                )
            }

            val showChoices = actionGame != null && !showingInfo
            GameChoiceLayer(
                visible = showChoices,
                game = actionGame,
                selectedRow = choiceRow,
                installing = actionGame != null && installingId == actionGame.id,
                onPlay = { game ->
                    if (game.installed) {
                        closeChoices()
                        onPlay(game)
                    } else if (installingId != game.id) onInstall(game)
                },
                onInfo = { actionGame?.let(::openInfo) },
                onDelete = { game ->
                    closeChoices()
                    onDelete(game)
                },
                onDismiss = { closeChoices() },
            )
            DownloadTray(
                transfer = liveTransfer,
                onStop = onStopDownload,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
            AddGameMenu(
                visible = addMenuOpen && section != "Friends",
                anchor = anchors["add-game"],
                highlighted = addMenuOpen,
                onAdd = {
                    addMenuOpen = false
                    onAddGame()
                },
                onDismiss = { addMenuOpen = false },
            )
            ProfileMenuLayer(
                visible = profileMenuOpen,
                anchor = anchors["profile"],
                selectedRow = menuIndex,
                signedIn = signedIn,
                onViewProfile = {
                    profileMenuOpen = false
                    onProfile()
                },
                onLogOut = {
                    profileMenuOpen = false
                    onSidebarAction(if (signedIn) "Log out" else "Create account")
                },
                onDismiss = { profileMenuOpen = false },
            )
        }
    }
}

@Composable
private fun Sidebar(
    open: Boolean,
    width: Dp,
    m: Metrics,
    section: String,
    settingsOpen: Boolean,
    focus: List<FocusRequester>,
    railCursor: Int,
    onHome: () -> Unit,
    onFriends: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.fillMaxHeight(),
        enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { -it },
        exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { -it },
    ) {
        data class Item(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val active: Boolean, val action: () -> Unit)
        val targets = listOf(
            Item("Home", Icons.Outlined.Home, section != "Friends" && section != "Downloads" && !settingsOpen, onHome),
            Item("Friends", Icons.Outlined.Group, section == "Friends", onFriends),
            Item("Downloads", Icons.Outlined.FileDownload, section == "Downloads", onDownloads),
            Item("Settings", Icons.Outlined.Settings, settingsOpen, onSettings),
        )
        val power = Color(0xFFFF3B30)
        LaunchedEffect(railCursor) {
            if (railCursor !in focus.indices) return@LaunchedEffect
            focus[railCursor].bringIntoFocus()
        }
        Column(
            Modifier.width(width).fillMaxHeight()
                .background(KryoColors.Rail)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = m.d(16), vertical = m.d(28)),
        ) {
            targets.forEachIndexed { index, item ->
                val tint by androidx.compose.animation.animateColorAsState(
                    if (item.active) KryoColors.Accent else KryoColors.Muted,
                    tween(MotionIn, easing = MotionEase),
                    label = "railTint",
                )
                FocusBox(
                    Modifier.fillMaxWidth().height(m.d(52).coerceAtLeast(44.dp)).focusRequester(focus[index]).focusProperties {
                        up = focus.getOrElse(index - 1) { FocusRequester.Cancel }
                        down = focus.getOrElse(index + 1) { FocusRequester.Cancel }
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                    description = item.label,
                    trackFocus = false,
                    highlighted = index == railCursor,
                    onClick = item.action,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = m.d(12)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(m.d(12)),
                    ) {
                        Icon(item.icon, null, tint = tint, modifier = Modifier.size(m.d(22)))
                        Text(item.label, color = if (item.active) KryoColors.Text else KryoColors.Muted, fontSize = m.t(16), fontWeight = if (item.active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(m.d(10)))
            }
            Spacer(Modifier.weight(1f))
            Text(
                BuildConfig.VERSION_NAME,
                color = KryoColors.Muted,
                fontSize = m.t(12),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = m.d(12), bottom = m.d(8))
                    .semantics { contentDescription = "App version ${BuildConfig.VERSION_NAME}" },
            )
            FocusBox(
                Modifier.fillMaxWidth().height(m.d(52).coerceAtLeast(44.dp)).focusRequester(focus[4]).focusProperties {
                    up = focus[3]
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
                description = "Exit",
                outlined = true,
                trackFocus = false,
                highlighted = railCursor == 4,
                onClick = onExit,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = m.d(12)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(m.d(12)),
                ) {
                    Icon(Icons.Outlined.PowerSettingsNew, null, tint = power, modifier = Modifier.size(m.d(22)))
                    Text("Exit", color = power, fontSize = m.t(16), fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Header(
    m: Metrics,
    narrow: Boolean,
    section: String,
    query: String,
    username: String,
    avatar: String?,
    searchPhase: SearchPhase,
    searchFocus: FocusRequester,
    searchButtonFocus: FocusRequester,
    menuFocus: FocusRequester,
    sectionFocus: List<FocusRequester>,
    downTarget: FocusRequester,
    cursor: ShellSlot,
    searchArmed: Boolean,
    onSection: (String) -> Unit,
    onQuery: (String) -> Unit,
    onToggleMenu: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onProfilePlaced: (LayoutCoordinates) -> Unit,
    onToggleSearch: () -> Unit,
) {
    val row = Metrics(m.scale * 1.03f)
    val widget = row.d(48).coerceAtLeast(44.dp)
    val searchOpen = searchPhase != SearchPhase.Closed
    val barDown = if (searchOpen) searchFocus else downTarget
    val icons = listOf(
        Triple("Discover", Icons.Outlined.Explore, "section_discover"),
        Triple("Library", Icons.Outlined.VideoLibrary, "section_library"),
        Triple("Favorites", Icons.Outlined.Star, "section_favorites"),
    )
    val bellFocus = remember { FocusRequester() }
    val profileFocus = remember { FocusRequester() }
    LaunchedEffect(cursor, searchPhase) {
        val target = when (cursor) {
            ShellSlot.MENU -> menuFocus
            ShellSlot.DISCOVER -> sectionFocus[0]
            ShellSlot.LIBRARY -> sectionFocus[1]
            ShellSlot.FAVORITES -> sectionFocus[2]
            ShellSlot.SEARCH -> if (searchPhase == SearchPhase.Closed) searchButtonFocus else return@LaunchedEffect
            ShellSlot.BELL -> bellFocus
            ShellSlot.PROFILE -> profileFocus
            else -> return@LaunchedEffect
        }
        target.bringIntoFocus()
    }
    val menuButton: @Composable () -> Unit = {
        FocusBox(
            Modifier.size(widget).focusRequester(menuFocus).focusProperties {
                right = sectionFocus[0]
                down = barDown
            }.testTag("menu_button"),
            description = "Menu",
            outlined = true,
            trackFocus = false,
            highlighted = cursor == ShellSlot.MENU,
            onClick = onToggleMenu,
        ) {
            Icon(Icons.Outlined.Menu, null, tint = KryoColors.Text, modifier = Modifier.size(row.d(22)))
        }
    }
    val sectionSwitcher: @Composable () -> Unit = {
        Row(
            Modifier.testTag("section_switcher"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(row.d(14)),
        ) {
            HeaderDivider(row)
            icons.forEachIndexed { index, (label, icon, tag) ->
                HeaderIcon(
                    label = label,
                    icon = icon,
                    active = section == label,
                    highlighted = cursor == when (label) {
                        "Discover" -> ShellSlot.DISCOVER
                        "Library" -> ShellSlot.LIBRARY
                        else -> ShellSlot.FAVORITES
                    },
                    m = row,
                    modifier = Modifier.focusRequester(sectionFocus[index]).focusProperties {
                        left = if (index == 0) menuFocus else sectionFocus[index - 1]
                        right = if (index == icons.lastIndex) searchButtonFocus else sectionFocus[index + 1]
                        down = barDown
                    }.testTag(tag),
                    onClick = { onSection(label) },
                )
            }
            HeaderDivider(row)
        }
    }
    val bell: @Composable () -> Unit = {
        FocusBox(
            Modifier.size(widget).focusRequester(bellFocus).focusProperties {
                left = searchButtonFocus
                right = profileFocus
                down = barDown
            },
            "Notifications",
            outlined = true,
            trackFocus = false,
            highlighted = cursor == ShellSlot.BELL,
            onClick = onNotifications,
        ) {
            Icon(Icons.Outlined.Notifications, null, tint = KryoColors.Text, modifier = Modifier.size(row.d(22)))
        }
    }
    val profile: @Composable (Modifier) -> Unit = { mod ->
        FocusBox(
            mod.height(widget).onGloballyPositioned(onProfilePlaced).focusRequester(profileFocus).focusProperties {
                left = bellFocus
                down = barDown
            },
            "Profile: $username",
            outlined = true,
            trackFocus = false,
            highlighted = cursor == ShellSlot.PROFILE,
            onClick = onProfile,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = row.d(6)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(row.d(6)),
            ) {
                AccountAvatar(
                    avatar,
                    Modifier.size(row.d(36)).clip(RoundedCornerShape(row.d(5))),
                )
                Text(
                    username,
                    fontSize = row.t(14),
                    fontWeight = FontWeight.SemiBold,
                    color = KryoColors.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = KryoColors.Muted, modifier = Modifier.size(row.d(17)))
            }
        }
    }
    val searchButton: @Composable () -> Unit = {
        FocusBox(
            Modifier.size(widget).focusRequester(searchButtonFocus).focusProperties {
                left = sectionFocus.last()
                right = bellFocus
                down = barDown
            }.testTag("search_button"),
            "Search",
            outlined = searchPhase == SearchPhase.Closed,
            marked = searchPhase != SearchPhase.Closed,
            trackFocus = false,
            highlighted = cursor == ShellSlot.SEARCH && searchPhase == SearchPhase.Closed,
            onClick = onToggleSearch,
        ) {
            Icon(Icons.Outlined.Search, null, tint = KryoColors.Text, modifier = Modifier.size(row.d(22)))
        }
    }
    val tools: @Composable () -> Unit = {
        Row(
            Modifier.padding(end = row.d(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(row.d(8)),
        ) {
            searchButton()
            bell()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(m.d(10))) {
        if (narrow) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                menuButton()
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { sectionSwitcher() }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(row.d(8))) {
                tools()
                Spacer(Modifier.weight(1f))
                profile(Modifier.widthIn(min = row.d(96), max = row.d(140)))
            }
        } else {
            OpenRailHeader(
                minGap = row.d(16),
                preferredProfile = row.d(150),
                minimumProfile = row.d(96),
                menu = { menuButton() },
                sections = { sectionSwitcher() },
                tools = { tools() },
                profile = { profile(Modifier.fillMaxWidth()) },
            )
        }
        if (searchOpen) {
            SearchField(
                query,
                onQuery,
                m,
                Modifier.fillMaxWidth().focusRequester(searchFocus).focusProperties {
                    up = searchButtonFocus
                    down = downTarget
                },
                highlighted = searchArmed,
                placeholder = if (section == "Friends") "Search usernames..." else "Search games...",
            )
        }
    }
}

@Composable
private fun OpenRailHeader(
    minGap: Dp,
    preferredProfile: Dp,
    minimumProfile: Dp,
    menu: @Composable () -> Unit,
    sections: @Composable () -> Unit,
    tools: @Composable () -> Unit,
    profile: @Composable () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            menu()
            sections()
            tools()
            profile()
        },
    ) { measurables, constraints ->
        val loose = Constraints(0, Constraints.Infinity, 0, constraints.maxHeight)
        val menuPlaceable = measurables[0].measure(loose)
        val sectionsPlaceable = measurables[1].measure(loose)
        val toolsPlaceable = measurables[2].measure(loose)
        val gap = minGap.roundToPx()
        val room = constraints.maxWidth - menuPlaceable.width - gap - toolsPlaceable.width
        val preferred = preferredProfile.roundToPx()
        val minimum = minimumProfile.roundToPx()
        val profileWidth = when {
            room >= preferred -> preferred
            room >= minimum -> room
            else -> room.coerceAtLeast(0)
        }
        val profilePlaceable = measurables[3].measure(
            Constraints(profileWidth, profileWidth, 0, constraints.maxHeight),
        )
        val width = constraints.maxWidth
        val height = maxOf(menuPlaceable.height, sectionsPlaceable.height, toolsPlaceable.height, profilePlaceable.height)
        layout(width, height) {
            val trailWidth = toolsPlaceable.width + profilePlaceable.width
            val trailX = (width - trailWidth).coerceAtLeast(menuPlaceable.width + gap)
            val spanStart = menuPlaceable.width
            val spanEnd = trailX
            val sectionsX = (spanStart + (spanEnd - spanStart - sectionsPlaceable.width) / 2)
                .coerceIn(spanStart, (spanEnd - sectionsPlaceable.width).coerceAtLeast(spanStart))
            menuPlaceable.placeRelative(0, (height - menuPlaceable.height) / 2)
            sectionsPlaceable.placeRelative(sectionsX, (height - sectionsPlaceable.height) / 2)
            toolsPlaceable.placeRelative(trailX, (height - toolsPlaceable.height) / 2)
            profilePlaceable.placeRelative(trailX + toolsPlaceable.width, (height - profilePlaceable.height) / 2)
        }
    }
}

@Composable
private fun HeaderDivider(m: Metrics) {
    Box(
        Modifier
            .width(1.dp)
            .height(m.d(18))
            .background(KryoColors.Muted.copy(alpha = 0.4f)),
    )
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    m: Metrics,
    modifier: Modifier,
    highlighted: Boolean,
    placeholder: String,
) {
    var focused by remember { mutableStateOf(false) }
    val frosted = LocalFrosted.current
    val editable = LocalUiEnabled.current
    val ring = focused || highlighted
    val border by androidx.compose.animation.animateColorAsState(
        if (ring) KryoColors.Accent else if (frosted) Color.White.copy(alpha = 0.22f) else KryoColors.Border,
        tween(MotionIn, easing = MotionEase),
        label = "searchBorder",
    )
    BasicTextField(
        value = query,
        onValueChange = { if (editable) onQuery(it) },
        enabled = true,
        singleLine = true,
        cursorBrush = SolidColor(KryoColors.Accent),
        textStyle = TextStyle(color = KryoColors.Text, fontSize = m.t(14)),
        modifier = modifier.height(m.d(54).coerceAtLeast(44.dp)).onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(m.d(7))).background(if (frosted) Color.White.copy(alpha = 0.16f) else KryoColors.Surface)
            .border(if (ring) 2.dp else 1.dp, border, RoundedCornerShape(m.d(7)))
            .semantics { if (highlighted) selected = true }
            .testTag("search_field"),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = m.d(12)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(m.d(10)),
            ) {
                Icon(Icons.Outlined.Search, "Search", tint = KryoColors.Muted, modifier = Modifier.size(m.d(20)))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder, color = KryoColors.Muted, fontSize = m.t(14), maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun Title(section: String, m: Metrics) {
    Text(section, color = KryoColors.Text, fontSize = m.t(20), fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun HeaderIcon(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    highlighted: Boolean,
    m: Metrics,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tint by androidx.compose.animation.animateColorAsState(
        if (active) KryoColors.Accent else KryoColors.Muted,
        tween(MotionIn, easing = MotionEase),
        label = "headerIcon",
    )
    val underline by androidx.compose.animation.animateColorAsState(
        if (active) KryoColors.Accent else Color.Transparent,
        tween(MotionIn, easing = MotionEase),
        label = "headerLine",
    )
    val shown = if (active) {
        when (label) {
            "Discover" -> Icons.Filled.Explore
            "Library" -> Icons.Filled.VideoLibrary
            else -> Icons.Filled.Star
        }
    } else {
        icon
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusBox(
            Modifier.size(m.d(44).coerceAtLeast(40.dp)).then(modifier),
            label,
            trackFocus = false,
            highlighted = highlighted,
            onClick = onClick,
        ) {
            Icon(shown, null, tint = tint, modifier = Modifier.size(m.d(22)))
        }
        Box(Modifier.width(m.d(22)).height(2.dp).background(underline))
    }
}

@Composable
private fun GameCard(game: Game, m: Metrics, modifier: Modifier, pinned: Boolean, highlighted: Boolean, onFocused: () -> Unit, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) UiCue.move() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(m.d(7))
    val enabled = LocalUiEnabled.current
    val accent = pinned || highlighted
    val scale by animateFloatAsState(
        when {
            accent -> 1.035f
            hovered -> 1.02f
            else -> 1f
        },
        tween(180, easing = MotionEase),
        label = "cardScale",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        when {
            accent -> KryoColors.Accent
            hovered -> Color(0xFF8FA0B3)
            else -> KryoColors.CardEdge
        },
        tween(180, easing = MotionEase),
        label = "cardBorder",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .aspectRatio(0.94f)
            .onPreviewKeyEvent { event ->
                val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
                if (!activate) false
                else if (event.type == KeyEventType.KeyUp && enabled) {
                    UiCue.select()
                    onSelect()
                    true
                } else true
            }
            .bringIntoViewRequester(bringIntoView)
            .hoverable(interaction)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                    scope.launch { bringIntoView.bringIntoView() }
                }
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(KryoColors.CardTop, KryoColors.CardBottom)))
            .border(if (accent) 3.dp else 1.dp, borderColor, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = {
                if (enabled) {
                    UiCue.select()
                    onSelect()
                }
            })
            .semantics {
                contentDescription = "${game.title}, ${if (game.installed) "Installed" else if (game.platform == GamePlatform.WEB) "Web game" else "Android game"}${if (game.favorite) ", Favorite" else ""}"
                if (highlighted) selected = true
            }
            .testTag("game_${game.id}")
            .padding(if (accent) 3.dp else 1.dp),
    ) {
        GameArtwork(game, Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.fillMaxWidth().height(m.d(50)).padding(horizontal = m.d(11), vertical = m.d(6)), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.title,
                    color = KryoColors.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = m.t(16, 13),
                    lineHeight = m.t(20, 15),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (game.favorite) Icon(Icons.Outlined.Star, "Favorite", tint = KryoColors.Accent, modifier = Modifier.size(m.d(13)))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.d(6))) {
                if (game.installed) {
                    Box(Modifier.size(m.d(14)).background(KryoColors.Green, RoundedCornerShape(m.d(3))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(m.d(12)))
                    }
                } else {
                    Icon(
                        if (game.platform == GamePlatform.WEB) Icons.Outlined.Language else Icons.Outlined.Android,
                        null,
                        tint = KryoColors.Muted,
                        modifier = Modifier.size(m.d(15)),
                    )
                }
                Text(
                    if (game.installed) "Installed" else game.genre.ifBlank { if (game.platform == GamePlatform.WEB) "Web game" else "Android" },
                    color = if (game.installed) KryoColors.Green else KryoColors.Muted,
                    fontSize = m.t(11),
                    lineHeight = m.t(14, 13),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun FocusBox(
    modifier: Modifier = Modifier,
    description: String,
    outlined: Boolean = false,
    filled: Boolean = false,
    marked: Boolean = false,
    boxed: Boolean = true,
    trackFocus: Boolean = true,
    highlighted: Boolean = false,
    tone: Color = Color.Unspecified,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) UiCue.move() }
    val shape = RoundedCornerShape(7.dp)
    val enabled = LocalUiEnabled.current
    val frosted = LocalFrosted.current
    val armed = highlighted || (trackFocus && focused)
    val ring = boxed && armed
    val showFill = boxed && (filled || outlined || armed || marked)
    val border by androidx.compose.animation.animateColorAsState(
        when {
            ring -> KryoColors.Accent
            marked -> KryoColors.Accent
            frosted && showFill -> Color.White.copy(alpha = 0.22f)
            outlined -> KryoColors.Border
            else -> Color.Transparent
        },
        tween(160, easing = MotionEase),
        label = "focusBorder",
    )
    val fill by androidx.compose.animation.animateColorAsState(
        when {
            tone != Color.Unspecified -> tone
            marked -> KryoColors.Accent.copy(alpha = 0.22f)
            frosted && showFill -> Color.White.copy(alpha = 0.16f)
            showFill -> KryoColors.Surface
            else -> Color.Transparent
        },
        tween(160, easing = MotionEase),
        label = "focusFill",
    )
    Box(
        modifier.onPreviewKeyEvent { event ->
            val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
            if (!activate) false
            else if (event.type == KeyEventType.KeyUp && enabled) {
                UiCue.select()
                onClick()
                true
            } else true
        }.hoverable(interaction).onFocusChanged { focused = it.isFocused }.clip(shape)
            .background(fill)
            .border(if (!boxed) 0.dp else if (ring || marked) 2.dp else 1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = {
                if (enabled) {
                    UiCue.select()
                    onClick()
                }
            })
            .semantics {
                contentDescription = description
                if (highlighted) selected = true
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun GameChoiceLayer(
    visible: Boolean,
    game: Game?,
    selectedRow: Int,
    installing: Boolean,
    onPlay: (Game) -> Unit,
    onInfo: () -> Unit,
    onDelete: (Game) -> Unit,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(game) }
    if (game != null) shown = game
    val current = shown
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                    UiCue.back()
                }
            })
        }
        AnimatedVisibility(
            visible = visible && current != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(130)) + scaleIn(initialScale = 0.9f, animationSpec = tween(150, easing = MotionEase)),
            exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.96f, animationSpec = tween(110)),
        ) {
            val playFocus = remember { FocusRequester() }
            val infoFocus = remember { FocusRequester() }
            val deleteFocus = remember { FocusRequester() }
            val shownGame = current ?: return@AnimatedVisibility
            LaunchedEffect(visible, shownGame.id, selectedRow) {
                if (!visible) return@LaunchedEffect
                when (selectedRow) {
                    0 -> playFocus
                    1 -> infoFocus
                    else -> if (shownGame.installed) deleteFocus else infoFocus
                }.bringIntoFocus()
            }
            val menuWidth = 168.dp
            val gap = 22.dp
            val cardWidth = (maxWidth * 0.3f).coerceIn(156.dp, 214.dp)
            val stacked = maxWidth < cardWidth + menuWidth + gap + 36.dp
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                if (stacked) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                        LiftedGameCard(shownGame, Modifier.width(cardWidth))
                        ChoiceMenu(shownGame, selectedRow, installing, playFocus, infoFocus, deleteFocus, onPlay, onInfo, onDelete, Modifier.width(menuWidth))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                        LiftedGameCard(shownGame, Modifier.width(cardWidth))
                        ChoiceMenu(shownGame, selectedRow, installing, playFocus, infoFocus, deleteFocus, onPlay, onInfo, onDelete, Modifier.width(menuWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiftedGameCard(game: Game, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .aspectRatio(0.94f)
            .shadow(26.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.38f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(KryoColors.CardTop, KryoColors.CardBottom)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        GameArtwork(game, Modifier.fillMaxWidth().weight(1f))
        Text(
            game.title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            color = KryoColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChoiceMenu(
    game: Game,
    selectedRow: Int,
    installing: Boolean,
    playFocus: FocusRequester,
    infoFocus: FocusRequester,
    deleteFocus: FocusRequester,
    onPlay: (Game) -> Unit,
    onInfo: () -> Unit,
    onDelete: (Game) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .shadow(22.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.28f))
            .clip(shape)
            .background(KryoColors.Surface)
            .border(1.dp, KryoColors.Border, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceRow(
            label = when {
                installing -> "Installing"
                game.installed -> "Play"
                else -> "Install"
            },
            icon = if (game.installed) Icons.Outlined.PlayArrow else Icons.Outlined.FileDownload,
            tag = "play_action",
            highlighted = selectedRow == 0,
            focus = playFocus,
            up = FocusRequester.Cancel,
            down = infoFocus,
            onClick = { if (!installing) onPlay(game) },
        )
        ChoiceRow(
            label = "Info",
            icon = Icons.Outlined.Info,
            tag = "info_action",
            highlighted = selectedRow == 1,
            focus = infoFocus,
            up = playFocus,
            down = if (game.installed) deleteFocus else FocusRequester.Cancel,
            onClick = onInfo,
        )
        if (game.installed) ChoiceRow(
            label = "Delete",
            icon = Icons.Outlined.Delete,
            tag = "delete_action",
            highlighted = selectedRow == 2,
            focus = deleteFocus,
            up = infoFocus,
            down = FocusRequester.Cancel,
            onClick = { onDelete(game) },
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    highlighted: Boolean,
    focus: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    onClick: () -> Unit,
) {
    val tint = if (highlighted) KryoColors.Accent else KryoColors.Text
    FocusBox(
        Modifier.fillMaxWidth().height(48.dp).focusRequester(focus).focusProperties {
            this.up = up
            this.down = down
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
        }.testTag(tag),
        label,
        outlined = true,
        highlighted = highlighted,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(label, color = KryoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GameInfoView(
    game: Game,
    m: Metrics,
    classic: Boolean,
    selection: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val gap = if (classic) m.d(16) else m.d(22)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val topGap = maxHeight * 0.04f
            val bodyH = maxHeight - topGap
            val ratio = 0.94f
            val sideBySide = maxWidth >= 480.dp
            val maxArtW = min(maxWidth.value * 0.42f, (maxWidth - gap - m.d(220)).coerceAtLeast(m.d(150)).value)
            val maxArtH = if (sideBySide) bodyH.value else bodyH.value * 0.52f
            val artW = min(maxArtW, maxArtH * ratio).dp
            val artH = (artW.value / ratio).dp
            val body = Modifier.fillMaxSize().padding(top = topGap).clipToBounds()
            if (sideBySide) {
                Row(
                    body,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Top,
                ) {
                    GameInfoArt(game, Modifier.size(artW, artH))
                    GameInfoDetails(game, m, classic, selection, onPlay, onToggleFavorite, Modifier.weight(1f))
                }
            } else {
                Column(
                    body,
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalAlignment = Alignment.Start,
                ) {
                    GameInfoArt(game, Modifier.size(artW, artH))
                    GameInfoDetails(game, m, classic, selection, onPlay, onToggleFavorite, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun GameInfoArt(game: Game, modifier: Modifier) {
    Box(
        modifier
            .shadow(12.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0A0A)),
    ) {
        GameArtwork(game, Modifier.fillMaxSize())
    }
}

@Composable
private fun GameInfoDetails(
    game: Game,
    m: Metrics,
    classic: Boolean,
    selection: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(m.d(12))) {
        GameInfoCopy(game, m, classic, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(m.d(12)), verticalAlignment = Alignment.CenterVertically) {
            if (game.installed) {
                FocusBox(
                    Modifier.size(m.d(48)).testTag("info_play"),
                    "Play ${game.title}",
                    outlined = true,
                    trackFocus = false,
                    highlighted = selection == 0,
                    onClick = onPlay,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(m.d(22)))
                }
            } else {
                FocusBox(
                    Modifier.height(m.d(48)).testTag("info_play"),
                    "Install ${game.title}",
                    outlined = true,
                    trackFocus = false,
                    highlighted = selection == 0,
                    tone = Color(0xFF8ED4F8),
                    onClick = onPlay,
                ) {
                    Text(
                        "Install",
                        Modifier.padding(horizontal = m.d(18)),
                        color = Color(0xFF08324A),
                        fontSize = m.t(15),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            FocusBox(
                Modifier.size(m.d(48)).testTag("info_favorite"),
                if (game.favorite) "Remove from favorites" else "Add to favorites",
                outlined = true,
                trackFocus = false,
                highlighted = selection == 1,
                onClick = onToggleFavorite,
            ) {
                Icon(Icons.Outlined.Star, null, tint = if (game.favorite) Color(0xFFFF3B3B) else Color.White, modifier = Modifier.size(m.d(22)))
            }
        }
    }
}

@Composable
private fun GameInfoCopy(
    game: Game,
    m: Metrics,
    classic: Boolean,
    modifier: Modifier = Modifier,
) {
    val genre = game.genre.ifBlank { if (game.platform == GamePlatform.WEB) "Web" else "Android" }
    val tags = game.tags.ifEmpty { listOf(if (game.platform == GamePlatform.WEB) "Web" else "Android") }
    val spacing = if (classic) m.d(6) else m.d(8)
    val panel = RoundedCornerShape(m.d(16))
    Column(
        modifier
            .clip(panel)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), panel)
            .padding(if (classic) m.d(12) else m.d(16)),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(genre.uppercase(), color = Color.White.copy(alpha = 0.78f), fontSize = m.t(12), fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
        Text(
            game.title,
            color = Color.White,
            fontSize = if (classic) m.t(24, 18) else m.t(30, 20),
            fontWeight = FontWeight.Bold,
            lineHeight = if (classic) m.t(28, 22) else m.t(34, 24),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.description.ifBlank { "No description yet." },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = if (classic) m.t(13) else m.t(15),
            lineHeight = if (classic) m.t(18, 16) else m.t(22, 18),
            maxLines = if (classic) 3 else 5,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(m.d(8))) {
            tags.forEach { tag ->
                Text(
                    tag,
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                        .padding(horizontal = m.d(10), vertical = m.d(4)),
                    color = Color.White,
                    fontSize = m.t(12),
                )
            }
        }
    }
}

@Composable
private fun GameArtwork(game: Game, modifier: Modifier) {
    if (game.cover != 0) {
        Image(painterResource(game.cover), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.background(Brush.verticalGradient(listOf(Color(0xFF243038), Color(0xFF12171B)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.SportsEsports, null, tint = KryoColors.Muted, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun AddGameMenu(
    visible: Boolean,
    anchor: Rect?,
    highlighted: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    AnimatedVisibility(visible = visible && anchor != null, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures {
                onDismiss()
                UiCue.back()
            }
        })
    }
    val menuWidth = 168.dp
    val x = if (anchor == null) 0f else with(density) { (anchor.right - menuWidth.toPx()).coerceAtLeast(8.dp.toPx()) }
    val y = if (anchor == null) 0f else with(density) { anchor.bottom + 8.dp.toPx() }
    AnimatedVisibility(
        visible = visible && anchor != null,
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        enter = fadeIn(motionIn()) + expandVertically(motionIn(), expandFrom = Alignment.Top) + slideInVertically(motionIn()) { -it / 4 },
        exit = fadeOut(motionOut()) + shrinkVertically(motionOut(), shrinkTowards = Alignment.Top) + slideOutVertically(motionOut()) { -it / 5 },
    ) {
        val action = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            action.bringIntoFocus()
        }
        Column(
            Modifier.width(menuWidth)
                .shadow(18.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(KryoColors.Surface)
                .border(1.dp, KryoColors.Border, RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(8.dp),
        ) {
            FocusBox(
                Modifier.fillMaxWidth().height(40.dp).focusRequester(action).testTag("add_game_action"),
                "Add a game",
                outlined = true,
                highlighted = highlighted,
                onClick = onAdd,
            ) {
                Text("Add a game", color = KryoColors.Text, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProfileMenuLayer(
    visible: Boolean,
    anchor: Rect?,
    selectedRow: Int,
    signedIn: Boolean,
    onViewProfile: () -> Unit,
    onLogOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    AnimatedVisibility(visible = visible && anchor != null, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)).pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                    UiCue.back()
                }
            },
        )
    }
    val menuWidth = if (signedIn) 152.dp else 176.dp
    val x = if (anchor == null) 0f else with(density) { (anchor.right - menuWidth.toPx()).coerceAtLeast(8.dp.toPx()) }
    val y = if (anchor == null) 0f else with(density) { anchor.bottom + 8.dp.toPx() }
    AnimatedVisibility(
        visible = visible && anchor != null,
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        enter = fadeIn(motionIn()) + expandVertically(motionIn(), expandFrom = Alignment.Top) + slideInVertically(motionIn()) { -it / 4 },
        exit = fadeOut(motionOut()) + shrinkVertically(motionOut(), shrinkTowards = Alignment.Top) + slideOutVertically(motionOut()) { -it / 5 },
    ) {
        val viewProfile = remember { FocusRequester() }
        val logOut = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            viewProfile.bringIntoFocus()
        }
        Column(
            Modifier.width(menuWidth)
                .shadow(18.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(KryoColors.Surface)
                .border(1.dp, KryoColors.Border, RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            data class RowItem(val label: String, val focus: FocusRequester, val up: FocusRequester, val down: FocusRequester, val action: () -> Unit)
            listOf(
                RowItem(if (signedIn) "View profile" else "Sign in", viewProfile, viewProfile, logOut, onViewProfile),
                RowItem(if (signedIn) "Log out" else "Create account", logOut, viewProfile, logOut, onLogOut),
            ).forEachIndexed { index, item ->
                FocusBox(
                    Modifier.fillMaxWidth().height(40.dp).focusRequester(item.focus).focusProperties {
                        up = item.up
                        down = item.down
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                    item.label,
                    outlined = true,
                    highlighted = index == selectedRow,
                    onClick = item.action,
                ) {
                    Text(item.label, color = if (item.label == "Log out") KryoColors.Danger else KryoColors.Text, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    m: Metrics,
    aspect: DisplayAspect,
    appearance: KryoAppearance,
    selectedIndex: Int,
    onAspect: (DisplayAspect) -> Unit,
    onAppearance: (KryoAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    val wide = remember { FocusRequester() }
    val classic = remember { FocusRequester() }
    val theme = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    val light = appearance == KryoAppearance.LIGHT
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = m.d(8)),
        verticalArrangement = Arrangement.spacedBy(m.d(14)),
    ) {
        SettingsLabel("Display", m)
        SettingsCard(m) {
            Text("Aspect ratio", color = KryoColors.Text, fontSize = m.t(16), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(m.d(4)))
            Text(
                "4:3 fills handhelds like the Retroid Pocket Nova. 16:9 fills phones and televisions.",
                color = KryoColors.Muted,
                fontSize = m.t(13),
                lineHeight = m.t(18),
            )
            Spacer(Modifier.height(m.d(12)))
            Row(horizontalArrangement = Arrangement.spacedBy(m.d(10))) {
                AspectChoice(
                    "16:9",
                    aspect == DisplayAspect.WIDESCREEN,
                    selectedIndex == 0,
                    Modifier.weight(1f).focusRequester(wide).focusProperties {
                        right = classic
                        down = theme
                    }.testTag("aspect_16_9"),
                ) { onAspect(DisplayAspect.WIDESCREEN) }
                AspectChoice(
                    "4:3",
                    aspect == DisplayAspect.CLASSIC,
                    selectedIndex == 1,
                    Modifier.weight(1f).focusRequester(classic).focusProperties {
                        left = wide
                        down = theme
                    }.testTag("aspect_4_3"),
                ) { onAspect(DisplayAspect.CLASSIC) }
            }
        }
        FocusBox(
            Modifier.fillMaxWidth().focusRequester(theme).focusProperties {
                up = if (aspect == DisplayAspect.CLASSIC) classic else wide
                down = close
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }.testTag("appearance_toggle"),
            if (light) "Appearance, light mode" else "Appearance, dark mode",
            outlined = true,
            highlighted = selectedIndex == 2,
            onClick = { onAppearance(if (light) KryoAppearance.DARK else KryoAppearance.LIGHT) },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = m.d(14), vertical = m.d(12)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(m.d(12)),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Appearance", color = KryoColors.Text, fontSize = m.t(16), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(m.d(2)))
                    Text(
                        if (light) "Light" else "Dark",
                        color = if (selectedIndex == 2) KryoColors.Accent else KryoColors.Muted,
                        fontSize = m.t(13),
                    )
                }
                AppearanceSwitch(light = light, m = m)
            }
        }
        FocusBox(
            Modifier.fillMaxWidth().height(m.d(48).coerceAtLeast(44.dp)).focusRequester(close).focusProperties {
                up = theme
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            },
            "Close settings",
            outlined = true,
            highlighted = selectedIndex == 3,
            onClick = onDismiss,
        ) {
            Text("Back", color = KryoColors.Text, fontSize = m.t(15), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsLabel(text: String, m: Metrics) {
    Text(
        text.uppercase(),
        color = KryoColors.Muted,
        fontSize = m.t(12),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun SettingsCard(m: Metrics, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(m.d(12)))
            .background(KryoColors.Surface)
            .border(1.dp, KryoColors.Border, RoundedCornerShape(m.d(12)))
            .padding(m.d(16)),
        content = content,
    )
}

@Composable
private fun AppearanceSwitch(light: Boolean, m: Metrics) {
    val travel by animateDpAsState(if (light) m.d(22) else 0.dp, motionIn(), label = "appearanceThumb")
    val track by androidx.compose.animation.animateColorAsState(
        if (light) KryoColors.Accent else KryoColors.Border,
        tween(MotionIn, easing = MotionEase),
        label = "appearanceTrack",
    )
    Box(
        Modifier.width(m.d(52)).height(m.d(30))
            .background(track, RoundedCornerShape(m.d(15)))
            .padding(m.d(3)),
    ) {
        Box(Modifier.offset(x = travel).size(m.d(24)).background(Color.White, CircleShape))
    }
}

@Composable
private fun AspectChoice(label: String, selected: Boolean, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border by androidx.compose.animation.animateColorAsState(
        if (selected) KryoColors.Accent else KryoColors.Border,
        tween(MotionIn, easing = MotionEase),
        label = "aspectBorder",
    )
    val fill by androidx.compose.animation.animateColorAsState(
        if (selected) KryoColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
        tween(MotionIn, easing = MotionEase),
        label = "aspectFill",
    )
    FocusBox(modifier.height(48.dp), label, highlighted = highlighted, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(fill).border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) KryoColors.Text else KryoColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun AddGameSlot(m: Metrics, modifier: Modifier, highlighted: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val ring = highlighted
    val enabled = LocalUiEnabled.current
    val shape = RoundedCornerShape(m.d(7))
    Box(
        modifier
            .aspectRatio(0.94f)
            .onPreviewKeyEvent { event ->
                val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
                if (!activate) false
                else if (event.type == KeyEventType.KeyUp && enabled) {
                    UiCue.select()
                    onClick()
                    true
                } else true
            }
            .onFocusChanged {
                if (it.isFocused) onFocused()
            }
            .clip(shape)
            .background(KryoColors.CardBottom)
            .clickable(role = Role.Button, onClick = {
                if (enabled) {
                    UiCue.select()
                    onClick()
                }
            })
            .semantics {
                contentDescription = "Add a game"
                if (highlighted) selected = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = m.d(7).toPx()
            drawRoundRect(
                color = if (ring) KryoColors.Accent else KryoColors.Border,
                style = Stroke(
                    width = if (ring) 3.dp.toPx() else 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
                ),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
        Box(
            Modifier.size(m.d(48).coerceAtLeast(42.dp))
                .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(KryoColors.Surface, CircleShape)
                .border(1.dp, if (ring) KryoColors.Accent else KryoColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(26)))
        }
    }
}

@Preview(widthDp = 1100, heightDp = 688, showBackground = true)
@Composable
private fun LibraryPreview() {
    KryoTheme { LibraryScreen(DemoGames.all, onPlay = {}, onOptions = {}, onNotifications = {}, onProfile = {}, onSidebarAction = {}) }
}

@Preview(widthDp = 960, heightDp = 720, showBackground = true)
@Composable
private fun LibraryClassicPreview() {
    KryoTheme {
        LibraryScreen(
            DemoGames.all,
            aspect = DisplayAspect.CLASSIC,
            onPlay = {},
            onOptions = {},
            onNotifications = {},
            onProfile = {},
            onSidebarAction = {},
        )
    }
}
