package com.kryogames.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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
    var sidebarOpen by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.ALL.name) }
    var section by rememberSaveable { mutableStateOf("Library") }
    var lastFocusedGameId by rememberSaveable { mutableStateOf(games.firstOrNull()?.id) }
    var actionGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingInfo by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var profileMenuOpen by rememberSaveable { mutableStateOf(false) }
    var addMenuOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchHadFocus by remember { mutableStateOf(false) }
    var searchRequest by remember { mutableIntStateOf(0) }
    var firstFocusPlaced by remember { mutableStateOf(false) }
    var previousModalOpen by remember { mutableStateOf(false) }
    var previousChoicesOpen by remember { mutableStateOf(false) }
    val filter = LibraryFilter.valueOf(filterName)
    val visible = remember(games, filter, query) { filterGames(games, filter, query) }
    val ids = visible.map { it.id }
    val cardFocus = remember(ids) { ids.associateWith { FocusRequester() } }
    val filterFocus = remember { LibraryFilter.entries.associateWith { FocusRequester() } }
    val menuFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val selectedGame = games.firstOrNull { it.id == lastFocusedGameId }
    val actionGame = games.firstOrNull { it.id == actionGameId }
    val anchors = remember { mutableStateMapOf<String, Rect>() }
    var frameCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuSize by remember { mutableStateOf(IntSize(0, 0)) }

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

    val handleAction by rememberUpdatedState<(ControllerAction) -> Unit> { action ->
        if (!modalOpen && actionGameId == null && !settingsOpen && !profileMenuOpen && !addMenuOpen) when (action) {
            ControllerAction.MENU -> sidebarOpen = !sidebarOpen
            ControllerAction.SEARCH -> searchRequest++
            ControllerAction.OPTIONS -> selectedGame?.let(::openGameChoices)
            ControllerAction.PREVIOUS_TAB -> section = "Library"
            ControllerAction.NEXT_TAB -> section = "Discover"
        }
    }
    LaunchedEffect(controllerActions) { controllerActions.collect { handleAction(it) } }
    LaunchedEffect(searchRequest) {
        if (searchRequest == 0) return@LaunchedEffect
        searchOpen = true
        withFrameNanos { }
        searchFocus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(ids, modalOpen, actionGameId, showingInfo) {
        val choicesOpen = actionGameId != null && !showingInfo
        if (!modalOpen && !choicesOpen && !showingInfo && !firstFocusPlaced && ids.isNotEmpty() && section != "Friends") {
            gridState.scrollToItem(0)
            withFrameNanos { }
            cardFocus[ids.first()]?.requestFocus()
            firstFocusPlaced = true
        } else if (!modalOpen && !choicesOpen && !showingInfo && (previousModalOpen || previousChoicesOpen)) {
            val index = ids.indexOf(lastFocusedGameId).takeIf { it >= 0 } ?: 0
            if (ids.isNotEmpty() && section != "Friends") {
                gridState.scrollToItem(index)
                withFrameNanos { }
                cardFocus[ids[index]]?.requestFocus()
            } else menuFocus.requestFocus()
        }
        previousModalOpen = modalOpen
        previousChoicesOpen = choicesOpen
    }
    BackHandler(enabled = !modalOpen && (settingsOpen || profileMenuOpen || addMenuOpen || actionGameId != null || section == "Friends" || query.isNotEmpty() || sidebarOpen)) {
        when {
            addMenuOpen -> addMenuOpen = false
            settingsOpen -> settingsOpen = false
            profileMenuOpen -> profileMenuOpen = false
            actionGameId != null && showingInfo -> showingInfo = false
            actionGameId != null -> closeChoices()
            query.isNotEmpty() -> query = ""
            section == "Friends" -> section = "Library"
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
        Box(
            Modifier.size(animatedW, animatedH).align(Alignment.Center)
                .clipToBounds()
                .background(KryoColors.Background)
                .onGloballyPositioned { frameCoordinates = it },
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
            CompositionLocalProvider(LocalUiEnabled provides chromeEnabled) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        open = sidebarOpen,
                        m = m,
                        section = section,
                        settingsOpen = settingsOpen,
                        translucent = showingInfo,
                        onLibrary = {
                            section = "Library"
                            profileMenuOpen = false
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
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val narrow = maxWidth < 650.dp && resolved != DisplayAspect.CLASSIC
                        val compactSearch = resolved == DisplayAspect.CLASSIC
                        val availableGridWidth = maxWidth - m.d(38)
                        val columns = when {
                            availableGridWidth >= 720.dp -> 5
                            availableGridWidth >= 560.dp -> 4
                            availableGridWidth >= 420.dp -> 3
                            else -> 2
                        }
                        CompositionLocalProvider(LocalFrosted provides showingInfo) {
                        Column(Modifier.fillMaxSize().padding(start = m.d(18), end = m.d(20), top = m.d(28))) {
                            Header(
                                m = m,
                                narrow = narrow,
                                compactSearch = compactSearch,
                                menuOpen = sidebarOpen,
                                searchExpanded = searchOpen || query.isNotEmpty(),
                                section = section,
                                query = query,
                                username = username,
                                searchFocus = searchFocus,
                                menuFocus = menuFocus,
                                onSection = {
                                    section = it
                                    profileMenuOpen = false
                                },
                                onQuery = { query = it },
                                onToggleMenu = { sidebarOpen = !sidebarOpen },
                                onNotifications = onNotifications,
                                onProfile = { profileMenuOpen = !profileMenuOpen },
                                onProfilePlaced = { rememberAnchor("profile", it) },
                                onExpandSearch = { searchRequest++ },
                                onSearchFocus = { focused ->
                                    if (focused) searchHadFocus = true
                                    else if (searchHadFocus && query.isEmpty()) {
                                        searchHadFocus = false
                                        searchOpen = false
                                    }
                                },
                            )
                            Spacer(Modifier.height(m.d(14)))
                            val filtersContent: @Composable () -> Unit = {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).focusGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(m.d(10)),
                                ) {
                                    LibraryFilter.entries.forEach { tab ->
                                        FilterTab(
                                            tab,
                                            filter == tab,
                                            m,
                                            Modifier.focusRequester(filterFocus.getValue(tab)).focusProperties {
                                                down = ids.firstOrNull()?.let { cardFocus.getValue(it) } ?: FocusRequester.Default
                                            },
                                        ) { filterName = tab.name }
                                    }
                                }
                            }
                            if (section != "Friends") {
                                val libraryStatus: @Composable () -> Unit = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FocusBox(
                                            Modifier.padding(start = m.d(10)).size(m.d(32).coerceAtLeast(36.dp))
                                                .onGloballyPositioned { rememberAnchor("add-game", it) }
                                                .testTag("add_game"),
                                            "Add",
                                            outlined = true,
                                            onClick = {
                                                profileMenuOpen = false
                                                settingsOpen = false
                                                closeChoices()
                                                addMenuOpen = !addMenuOpen
                                            },
                                        ) {
                                            Icon(Icons.Outlined.Add, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(18)))
                                        }
                                        OnlineStatus(online, m)
                                    }
                                }
                                if (narrow) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) {
                                            Crossfade(section, animationSpec = tween(MotionIn, easing = MotionEase), label = "title") { Title(it, m) }
                                        }
                                        libraryStatus()
                                    }
                                    Spacer(Modifier.height(m.d(10)))
                                    filtersContent()
                                } else {
                                    Row(Modifier.fillMaxWidth().heightIn(min = m.d(44)), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.padding(start = m.d(8))) {
                                            Crossfade(section, animationSpec = tween(MotionIn, easing = MotionEase), label = "title") { Title(it, m) }
                                        }
                                        Spacer(Modifier.width(m.d(16)))
                                        Box(Modifier.weight(1f)) { filtersContent() }
                                        libraryStatus()
                                    }
                                }
                                Spacer(Modifier.height(m.d(14)))
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                OverlayVisibility(
                                    visible = section != "Friends" && !showingInfo,
                                    modifier = Modifier.fillMaxSize(),
                                    enter = fadeIn(motionIn()) + slideInHorizontally(motionIn()) { -it / 16 },
                                    exit = fadeOut(motionOut()) + slideOutHorizontally(motionOut()) { -it / 16 },
                                ) {
                                    if (visible.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No games match your search or filter.", color = KryoColors.Muted, fontSize = m.t(15))
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            state = gridState,
                                            modifier = Modifier.fillMaxSize().testTag("game_grid"),
                                            horizontalArrangement = Arrangement.spacedBy(m.d(12)),
                                            verticalArrangement = Arrangement.spacedBy(m.d(12)),
                                            contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, m.d(16)),
                                        ) {
                                                itemsIndexed(visible, key = { _, game -> game.id }) { index, game ->
                                                    val requester = cardFocus.getValue(game.id)
                                                    val neighbors = Modifier.focusProperties {
                                                        fun neighbor(dx: Int, dy: Int) = gridNeighbor(index, visible.size, columns, dx, dy)
                                                            ?.let { cardFocus.getValue(visible[it].id) }
                                                        left = neighbor(-1, 0) ?: FocusRequester.Default
                                                        right = neighbor(1, 0) ?: FocusRequester.Cancel
                                                        up = neighbor(0, -1) ?: filterFocus.getValue(filter)
                                                        down = neighbor(0, 1) ?: FocusRequester.Cancel
                                                    }
                                                    GameCard(
                                                        game = game,
                                                        m = m,
                                                        modifier = Modifier
                                                            .onGloballyPositioned { rememberAnchor(game.id, it) }
                                                            .focusRequester(requester)
                                                            .then(neighbors),
                                                        pinned = game.id == actionGameId,
                                                        onFocused = { lastFocusedGameId = game.id },
                                                        onSelect = { openGameChoices(game) },
                                                    )
                                                }
                                            }
                                        }
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
                }
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
    m: Metrics,
    section: String,
    settingsOpen: Boolean,
    translucent: Boolean,
    onLibrary: () -> Unit,
    onFriends: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
) {
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(motionIn()) + expandHorizontally(motionIn(), expandFrom = Alignment.Start),
        exit = fadeOut(motionOut()) + shrinkHorizontally(motionOut(), shrinkTowards = Alignment.Start),
    ) {
        Column(
            Modifier.width(m.d(78)).fillMaxHeight()
                .background(if (translucent) KryoColors.Rail.copy(alpha = 0.42f) else KryoColors.Rail)
                .padding(top = m.d(20)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            data class Item(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val active: Boolean, val action: () -> Unit)
            val targets = listOf(
                Item("Library", Icons.Outlined.SportsEsports, section == "Library" || section == "Discover", onLibrary),
                Item("Friends", Icons.Outlined.Group, section == "Friends", onFriends),
                Item("Downloads", Icons.Outlined.FileDownload, false, onDownloads),
                Item("Settings", Icons.Outlined.Settings, settingsOpen, onSettings),
            )
            targets.forEach { item ->
                val tint by androidx.compose.animation.animateColorAsState(
                    if (item.active) KryoColors.Accent else KryoColors.Muted,
                    tween(MotionIn, easing = MotionEase),
                    label = "railTint",
                )
                Box {
                    FocusBox(Modifier.size(m.d(48).coerceAtLeast(40.dp)), description = item.label, filled = item.active, onClick = item.action) {
                        Icon(item.icon, null, tint = tint, modifier = Modifier.size(m.d(24)))
                    }
                    if (item.active) Box(Modifier.align(Alignment.CenterStart).width(1.dp).height(m.d(40)).background(KryoColors.Accent))
                }
                Spacer(Modifier.height(m.d(20)))
            }
        }
    }
}

