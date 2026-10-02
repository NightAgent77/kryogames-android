package com.kryogames.app

import androidx.annotation.DrawableRes
import java.util.Locale

enum class GamePlatform { WEB, ANDROID }
enum class LibraryFilter(val label: String) {
    ALL("All"), WEB("Web games"), ANDROID("Android"), INSTALLED("Installed"), FAVORITES("Favorites")
}

/** Frame lock for the library shell. Null in the UI means "fill this screen". */
enum class DisplayAspect(val ratio: Float, val label: String) {
    WIDESCREEN(16f / 9f, "16:9"),
    CLASSIC(4f / 3f, "4:3");

    companion object {
        fun closest(width: Float, height: Float): DisplayAspect {
            if (width <= 0f || height <= 0f) return WIDESCREEN
            val ratio = width / height
            return entries.minBy { kotlin.math.abs(it.ratio - ratio) }
        }
    }
}

data class Game(
    val id: String,
    val title: String,
    @param:DrawableRes val cover: Int,
    val platform: GamePlatform,
    val description: String = "",
    val installed: Boolean = false,
    val favorite: Boolean = false,
    // Local asset paths are relative to app/src/main/assets, not filesystem paths.
    val webAssetPath: String? = null,
    val androidPackage: String? = null,
    // A user-picked folder, served by the same player as a bundled game.
    val contentTreeUri: String? = null,
    val contentEntry: String? = null,
    val genre: String = "",
    val tags: List<String> = emptyList(),
)

fun filterGames(games: List<Game>, filter: LibraryFilter, query: String): List<Game> {
    val term = query.trim().lowercase(Locale.ROOT)
    return games.filter { game ->
        val matchesFilter = when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.WEB -> game.platform == GamePlatform.WEB
            LibraryFilter.ANDROID -> game.platform == GamePlatform.ANDROID
            LibraryFilter.INSTALLED -> game.installed
            LibraryFilter.FAVORITES -> game.favorite
        }
        matchesFilter && game.title.lowercase(Locale.ROOT).contains(term)
    }
}

/** null means no neighbor; never wraps across row boundaries. */
fun gridNeighbor(index: Int, count: Int, columns: Int, dx: Int, dy: Int): Int? {
    if (index !in 0 until count || columns <= 0) return null
    val next = index + dx + dy * columns
    if (next !in 0 until count) return null
    if (dy == 0 && index / columns != next / columns) return null
    return next
}

object DemoGames {
    /** Bundled copy of the website's Maze-Ops build, already installed. */
    val all = listOf(
        Game(
            id = "maze-ops",
            title = "Maze-Ops",
            cover = R.drawable.cover_maze_ops,
            platform = GamePlatform.WEB,
            description = "An in-progress maze ops build. Early look at the game — playable in the browser while it is still in development.",
            installed = true,
            webAssetPath = "games/maze-ops/index.html",
            genre = "Maze",
            tags = listOf("Web", "Dev", "Maze"),
        ),
    )
}
