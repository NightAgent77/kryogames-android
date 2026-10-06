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
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
private data class Metrics(val scale: Float) {
    fun d(value: Int): Dp = (value * scale).dp
    fun t(value: Int, minimum: Int = 11): TextUnit = (value * scale).coerceAtLeast(minimum.toFloat()).sp
}

private const val MotionIn = 220
private const val MotionOut = 160
private val MotionEase = FastOutSlowInEasing
private fun <T> motionIn() = tween<T>(MotionIn, easing = MotionEase)
private fun <T> motionOut() = tween<T>(MotionOut, easing = MotionEase)

private enum class SearchPhase { Closed, Highlighted, Editing }

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
    onToggleFavorite: (Game) -> Unit = {},
    onAddGame: () -> Unit = {},
) {
    var sidebarOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.ALL.name) }
    var section by rememberSaveable { mutableStateOf("Library") }
    var immersive by rememberSaveable { mutableStateOf(false) }
    var immersiveIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedGameId by rememberSaveable { mutableStateOf(games.firstOrNull()?.id) }
    var focusOnAdd by rememberSaveable { mutableStateOf(false) }
    var actionGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingInfo by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var profileMenuOpen by rememberSaveable { mutableStateOf(false) }
    var addMenuOpen by rememberSaveable { mutableStateOf(false) }
    var searchPhaseName by rememberSaveable { mutableStateOf(SearchPhase.Closed.name) }
    var railFocusNonce by remember { mutableIntStateOf(0) }
    var rootHasFocus by remember { mutableStateOf(false) }
    val railFocus = remember { List(4) { FocusRequester() } }
    val sectionFocus = remember { List(HeaderSections.size) { FocusRequester() } }
    val addFocus = remember { FocusRequester() }
    val viewFocus = remember { FocusRequester() }
    val searchButtonFocus = remember { FocusRequester() }
    val searchHighlightFocus = remember { FocusRequester() }
    var firstFocusPlaced by remember { mutableStateOf(false) }
    val searchPhase = SearchPhase.valueOf(searchPhaseName)
    val filter = LibraryFilter.valueOf(filterName)
    val visible = remember(games, filter, section, query) {
        val effective = if (section == "Favorites") LibraryFilter.FAVORITES else filter
        filterGames(games, effective, query)
    }
    val ids = visible.map { it.id }
    val cardFocus = remember(ids) { ids.associateWith { FocusRequester() } }
    val menuFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val immersiveFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val selectedGame = games.firstOrNull { it.id == lastFocusedGameId } ?: visible.firstOrNull()
    val actionGame = games.firstOrNull { it.id == actionGameId }
    val anchors = remember { mutableStateMapOf<String, Rect>() }
    var frameCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuSize by remember { mutableStateOf(IntSize(0, 0)) }
    val shellBlocked = settingsOpen || profileMenuOpen || addMenuOpen || actionGameId != null || showingInfo || modalOpen

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
        settingsOpen = false
        actionGameId = game.id
        showingInfo = false
        lastFocusedGameId = game.id
    }
    fun closeChoices() {
        actionGameId = null
        showingInfo = false
    }
    fun toggleSidebar() {
        sidebarOpen = !sidebarOpen
        if (sidebarOpen) railFocusNonce++
    }
    fun applySection(next: String) {
        section = next
        profileMenuOpen = false
        closeChoices()
        if (next == "Favorites") filterName = LibraryFilter.FAVORITES.name
        else if (next == "Library" || next == "Discover") filterName = LibraryFilter.ALL.name
    }
    fun closeSearch() {
        searchPhaseName = SearchPhase.Closed.name
        query = ""
        keyboard?.hide()
    }
    fun toggleSearch() {
        if (searchPhase == SearchPhase.Closed) searchPhaseName = SearchPhase.Highlighted.name
        else closeSearch()
    }
    fun activateSearch() {
        if (searchPhase == SearchPhase.Highlighted) searchPhaseName = SearchPhase.Editing.name
    }
    val restoreFocus = rememberUpdatedState {
        when {
            section == "Friends" -> menuFocus.requestSafe()
            searchPhase == SearchPhase.Editing -> searchFocus.requestSafe()
            searchPhase == SearchPhase.Highlighted -> searchHighlightFocus.requestSafe()
            immersive && visible.isNotEmpty() -> immersiveFocus.requestSafe()
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

    val handleAction by rememberUpdatedState<(ControllerAction) -> Unit> { action ->
        if (!modalOpen && actionGameId == null && !settingsOpen && !profileMenuOpen && !addMenuOpen) when (action) {
            ControllerAction.MENU -> toggleSidebar()
            ControllerAction.SEARCH -> toggleSearch()
            ControllerAction.OPTIONS -> {
                val game = if (immersive && section != "Friends") visible.getOrNull(immersiveIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))) else selectedGame
                if (game == null) return@rememberUpdatedState
                if (immersive && section != "Friends") {
                    lastFocusedGameId = game.id
                    actionGameId = game.id
                    showingInfo = true
                } else openGameChoices(game)
            }
            ControllerAction.PREVIOUS_TAB -> {
                val index = HeaderSections.indexOf(section)
                if (index > 0) applySection(HeaderSections[index - 1])
            }
            ControllerAction.NEXT_TAB -> {
                val index = HeaderSections.indexOf(section)
                if (index in 0 until HeaderSections.lastIndex) applySection(HeaderSections[index + 1])
            }
        }
    }
    LaunchedEffect(controllerActions) { controllerActions.collect { handleAction(it) } }
    LaunchedEffect(searchPhase) {
        when (searchPhase) {
            SearchPhase.Highlighted -> {
                keyboard?.hide()
                searchHighlightFocus.bringIntoFocus()
            }
            SearchPhase.Editing -> {
                searchFocus.bringIntoFocus()
                keyboard?.show()
            }
            SearchPhase.Closed -> keyboard?.hide()
        }
    }
    LaunchedEffect(ids, immersive) {
        if (!modalOpen && !shellBlocked && !firstFocusPlaced && section != "Friends") {
            if (!immersive && ids.isNotEmpty()) gridState.scrollToItem(0)
            if (immersive && ids.isNotEmpty()) immersiveFocus.bringIntoFocus()
            else if (ids.isNotEmpty()) cardFocus[ids.first()]?.bringIntoFocus()
            else addFocus.bringIntoFocus()
            firstFocusPlaced = true
        }
    }
    LaunchedEffect(immersive) {
        if (!firstFocusPlaced || shellBlocked || section == "Friends") return@LaunchedEffect
        if (immersive && ids.isNotEmpty()) {
            val selected = ids.indexOf(lastFocusedGameId)
            if (selected >= 0) immersiveIndex = selected
            immersiveFocus.bringIntoFocus()
        } else if (!immersive) {
            val index = ids.indexOf(lastFocusedGameId).takeIf { it >= 0 } ?: 0
            if (ids.isNotEmpty()) {
                gridState.scrollToItem(index)
                cardFocus[ids[index]]?.bringIntoFocus()
            } else addFocus.bringIntoFocus()
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
    BackHandler(enabled = !modalOpen && (settingsOpen || profileMenuOpen || addMenuOpen || actionGameId != null || section == "Friends" || searchPhase != SearchPhase.Closed || sidebarOpen)) {
        when {
            addMenuOpen -> addMenuOpen = false
            settingsOpen -> settingsOpen = false
            profileMenuOpen -> profileMenuOpen = false
            actionGameId != null && showingInfo -> showingInfo = false
            actionGameId != null -> closeChoices()
            searchPhase != SearchPhase.Closed -> closeSearch()
            section == "Friends" -> applySection("Library")
            else -> sidebarOpen = false
        }
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
        val immersiveBackdrop = immersive && section != "Friends" && !showingInfo
        val frameColor by androidx.compose.animation.animateColorAsState(
            if (immersiveBackdrop) Color(0xFF071426) else KryoColors.Background,
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
            val chromeEnabled = !modalOpen && actionGameId == null && !settingsOpen && !profileMenuOpen && !addMenuOpen
            val menuBlur by animateDpAsState(if (sidebarOpen) 18.dp else 0.dp, motionIn(), label = "menuBlur")
            val panelWidth = (animatedW * 0.30f).coerceAtLeast(m.d(220)).coerceAtMost(animatedW * 0.48f)
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    Modifier.fillMaxSize().then(if (menuBlur >= 0.5.dp) Modifier.blur(menuBlur) else Modifier),
                ) {
                        val narrow = maxWidth < 720.dp && resolved != DisplayAspect.CLASSIC
                        val availableGridWidth = maxWidth - m.d(38)
                        val columns = when {
                            availableGridWidth >= 720.dp -> 5
                            availableGridWidth >= 560.dp -> 4
                            availableGridWidth >= 420.dp -> 3
                            else -> 2
                        }
                        val libraryDown = when {
                            section == "Friends" -> menuFocus
                            immersive && visible.isNotEmpty() -> immersiveFocus
                            visible.isNotEmpty() -> cardFocus.getValue(visible.first().id)
                            else -> addFocus
                        }
                        val slotCount = visible.size + 1
                        fun slotTarget(index: Int): FocusRequester? = when {
                            index !in 0 until slotCount -> null
                            index == visible.size -> addFocus
                            else -> cardFocus[visible[index].id]
                        }
                        fun slotLink(index: Int, dx: Int, dy: Int): FocusRequester {
                            val next = gridNeighbor(index, slotCount, columns, dx, dy)
                            slotTarget(next ?: -1)?.let { return it }
                            return when {
                                dy < 0 -> viewFocus
                                dx < 0 && sidebarOpen -> railFocus[0]
                                dx < 0 -> menuFocus
                                else -> FocusRequester.Default
                            }
                        }
                        CompositionLocalProvider(LocalUiEnabled provides chromeEnabled, LocalFrosted provides showingInfo) {
                        Column(Modifier.fillMaxSize().padding(start = m.d(18), end = m.d(20), top = m.d(20))) {
                            Header(
                                m = m,
                                narrow = narrow,
                                section = section,
                                query = query,
                                username = username,
                                searchPhase = searchPhase,
                                immersive = immersive,
                                searchFocus = searchFocus,
                                searchHighlightFocus = searchHighlightFocus,
                                searchButtonFocus = searchButtonFocus,
                                menuFocus = menuFocus,
                                sectionFocus = sectionFocus,
                                viewFocus = viewFocus,
                                downTarget = libraryDown,
                                onSection = ::applySection,
                                onQuery = { query = it },
                                onToggleMenu = { toggleSidebar() },
                                onNotifications = onNotifications,
                                onProfile = { profileMenuOpen = !profileMenuOpen },
                                onProfilePlaced = { rememberAnchor("profile", it) },
                                onToggleSearch = { toggleSearch() },
                                onActivateSearch = { activateSearch() },
                                onToggleView = { immersive = !immersive },
                            )
                            if (section != "Friends" && !immersive && !showingInfo) {
                                Spacer(Modifier.height(m.d(16)))
                                Box(Modifier.padding(start = m.d(8))) {
                                    Crossfade(section, animationSpec = tween(MotionIn, easing = MotionEase), label = "title") { Title(it, m) }
                                }
                                Spacer(Modifier.height(m.d(12)))
                            } else {
                                Spacer(Modifier.height(m.d(8)))
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                OverlayVisibility(
                                    visible = section != "Friends" && !showingInfo && !immersive,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { -it / 16 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { -it / 16 },
                                ) {
                                    Column(Modifier.fillMaxSize()) {
                                        if (visible.isEmpty()) {
                                            Text(
                                                "No games match your search or filter.",
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
                                                    onFocused = {
                                                        lastFocusedGameId = game.id
                                                        focusOnAdd = false
                                                    },
                                                    onSelect = { openGameChoices(game) },
                                                )
                                            }
                                            item(key = "add-slot") {
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
                                                    onFocused = { focusOnAdd = true },
                                                    onClick = {
                                                        profileMenuOpen = false
                                                        settingsOpen = false
                                                        closeChoices()
                                                        addMenuOpen = !addMenuOpen
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                OverlayVisibility(
                                    visible = section != "Friends" && !showingInfo && immersive,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()),
                                    exit = fadeOut(motionOut()),
                                ) {
                                    val safeIndex = if (visible.isEmpty()) 0 else immersiveIndex.coerceIn(0, visible.lastIndex)
                                    ImmersiveLibrary(
                                        games = visible,
                                        index = safeIndex,
                                        filterLabel = if (section == "Favorites") "Favorites" else if (filter == LibraryFilter.ALL) "All games" else filter.label,
                                        m = m,
                                        carouselFocus = immersiveFocus,
                                        upFocus = viewFocus,
                                        onIndex = { next ->
                                            immersiveIndex = next
                                            visible.getOrNull(next)?.let {
                                                lastFocusedGameId = it.id
                                                focusOnAdd = false
                                            }
                                        },
                                        onCycleFilter = {
                                            if (section == "Favorites") return@ImmersiveLibrary
                                            val order = listOf(LibraryFilter.ALL, LibraryFilter.WEB, LibraryFilter.ANDROID, LibraryFilter.INSTALLED)
                                            val current = order.indexOf(filter).let { if (it < 0) 0 else it }
                                            filterName = order[(current + 1) % order.size].name
                                        },
                                        onPlay = { game ->
                                            lastFocusedGameId = game.id
                                            onPlay(game)
                                        },
                                        onDetails = { game ->
                                            lastFocusedGameId = game.id
                                            profileMenuOpen = false
                                            addMenuOpen = false
                                            settingsOpen = false
                                            actionGameId = game.id
                                            showingInfo = true
                                        },
                                    )
                                }
                                OverlayVisibility(
                                    visible = section == "Friends",
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { it / 16 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { it / 16 },
                                ) {
                                    FriendsPane(m)
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
                                                onPlay = {
                                                    closeChoices()
                                                    onPlay(game)
                                                },
                                                onBack = { showingInfo = false },
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
                            .pointerInput(Unit) { detectTapGestures { sidebarOpen = false } },
                    )
                }
                Sidebar(
                    open = sidebarOpen,
                    width = panelWidth,
                    m = m,
                    section = section,
                    settingsOpen = settingsOpen,
                    focus = railFocus,
                    focusRequest = railFocusNonce,
                    onLibrary = {
                        applySection("Library")
                        sidebarOpen = false
                    },
                    onFriends = {
                        section = "Friends"
                        profileMenuOpen = false
                        closeChoices()
                        sidebarOpen = false
                    },
                    onDownloads = {
                        sidebarOpen = false
                        onSidebarAction("Downloads")
                    },
                    onSettings = {
                        profileMenuOpen = false
                        settingsOpen = true
                        sidebarOpen = false
                    },
                )
            }

            val showChoices = actionGame != null && !showingInfo
            val choiceAnchor = actionGameId?.let { anchors[it] }
            GameChoiceLayer(
                visible = showChoices,
                game = actionGame,
                anchor = choiceAnchor,
                menuSize = menuSize,
                onMenuSize = { menuSize = it },
                onPlay = { game ->
                    closeChoices()
                    onPlay(game)
                },
                onInfo = { showingInfo = true },
                onDismiss = { closeChoices() },
            )
            AddGameMenu(
                visible = addMenuOpen && section != "Friends",
                anchor = anchors["add-game"],
                onAdd = {
                    addMenuOpen = false
                    onAddGame()
                },
                onDismiss = { addMenuOpen = false },
            )
            ProfileMenuLayer(
                visible = profileMenuOpen,
                anchor = anchors["profile"],
                onViewProfile = {
                    profileMenuOpen = false
                    onProfile()
                },
                onSettings = {
                    profileMenuOpen = false
                    settingsOpen = true
                },
                onLogOut = {
                    profileMenuOpen = false
                    onSidebarAction("Log out")
                },
                onDismiss = { profileMenuOpen = false },
            )
            SettingsLayer(
                visible = settingsOpen,
                aspect = resolved,
                onAspect = onAspect,
                onDismiss = { settingsOpen = false },
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
    focusRequest: Int,
    onLibrary: () -> Unit,
    onFriends: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
) {
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.fillMaxHeight(),
        enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { -it },
        exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { -it },
    ) {
        data class Item(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val active: Boolean, val action: () -> Unit)
        val targets = listOf(
            Item("Library", Icons.Outlined.SportsEsports, section != "Friends" && !settingsOpen, onLibrary),
            Item("Friends", Icons.Outlined.Group, section == "Friends", onFriends),
            Item("Downloads", Icons.Outlined.FileDownload, false, onDownloads),
            Item("Settings", Icons.Outlined.Settings, settingsOpen, onSettings),
        )
        LaunchedEffect(focusRequest) {
            if (focusRequest == 0) return@LaunchedEffect
            val index = targets.indexOfFirst { it.active }.coerceAtLeast(0)
            focus.getOrNull(index)?.bringIntoFocus()
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
                    filled = item.active,
                    marked = item.active,
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
    searchPhase: SearchPhase,
    immersive: Boolean,
    searchFocus: FocusRequester,
    searchHighlightFocus: FocusRequester,
    searchButtonFocus: FocusRequester,
    menuFocus: FocusRequester,
    sectionFocus: List<FocusRequester>,
    viewFocus: FocusRequester,
    downTarget: FocusRequester,
    onSection: (String) -> Unit,
    onQuery: (String) -> Unit,
    onToggleMenu: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onProfilePlaced: (LayoutCoordinates) -> Unit,
    onToggleSearch: () -> Unit,
    onActivateSearch: () -> Unit,
    onToggleView: () -> Unit,
) {
    val widget = m.d(48).coerceAtLeast(44.dp)
    val searchOpen = searchPhase != SearchPhase.Closed
    val barDown = if (searchPhase == SearchPhase.Highlighted) searchHighlightFocus else if (searchPhase == SearchPhase.Editing) searchFocus else downTarget
    val icons = listOf(
        Triple("Discover", Icons.Outlined.Explore, "section_discover"),
        Triple("Library", Icons.Outlined.VideoLibrary, "section_library"),
        Triple("Favorites", Icons.Outlined.Star, "section_favorites"),
    )
    val bellFocus = remember { FocusRequester() }
    val profileFocus = remember { FocusRequester() }
    val menuButton: @Composable () -> Unit = {
        FocusBox(
            Modifier.size(widget).focusRequester(menuFocus).focusProperties {
                right = sectionFocus[0]
                down = barDown
            }.testTag("menu_button"),
            description = "Menu",
            outlined = true,
            onClick = onToggleMenu,
        ) {
            Icon(Icons.Outlined.Menu, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(22)))
        }
    }
    val tabGroup: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.d(8))) {
            menuButton()
            KeyBadge("LB", m)
            icons.forEachIndexed { index, (label, icon, tag) ->
                HeaderIcon(
                    label = label,
                    icon = icon,
                    active = section == label,
                    m = m,
                    modifier = Modifier.focusRequester(sectionFocus[index]).focusProperties {
                        left = if (index == 0) menuFocus else sectionFocus[index - 1]
                        right = if (index == icons.lastIndex) viewFocus else sectionFocus[index + 1]
                        down = barDown
                    }.testTag(tag),
                    onClick = { onSection(label) },
                )
            }
            KeyBadge("RB", m)
        }
    }
    val viewButton: @Composable () -> Unit = {
        FocusBox(
            Modifier.size(widget).focusRequester(viewFocus).focusProperties {
                left = sectionFocus.last()
                right = searchButtonFocus
                down = barDown
            }.testTag("view_mode"),
            description = if (immersive) "Switch to grid view" else "Switch to immersive view",
            outlined = true,
            onClick = onToggleView,
        ) {
            Icon(
                if (immersive) Icons.Outlined.ViewCarousel else Icons.Outlined.GridView,
                null,
                tint = KryoColors.Text,
                modifier = Modifier.size(m.d(22)),
            )
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
            onClick = onNotifications,
        ) {
            Icon(Icons.Outlined.Notifications, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(22)))
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
            onClick = onProfile,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = m.d(6)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(m.d(6)),
            ) {
                Image(
                    painterResource(R.drawable.profile_avatar),
                    null,
                    Modifier.size(m.d(36)).clip(RoundedCornerShape(m.d(5))),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    username,
                    fontSize = m.t(14),
                    fontWeight = FontWeight.SemiBold,
                    color = KryoColors.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = KryoColors.Muted, modifier = Modifier.size(m.d(17)))
            }
        }
    }
    val searchButton: @Composable () -> Unit = {
        FocusBox(
            Modifier.size(widget).focusRequester(searchButtonFocus).focusProperties {
                left = viewFocus
                right = bellFocus
                down = barDown
            }.testTag("search_button"),
            "Search",
            outlined = searchPhase == SearchPhase.Closed,
            marked = searchPhase != SearchPhase.Closed,
            onClick = onToggleSearch,
        ) {
            Icon(Icons.Outlined.Search, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(22)))
        }
    }
    val tools: @Composable () -> Unit = {
        Row(
            Modifier.padding(end = m.d(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.d(8)),
        ) {
            viewButton()
            searchButton()
            bell()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(m.d(10))) {
        if (narrow) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                tabGroup()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.d(8))) {
                tools()
                Spacer(Modifier.weight(1f))
                profile(Modifier.widthIn(min = m.d(96), max = m.d(140)))
            }
        } else {
            OpenRailHeader(
                minGap = m.d(16),
                preferredProfile = m.d(150),
                minimumProfile = m.d(96),
                tabs = { tabGroup() },
                tools = { tools() },
                profile = { profile(Modifier.fillMaxWidth()) },
            )
        }
        if (searchPhase == SearchPhase.Highlighted) {
            SearchHighlight(m, Modifier.fillMaxWidth().focusRequester(searchHighlightFocus).focusProperties {
                up = searchButtonFocus
                down = downTarget
            }, onActivateSearch)
        } else if (searchOpen) {
            SearchField(query, onQuery, m, Modifier.fillMaxWidth().focusRequester(searchFocus).focusProperties {
                up = searchButtonFocus
                down = downTarget
            })
        }
    }
}

@Composable
private fun OpenRailHeader(
    minGap: Dp,
    preferredProfile: Dp,
    minimumProfile: Dp,
    tabs: @Composable () -> Unit,
    tools: @Composable () -> Unit,
    profile: @Composable () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            tabs()
            tools()
            profile()
        },
    ) { measurables, constraints ->
        val loose = Constraints(0, Constraints.Infinity, 0, constraints.maxHeight)
        val lead = measurables[0].measure(loose)
        val toolsPlaceable = measurables[1].measure(loose)
        val room = constraints.maxWidth - lead.width - minGap.roundToPx() - toolsPlaceable.width
        val profileWidth = room.coerceIn(minimumProfile.roundToPx(), preferredProfile.roundToPx())
        val profilePlaceable = measurables[2].measure(
            Constraints(profileWidth, profileWidth, 0, constraints.maxHeight),
        )
        val width = constraints.maxWidth
        val height = maxOf(lead.height, toolsPlaceable.height, profilePlaceable.height)
        layout(width, height) {
            val trailWidth = toolsPlaceable.width + profilePlaceable.width
            val gapped = lead.width + minGap.roundToPx()
            val trailX = if (gapped + trailWidth <= width) width - trailWidth else gapped
            lead.placeRelative(0, (height - lead.height) / 2)
            toolsPlaceable.placeRelative(trailX, (height - toolsPlaceable.height) / 2)
            profilePlaceable.placeRelative(trailX + toolsPlaceable.width, (height - profilePlaceable.height) / 2)
        }
    }
}

@Composable
private fun SearchHighlight(m: Metrics, modifier: Modifier, onActivate: () -> Unit) {
    FocusBox(
        modifier.height(m.d(54).coerceAtLeast(44.dp)).testTag("search_bar"),
        "Search games",
        outlined = true,
        marked = true,
        onClick = onActivate,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = m.d(12)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.d(10)),
        ) {
            Icon(Icons.Outlined.Search, null, tint = KryoColors.Accent, modifier = Modifier.size(m.d(20)))
            Text("Search games...", color = KryoColors.Muted, fontSize = m.t(14), maxLines = 1)
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    m: Metrics,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val frosted = LocalFrosted.current
    val editable = LocalUiEnabled.current
    val border by androidx.compose.animation.animateColorAsState(
        if (focused) Color.White else if (frosted) Color.White.copy(alpha = 0.22f) else KryoColors.Border,
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
            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(m.d(7)))
            .testTag("search_field"),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = m.d(12)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(m.d(10)),
            ) {
                Icon(Icons.Outlined.Search, "Search", tint = KryoColors.Muted, modifier = Modifier.size(m.d(20)))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search games...", color = KryoColors.Muted, fontSize = m.t(14), maxLines = 1)
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusBox(Modifier.size(m.d(44).coerceAtLeast(40.dp)).then(modifier), label, onClick = onClick) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(m.d(22)))
        }
        Box(Modifier.width(m.d(22)).height(2.dp).background(underline))
    }
}