@Composable
private fun Header(
    m: Metrics,
    narrow: Boolean,
    compactSearch: Boolean,
    menuOpen: Boolean,
    searchExpanded: Boolean,
    section: String,
    query: String,
    username: String,
    searchFocus: FocusRequester,
    menuFocus: FocusRequester,
    onSection: (String) -> Unit,
    onQuery: (String) -> Unit,
    onToggleMenu: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onProfilePlaced: (LayoutCoordinates) -> Unit,
    onExpandSearch: () -> Unit,
    onSearchFocus: (Boolean) -> Unit,
) {
    val widget = m.d(54).coerceAtLeast(44.dp)
    val menuButton: @Composable () -> Unit = {
        FocusBox(Modifier.size(widget).focusRequester(menuFocus).testTag("menu_button"), description = "Menu", outlined = true, onClick = onToggleMenu) {
            Icon(Icons.Outlined.Menu, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(24)))
        }
    }
    val tabGroup: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.d(12))) {
            menuButton()
            KeyBadge("LB", m)
            HeaderTab("Library", section == "Library", m) { onSection("Library") }
            HeaderTab("Discover", section == "Discover", m) { onSection("Discover") }
            KeyBadge("RB", m)
        }
    }
    val bell: @Composable () -> Unit = {
        FocusBox(Modifier.size(widget), "Notifications", outlined = true, onClick = onNotifications) {
            Icon(Icons.Outlined.Notifications, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(24)))
        }
    }
    val profile: @Composable (Modifier) -> Unit = { mod ->
        FocusBox(
            mod.height(widget).onGloballyPositioned(onProfilePlaced),
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
                    Modifier.size(m.d(40)).clip(RoundedCornerShape(m.d(5))),
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
    val searchIcon: @Composable () -> Unit = {
        FocusBox(Modifier.size(widget).testTag("search_button"), "Search", outlined = true, onClick = onExpandSearch) {
            Icon(Icons.Outlined.Search, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(22)))
        }
    }
    val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
        SearchField(query, onQuery, m, fieldModifier.focusRequester(searchFocus), onSearchFocus)
    }
    if (compactSearch) {
        val clusterGap = m.d(8)
        Column(verticalArrangement = Arrangement.spacedBy(m.d(10))) {
            if (menuOpen) {
                OpenRailHeader(
                    minGap = m.d(16),
                    preferredProfile = m.d(140),
                    minimumProfile = m.d(96),
                    tabs = { tabGroup() },
                    tools = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!searchExpanded) {
                                searchIcon()
                                Spacer(Modifier.width(clusterGap))
                            }
                            bell()
                            Spacer(Modifier.width(clusterGap))
                        }
                    },
                    profile = { profile(Modifier.fillMaxWidth()) },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(clusterGap),
                ) {
                    tabGroup()
                    Spacer(Modifier.weight(1f))
                    if (!searchExpanded) searchIcon()
                    bell(); profile(Modifier.width(m.d(140)))
                }
            }
            if (searchExpanded) searchField(Modifier.fillMaxWidth())
        }
    } else if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(m.d(10))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                tabGroup(); bell()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(m.d(12)), verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, onQuery, m, Modifier.weight(1f).widthIn(max = m.d(220)).focusRequester(searchFocus), onSearchFocus); profile(Modifier.width(m.d(140)))
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = m.d(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabGroup()
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(m.d(16)))
            SearchField(query, onQuery, m, Modifier.width(m.d(200)).focusRequester(searchFocus), onSearchFocus)
            Spacer(Modifier.width(m.d(6)))
            bell()
            Spacer(Modifier.width(m.d(6)))
            profile(Modifier.width(m.d(140)))
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
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    m: Metrics,
    modifier: Modifier,
    onFocusChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val frosted = LocalFrosted.current
    val border by androidx.compose.animation.animateColorAsState(
        if (focused) Color.White else if (frosted) Color.White.copy(alpha = 0.22f) else KryoColors.Border,
        tween(MotionIn, easing = MotionEase),
        label = "searchBorder",
    )
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        enabled = LocalUiEnabled.current,
        singleLine = true,
        cursorBrush = SolidColor(KryoColors.Accent),
        textStyle = TextStyle(color = KryoColors.Text, fontSize = m.t(14)),
        modifier = modifier.height(m.d(54).coerceAtLeast(44.dp)).onFocusChanged {
            focused = it.isFocused
            onFocusChange(it.isFocused)
        }
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
private fun HeaderTab(label: String, active: Boolean, m: Metrics, onClick: () -> Unit) {
    val color by androidx.compose.animation.animateColorAsState(
        if (active) KryoColors.Text else KryoColors.Muted,
        tween(MotionIn, easing = MotionEase),
        label = "tabColor",
    )
    val underline by androidx.compose.animation.animateColorAsState(
        if (active) KryoColors.Accent else Color.Transparent,
        tween(MotionIn, easing = MotionEase),
        label = "tabLine",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusBox(Modifier.height(m.d(36)).width(m.d(if (label == "Library") 84 else 94)), label, onClick = onClick) {
            Text(label, color = color, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, fontSize = m.t(16))
        }
        Box(Modifier.width(m.d(84)).height(2.dp).background(underline))
    }
}

