package com.kryogames.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LibraryLogicTest {
    private val games = listOf(
        Game("a", "Orbit", 0, GamePlatform.WEB, favorite = true),
        Game("b", "Drift", 0, GamePlatform.ANDROID, installed = true),
        Game("c", "Hollow Peak", 0, GamePlatform.ANDROID),
    )
    @Test fun filtersAndQueryCombineWithoutMutatingSource() {
        assertEquals(listOf("a"), filterGames(games, LibraryFilter.WEB, " ORB ").map { it.id })
        assertEquals(listOf("b", "c"), filterGames(games, LibraryFilter.ANDROID, "").map { it.id })
        assertEquals(listOf("b"), filterGames(games, LibraryFilter.INSTALLED, "").map { it.id })
        assertEquals(listOf("a"), filterGames(games, LibraryFilter.FAVORITES, "").map { it.id })
        assertTrue(filterGames(games, LibraryFilter.INSTALLED, "Orbit").isEmpty())
        assertEquals(3, games.size)
    }
    @Test fun searchIsLocaleIndependent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("b"), filterGames(games, LibraryFilter.ALL, "DRIFT").map { it.id })
        } finally { Locale.setDefault(original) }
    }
    @Test fun aspectFollowsTheNearerRatio() {
        assertEquals(DisplayAspect.CLASSIC, DisplayAspect.closest(4f, 3f))
        assertEquals(DisplayAspect.WIDESCREEN, DisplayAspect.closest(16f, 9f))
        assertEquals(DisplayAspect.WIDESCREEN, DisplayAspect.closest(1100f, 688f))
        assertEquals(DisplayAspect.CLASSIC, DisplayAspect.closest(1280f, 960f))
    }
    @Test fun gameMenuSitsBesideTheCardAndFlipsAtTheEdge() {
        val beside = placeGameMenu(100f, 80f, 220f, 240f, 800f, 480f, 148f, 124f)
        assertEquals(228f, beside.x)
        assertEquals(98f, beside.y)
        assertTrue(!beside.placeOnLeft)
        val flipped = placeGameMenu(640f, 40f, 760f, 200f, 800f, 480f, 148f, 124f)
        assertEquals(484f, flipped.x)
        assertTrue(flipped.placeOnLeft)
    }
    @Test fun dpadDoesNotWrapRowsOrSelectMissingTiles() {
        assertNull(gridNeighbor(4, 10, 5, 1, 0))
        assertNull(gridNeighbor(5, 10, 5, -1, 0))
        assertEquals(5, gridNeighbor(0, 10, 5, 0, 1))
        assertEquals(0, gridNeighbor(5, 10, 5, 0, -1))
        assertNull(gridNeighbor(3, 7, 5, 0, 1))
        assertNull(gridNeighbor(0, 0, 5, 1, 0))
        assertNull(gridNeighbor(0, 10, 0, 1, 0))
    }
    @Test fun localFolderUsesTheGameScriptBesideTheHtmlFile() {
        val entries = listOf(
            FolderEntry("index.html"),
            FolderEntry("q5.js"),
            FolderEntry("q5play.js"),
            FolderEntry("peerjs.min.js"),
            FolderEntry("maze-ops.js"),
            FolderEntry("other/note.txt"),
        )
        val html = """
            <script type="module" src="./q5.js"></script>
            <script type="module" src="./q5play.js"></script>
            <script src="./peerjs.min.js"></script>
            <script type="module" src="./maze-ops.js"></script>
        """.trimIndent()
        val chosen = chooseGame(entries) { if (it == "index.html") html else null }
        assertEquals("index.html", chosen?.entryPath)
        assertEquals("maze-ops.js", chosen?.scriptPath)
        assertEquals(
            "Maze Ops",
            titleFromGameScript("// Maze Ops — solo maze + 2-player PeerJS race.\nconst SPAWN = {x: 0};\n", "maze-ops.js"),
        )
    }
    @Test fun nestedFolderIsUsedWhenTheSelectedRootIsOnlyAParent() {
        val entries = listOf(
            FolderEntry("readme.txt"),
            FolderEntry("play/index.html"),
            FolderEntry("play/sketch.js"),
        )
        val chosen = chooseGame(entries) { if (it.endsWith("index.html")) "<script src=\"./sketch.js\"></script>" else null }
        assertEquals("play/index.html", chosen?.entryPath)
        assertEquals("play/sketch.js", chosen?.scriptPath)
        assertEquals("Sketch", titleFromGameScript("const speed = 1;\n", "sketch.js"))
    }
    @Test fun folderWithoutBothFilesIsNotAGame() {
        assertNull(chooseGame(listOf(FolderEntry("index.html"))) { "" })
        assertNull(chooseGame(listOf(FolderEntry("game.js"))) { "" })
        assertNull(normalizeRelative("../secret.js"))
        assertEquals("index.html", normalizeRequestPath("/"))
    }
}