@Composable
private fun GameCard(game: Game, m: Metrics, modifier: Modifier, pinned: Boolean, onFocused: () -> Unit, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(m.d(7))
    val enabled = LocalUiEnabled.current
    val scale by animateFloatAsState(
        when {
            focused -> 1.035f
            hovered -> 1.02f
            pinned -> 1.02f
            else -> 1f
        },
        tween(180, easing = MotionEase),
        label = "cardScale",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        when {
            focused || pinned -> Color.White
            hovered -> Color(0xFF8FA0B3)
            else -> Color(0xFF1F272D)
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
                    onSelect()
                    true
                } else true
            }
            .focusable()
            .bringIntoViewRequester(bringIntoView)
            .hoverable(interaction)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                    scope.launch { bringIntoView.bringIntoView() }
                }
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2025), Color(0xFF14191D))))
            .border(if (focused || pinned) 3.dp else 1.dp, borderColor, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = { if (enabled) onSelect() })
            .semantics {
                contentDescription = "${game.title}, ${if (game.installed) "Installed" else if (game.platform == GamePlatform.WEB) "Web game" else "Android game"}${if (game.favorite) ", Favorite" else ""}"
            }
            .testTag("game_${game.id}")
            .padding(if (focused || pinned) 3.dp else 1.dp),
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
                    if (game.installed) "Installed" else if (game.platform == GamePlatform.WEB) "Web game" else "Android",
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
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    val enabled = LocalUiEnabled.current
    val frosted = LocalFrosted.current
    val showFill = filled || outlined || focused || marked
    val border by androidx.compose.animation.animateColorAsState(
        when {
            focused -> Color.White
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
                onClick()
                true
            } else true
        }.focusable().onFocusChanged { focused = it.isFocused }.clip(shape)
            .background(fill)
            .border(if (focused || marked) 2.dp else 1.dp, border, shape)
            .clickable(role = Role.Button, onClick = { if (enabled) onClick() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun KeyBadge(label: String, m: Metrics) {
    Text(
        label,
        Modifier.border(1.dp, KryoColors.Border, RoundedCornerShape(4.dp)).padding(horizontal = m.d(8), vertical = m.d(3)),
        color = KryoColors.Muted,
        fontSize = m.t(10, 10),
        lineHeight = m.t(12, 12),
    )
}

@Composable
private fun GameChoiceLayer(
    visible: Boolean,
    game: Game?,
    anchor: Rect?,
    menuSize: IntSize,
    onMenuSize: (IntSize) -> Unit,
    onPlay: (Game) -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(game) }
    if (game != null) shown = game
    val current = shown
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val menuWidth = 152.dp
        val estimatedHeight = if (menuSize.height > 0) with(density) { menuSize.height.toDp() } else 118.dp
        val place = with(density) {
            val widthPx = menuWidth.toPx()
            val heightPx = estimatedHeight.toPx()
            if (anchor == null) {
                PopupPlace((maxWidth.toPx() - widthPx) / 2f, (maxHeight.toPx() - heightPx) / 2f, false)
            } else {
                placeGameMenu(
                    anchor.left, anchor.top, anchor.right, anchor.bottom,
                    maxWidth.toPx(), maxHeight.toPx(), widthPx, heightPx,
                )
            }
        }
        val origin = if (place.placeOnLeft) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
        AnimatedVisibility(visible = visible, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)).pointerInput(Unit) { detectTapGestures { onDismiss() } })
        }
        AnimatedVisibility(
            visible = visible && current != null,
            modifier = Modifier.offset { IntOffset(place.x.roundToInt(), place.y.roundToInt()) },
            enter = fadeIn(motionIn()) + scaleIn(initialScale = 0.92f, animationSpec = motionIn(), transformOrigin = origin) +
                slideInHorizontally(motionIn()) { if (place.placeOnLeft) it / 5 else -it / 5 },
            exit = fadeOut(motionOut()) + scaleOut(targetScale = 0.96f, animationSpec = motionOut(), transformOrigin = origin),
        ) {
            val playFocus = remember { FocusRequester() }
            val infoFocus = remember { FocusRequester() }
            val shownGame = current ?: return@AnimatedVisibility
            LaunchedEffect(visible, shownGame.id) {
                if (visible) playFocus.bringIntoFocus()
            }
            Column(
                Modifier.width(menuWidth)
                    .onSizeChanged(onMenuSize)
                    .shadow(16.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(KryoColors.Surface)
                    .border(1.dp, KryoColors.Border, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(shownGame.title, color = KryoColors.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FocusBox(
                    Modifier.fillMaxWidth().height(34.dp).focusRequester(playFocus).focusProperties {
                        down = infoFocus
                        up = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }.testTag("play_action"),
                    "Play",
                    outlined = true,
                    onClick = { onPlay(shownGame) },
                ) {
                    Text("Play", color = KryoColors.Text, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                FocusBox(
                    Modifier.fillMaxWidth().height(34.dp).focusRequester(infoFocus).focusProperties {
                        up = playFocus
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }.testTag("info_action"),
                    "Info",
                    outlined = true,
                    onClick = onInfo,
                ) {
                    Text("Info", color = KryoColors.Text, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GameInfoView(
    game: Game,
    m: Metrics,
    classic: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(game.id) {
        playFocus.bringIntoFocus()
    }
    val gap = if (classic) m.d(12) else m.d(20)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().padding(horizontal = m.d(8), vertical = m.d(6))) {
            FocusBox(Modifier.size(m.d(40)), "Back", outlined = true, onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(18)))
            }
            Spacer(Modifier.height(m.d(8)))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (classic) {
                    val ratio = 735f / 575f
                    val sideBySide = maxWidth >= 440.dp
                    if (sideBySide) {
                        val roomForArt = (maxWidth - gap - 200.dp).coerceAtLeast(120.dp)
                        val maxArtW = min(maxWidth.value * 0.36f, roomForArt.value)
                        val maxArtH = maxHeight.value * 0.88f
                        val artW = min(maxArtW, maxArtH * ratio).dp
                        val artH = (artW.value / ratio).dp
                        Row(
                            Modifier.fillMaxSize().clipToBounds(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GameInfoArt(game, Modifier.size(artW, artH))
                            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.CenterStart) {
                                GameInfoCopy(game, m, classic, playFocus, onPlay, onToggleFavorite, Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        val maxArtH = maxHeight.value * 0.36f
                        val artW = min(maxWidth.value * 0.72f, maxArtH * ratio).dp
                        val artH = (artW.value / ratio).dp
                        Column(
                            Modifier.fillMaxSize().clipToBounds(),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GameInfoArt(game, Modifier.size(artW, artH))
                            GameInfoCopy(game, m, classic, playFocus, onPlay, onToggleFavorite, Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    val stack = maxWidth < 520.dp
                    if (stack) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            GameInfoArt(game, Modifier.fillMaxWidth().weight(1f))
                            GameInfoCopy(game, m, classic, playFocus, onPlay, onToggleFavorite, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GameInfoArt(
                                game,
                                Modifier.fillMaxHeight().aspectRatio(735f / 575f, matchHeightConstraintsFirst = true),
                            )
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                GameInfoCopy(game, m, classic, playFocus, onPlay, onToggleFavorite, Modifier.fillMaxWidth())
                            }
                        }
                    }
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
private fun GameInfoCopy(
    game: Game,
    m: Metrics,
    classic: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(m.d(10)), verticalAlignment = Alignment.CenterVertically) {
            var playFocused by remember { mutableStateOf(false) }
            Box(
                Modifier.height(m.d(44)).widthIn(min = m.d(120)).focusRequester(playFocus)
                    .onFocusChanged { playFocused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(if (playFocused) 2.dp else 0.dp, KryoColors.Accent, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onPlay)
                    .semantics { contentDescription = "Play ${game.title}" }
                    .padding(horizontal = m.d(22)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Play", color = Color(0xFF12141A), fontSize = m.t(15), fontWeight = FontWeight.Bold)
            }
            FocusBox(
                Modifier.size(m.d(44)),
                if (game.favorite) "Remove from favorites" else "Add to favorites",
                outlined = true,
                onClick = onToggleFavorite,
            ) {
                Icon(Icons.Outlined.Star, null, tint = if (game.favorite) Color(0xFFFF3B3B) else Color.White, modifier = Modifier.size(m.d(20)))
            }
        }
    }
}

@Composable
private fun FriendsPane(m: Metrics) {
    Column(Modifier.fillMaxSize().padding(top = m.d(8))) {
        Text("Friends", color = KryoColors.Text, fontSize = m.t(32), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(m.d(4)))
        Text("See who's around and invite them to play.", color = KryoColors.Muted, fontSize = m.t(14))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(m.d(10))) {
                Icon(Icons.Outlined.Group, null, tint = KryoColors.Muted, modifier = Modifier.size(m.d(36)))
                Text("No friends yet.", color = KryoColors.Muted, fontSize = m.t(15))
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
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    AnimatedVisibility(visible = visible && anchor != null, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
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
    onViewProfile: () -> Unit,
    onSettings: () -> Unit,
    onLogOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    AnimatedVisibility(visible = visible && anchor != null, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
    }
    val menuWidth = 196.dp
    val x = if (anchor == null) 0f else with(density) { (anchor.right - menuWidth.toPx()).coerceAtLeast(8.dp.toPx()) }
    val y = if (anchor == null) 0f else with(density) { anchor.bottom + 8.dp.toPx() }
    AnimatedVisibility(
        visible = visible && anchor != null,
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        enter = fadeIn(motionIn()) + expandVertically(motionIn(), expandFrom = Alignment.Top) + slideInVertically(motionIn()) { -it / 4 },
        exit = fadeOut(motionOut()) + shrinkVertically(motionOut(), shrinkTowards = Alignment.Top) + slideOutVertically(motionOut()) { -it / 5 },
    ) {
        val first = remember { FocusRequester() }
        val second = remember { FocusRequester() }
        val third = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            first.bringIntoFocus()
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
                RowItem("View profile", first, first, second, onViewProfile),
                RowItem("Settings", second, first, third, onSettings),
                RowItem("Log out", third, second, third, onLogOut),
            ).forEach { item ->
                FocusBox(
                    Modifier.fillMaxWidth().height(40.dp).focusRequester(item.focus).focusProperties {
                        up = item.up
                        down = item.down
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                    item.label,
                    outlined = true,
                    onClick = item.action,
                ) {
                    Text(item.label, color = if (item.label == "Log out") Color(0xFFF0A0A0) else KryoColors.Text, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsLayer(visible: Boolean, aspect: DisplayAspect, onAspect: (DisplayAspect) -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = visible, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.46f)).pointerInput(Unit) { detectTapGestures { onDismiss() } })
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(motionIn()) + scaleIn(initialScale = 0.96f, animationSpec = motionIn()) + slideInVertically(motionIn()) { it / 10 },
            exit = fadeOut(motionOut()) + scaleOut(targetScale = 0.98f, animationSpec = motionOut()) + slideOutVertically(motionOut()) { it / 12 },
        ) {
            val wide = remember { FocusRequester() }
            val classic = remember { FocusRequester() }
            val close = remember { FocusRequester() }
            LaunchedEffect(aspect) {
                (if (aspect == DisplayAspect.CLASSIC) classic else wide).bringIntoFocus()
            }
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth(0.86f)
                    .heightIn(max = maxHeight * 0.9f)
                    .shadow(20.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(KryoColors.Surface)
                    .border(1.dp, KryoColors.Border, RoundedCornerShape(14.dp))
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(22.dp),
            ) {
                Text("Settings", color = KryoColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Display and this build.", color = KryoColors.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Text("ASPECT RATIO", color = KryoColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AspectChoice("16:9", aspect == DisplayAspect.WIDESCREEN, Modifier.weight(1f).focusRequester(wide).focusProperties {
                        right = classic
                        down = close
                    }.testTag("aspect_16_9")) { onAspect(DisplayAspect.WIDESCREEN) }
                    AspectChoice("4:3", aspect == DisplayAspect.CLASSIC, Modifier.weight(1f).focusRequester(classic).focusProperties {
                        left = wide
                        down = close
                    }.testTag("aspect_4_3")) { onAspect(DisplayAspect.CLASSIC) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "4:3 fills the Retroid Pocket Nova. 16:9 fills phones and televisions.",
                    color = KryoColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(18.dp))
                Text("APP DETAILS", color = KryoColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(KryoColors.Background)
                        .border(1.dp, KryoColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("app_details"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("KryoGames", color = KryoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text("Version", color = KryoColors.Muted, fontSize = 12.sp)
                    }
                    Text(
                        BuildConfig.VERSION_NAME,
                        color = KryoColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { contentDescription = "App version ${BuildConfig.VERSION_NAME}" },
                    )
                }
                Spacer(Modifier.height(18.dp))
                FocusBox(Modifier.fillMaxWidth().height(44.dp).focusRequester(close).focusProperties {
                    up = if (aspect == DisplayAspect.CLASSIC) classic else wide
                }, "Close settings", outlined = true, onClick = onDismiss) {
                    Text("Close", color = KryoColors.Text, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun AspectChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
    FocusBox(modifier.height(48.dp), label, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(fill).border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) KryoColors.Text else KryoColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun AddGameSlot(m: Metrics, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val enabled = LocalUiEnabled.current
    val shape = RoundedCornerShape(m.d(7))
    Box(
        modifier
            .aspectRatio(0.94f)
            .onPreviewKeyEvent { event ->
                val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
                if (!activate) false
                else if (event.type == KeyEventType.KeyUp && enabled) {
                    onClick()
                    true
                } else true
            }
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(shape)
            .background(Color(0xFF14191D))
            .clickable(role = Role.Button, onClick = { if (enabled) onClick() })
            .semantics { contentDescription = "Add a game" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = m.d(7).toPx()
            drawRoundRect(
                color = if (focused) Color.White else KryoColors.Border,
                style = Stroke(
                    width = if (focused) 3.dp.toPx() else 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
                ),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
        Box(
            Modifier.size(m.d(48).coerceAtLeast(42.dp))
                .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(KryoColors.Surface, CircleShape)
                .border(1.dp, if (focused) KryoColors.Accent else KryoColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(26)))
        }
    }
}

@Composable
private fun ImmersiveLibrary(
    games: List<Game>,
    index: Int,
    filterLabel: String,
    m: Metrics,
    carouselFocus: FocusRequester,
    upFocus: FocusRequester,
    onIndex: (Int) -> Unit,
    onCycleFilter: () -> Unit,
    onPlay: (Game) -> Unit,
    onDetails: (Game) -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val game = games.getOrNull(index)
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().focusRequester(carouselFocus).onPreviewKeyEvent { event ->
                if (games.isEmpty()) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown && index > 0) onIndex(index - 1)
                        true
                    }
                    Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown && index < games.lastIndex) onIndex(index + 1)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (event.type == KeyEventType.KeyUp) game?.let(onPlay)
                        true
                    }
                    else -> false
                }
            }.focusable().focusProperties {
                up = upFocus
                down = if (game == null) filterFocus else playFocus
            }.clickable(role = Role.Button, onClick = { game?.let(onPlay) })
                .semantics { contentDescription = game?.let { "Selected ${it.title}" } ?: "No games" },
            contentAlignment = Alignment.Center,
        ) {
            if (games.isEmpty() || game == null) {
                Text("No games match your search or filter.", color = KryoColors.Muted, fontSize = m.t(15))
            } else {
                val shift by animateFloatAsState(index.toFloat(), tween(280, easing = MotionEase), label = "carouselShift")
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val cardWidth = (maxWidth * 0.24f).coerceIn(m.d(128), m.d(240))
                    val cardHeight = cardWidth / 0.68f
                    Box(
                        Modifier.width(cardWidth * 1.08f).height(cardHeight * 0.92f)
                            .blur(26.dp)
                            .background(KryoColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(m.d(18))),
                    )
                    val ordered = games.indices.sortedByDescending { abs(it - shift) }
                    ordered.forEach { position ->
                        val delta = position - shift
                        if (abs(delta) > 3.2f) return@forEach
                        val depth = abs(delta).coerceAtMost(3f)
                        val scale = 1f - depth * 0.13f
                        val poster = games[position]
                        val selected = abs(delta) < 0.45f
                        Box(
                            Modifier.width(cardWidth).height(cardHeight).graphicsLayer {
                                translationX = delta * cardWidth.toPx() * 0.72f
                                scaleX = scale
                                scaleY = scale
                                rotationY = (-delta * 20f).coerceIn(-46f, 46f)
                                alpha = (1f - depth * 0.16f).coerceIn(0.4f, 1f)
                                cameraDistance = 14f * density
                            },
                        ) {
                            val shape = RoundedCornerShape(m.d(12))
                            Box(
                                Modifier.fillMaxSize()
                                    .shadow(if (selected) 18.dp else 6.dp, shape)
                                    .clip(shape)
                                    .background(Color(0xFF101820))
                                    .border(if (selected) 3.dp else 1.dp, if (selected) KryoColors.Accent else Color(0xFF243041), shape),
                            ) {
                                GameArtwork(poster, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        if (games.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = m.d(10)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                games.forEachIndexed { dot, _ ->
                    val active = dot == index
                    Box(
                        Modifier.padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 6.dp)
                            .background(if (active) KryoColors.Accent else Color.White.copy(alpha = 0.35f), CircleShape),
                    )
                }
            }
            if (game != null) {
                val platform = if (game.platform == GamePlatform.WEB) "Web" else "Android"
                val status = if (game.installed) "Installed" else "Not installed"
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.widthIn(max = 760.dp).fillMaxWidth(0.86f)
                            .heightIn(min = m.d(78))
                            .clip(RoundedCornerShape(m.d(28)))
                            .background(Color(0xFF101820).copy(alpha = 0.92f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(m.d(28)))
                            .padding(horizontal = m.d(18), vertical = m.d(12)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(m.d(12)),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(game.title, color = KryoColors.Text, fontSize = m.t(22, 16), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$platform · $status", color = KryoColors.Muted, fontSize = m.t(13), maxLines = 1)
                        }
                        ImmersiveAction(
                            keyLabel = "A",
                            label = "Play",
                            filled = true,
                            modifier = Modifier.focusRequester(playFocus).focusProperties {
                                left = carouselFocus
                                right = detailsFocus
                                up = carouselFocus
                                down = filterFocus
                            }.testTag("immersive_play"),
                            onClick = { onPlay(game) },
                        )
                        ImmersiveAction(
                            keyLabel = "X",
                            label = "Details",
                            filled = false,
                            modifier = Modifier.focusRequester(detailsFocus).focusProperties {
                                left = playFocus
                                right = filterFocus
                                up = carouselFocus
                                down = filterFocus
                            }.testTag("immersive_details"),
                            onClick = { onDetails(game) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(m.d(12)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            var filterFocused by remember { mutableStateOf(false) }
            Row(
                Modifier.focusRequester(filterFocus).onPreviewKeyEvent { event ->
                    val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
                    if (!activate) false
                    else if (event.type == KeyEventType.KeyUp) {
                        onCycleFilter()
                        true
                    } else true
                }.focusable().focusProperties {
                    up = if (game == null) carouselFocus else playFocus
                    right = if (game == null) FocusRequester.Cancel else detailsFocus
                }.onFocusChanged { filterFocused = it.isFocused }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121A24).copy(alpha = 0.92f))
                    .border(if (filterFocused) 2.dp else 1.dp, if (filterFocused) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onCycleFilter)
                    .padding(horizontal = m.d(12), vertical = m.d(8))
                    .testTag("library_filter"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(m.d(8)),
            ) {
                Icon(Icons.Outlined.FilterAlt, null, tint = KryoColors.Muted, modifier = Modifier.size(m.d(16)))
                Text(filterLabel, color = KryoColors.Text, fontSize = m.t(13))
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = KryoColors.Muted, modifier = Modifier.size(m.d(16)))
            }
        }
    }
}

@Composable
private fun ImmersiveAction(
    keyLabel: String,
    label: String,
    filled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.onPreviewKeyEvent { event ->
            val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
            if (!activate) false
            else if (event.type == KeyEventType.KeyUp) {
                onClick()
                true
            } else true
        }.focusable().height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (filled) KryoColors.Accent else Color.White.copy(alpha = 0.08f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = if (filled) 0f else 0.2f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(22.dp).background(if (filled) Color(0xFF083044) else Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(keyLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = if (filled) Color(0xFF062033) else KryoColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