@Composable
private fun FilterTab(filter: LibraryFilter, active: Boolean, m: Metrics, modifier: Modifier, onClick: () -> Unit) {
    val icon = when (filter) {
        LibraryFilter.ALL -> Icons.Outlined.GridView
        LibraryFilter.WEB -> Icons.Outlined.Language
        LibraryFilter.ANDROID -> Icons.Outlined.Android
        LibraryFilter.INSTALLED -> Icons.Outlined.Check
        LibraryFilter.FAVORITES -> Icons.Outlined.Star
    }
    val frosted = LocalFrosted.current
    val edge by androidx.compose.animation.animateColorAsState(
        when {
            active -> KryoColors.Accent
            frosted -> Color.White.copy(alpha = 0.22f)
            else -> KryoColors.Border
        },
        tween(MotionIn, easing = MotionEase),
        label = "filterEdge",
    )
    FocusBox(modifier.size(m.d(44)).testTag("filter_${filter.label}"), filter.label, onClick = onClick) {
        Box(
            Modifier.fillMaxSize().padding(3.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (frosted) Color.White.copy(alpha = 0.16f) else KryoColors.Surface)
                .border(if (active) 2.dp else 1.dp, edge, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (active) KryoColors.Accent else KryoColors.Muted, modifier = Modifier.size(m.d(20)))
        }
    }
}

@Composable
private fun OnlineStatus(online: Boolean, m: Metrics) {
    val glow = if (online) KryoColors.Green else KryoColors.Offline
    Row(
        Modifier.padding(start = m.d(8)).semantics { contentDescription = if (online) "Online" else "Offline" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.d(8)),
    ) {
        Box(Modifier.size(m.d(18)), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(m.d(16)).background(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.75f), glow.copy(alpha = 0f))),
                    CircleShape,
                ),
            )
            Box(Modifier.size(m.d(8)).background(glow, CircleShape))
        }
        Text(if (online) "Online" else "Offline", color = KryoColors.Text, fontSize = m.t(11))
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
            .focusProperties { canFocus = enabled }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onSelect)
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
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    val enabled = LocalUiEnabled.current
    val frosted = LocalFrosted.current
    val showFill = filled || outlined || focused
    val border by androidx.compose.animation.animateColorAsState(
        when {
            focused -> Color.White
            frosted && showFill -> Color.White.copy(alpha = 0.22f)
            outlined -> KryoColors.Border
            else -> Color.Transparent
        },
        tween(160, easing = MotionEase),
        label = "focusBorder",
    )
    val fill by androidx.compose.animation.animateColorAsState(
        when {
            frosted && showFill -> Color.White.copy(alpha = 0.16f)
            showFill -> KryoColors.Surface
            else -> Color.Transparent
        },
        tween(160, easing = MotionEase),
        label = "focusFill",
    )
    Box(
        modifier.onFocusChanged { focused = it.isFocused }.clip(shape)
            .background(fill)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .focusProperties { canFocus = enabled }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
            val shownGame = current ?: return@AnimatedVisibility
            LaunchedEffect(shownGame.id) {
                withFrameNanos { }
                playFocus.requestFocus()
            }
            Row(
                Modifier.width(menuWidth).height(IntrinsicSize.Min)
                    .onSizeChanged(onMenuSize)
                    .shadow(16.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(KryoColors.Surface)
                    .border(1.dp, KryoColors.Border, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                if (!place.placeOnLeft) Box(Modifier.width(3.dp).fillMaxHeight().background(KryoColors.Accent))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(shownGame.title, color = KryoColors.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    FocusBox(Modifier.fillMaxWidth().height(34.dp).focusRequester(playFocus).testTag("play_action"), "Play", outlined = true, onClick = { onPlay(shownGame) }) {
                        Text("Play", color = KryoColors.Text, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    FocusBox(Modifier.fillMaxWidth().height(34.dp).testTag("info_action"), "Info", outlined = true, onClick = onInfo) {
                        Text("Info", color = KryoColors.Text, fontSize = 13.sp)
                    }
                }
                if (place.placeOnLeft) Box(Modifier.width(3.dp).fillMaxHeight().background(KryoColors.Accent))
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
        withFrameNanos { }
        playFocus.requestFocus()
    }
    val gap = if (classic) m.d(12) else m.d(20)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().padding(horizontal = m.d(8), vertical = m.d(6))) {
            FocusBox(Modifier.size(m.d(40)), "Back", outlined = true, onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = KryoColors.Text, modifier = Modifier.size(m.d(18)))
            }
            Spacer(Modifier.height(m.d(8)))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
            withFrameNanos { }
            action.requestFocus()
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
            withFrameNanos { }
            first.requestFocus()
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
                withFrameNanos { }
                (if (aspect == DisplayAspect.CLASSIC) classic else wide).requestFocus()
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
