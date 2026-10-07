package com.kryogames.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.After
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1100dp-h688dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class LibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun clearRemovedGames() {
        compose.activity.getSharedPreferences("kryo_settings", Context.MODE_PRIVATE)
            .edit().remove("removed_games").commit()
    }

    private fun controllerButton(code: Int) {
        compose.runOnUiThread {
            val now = SystemClock.uptimeMillis()
            compose.activity.onKeyDown(code, KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            compose.activity.onKeyUp(code, KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        }
        compose.waitForIdle()
    }

    @Test fun dpadAndStickDirectionsMoveFocus() {
        compose.onNodeWithTag("game_maze-ops").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("add_game").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("game_maze-ops").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("section_library").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("section_discover").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("game_maze-ops").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        compose.onNodeWithTag("play_action").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("info_action").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("delete_action").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("delete_action").assertIsSelected()
    }

    @Test fun renderAndActivateActuallyFocusedGame() {
        compose.onNodeWithTag("game_maze-ops").assertIsSelected()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val path = File("build/outputs/ui/library-landscape.png")
            path.parentFile?.mkdirs()
            path.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        compose.onNodeWithTag("game_maze-ops").performClick()
        compose.onNodeWithTag("play_action").assertExists()
        compose.onNodeWithTag("info_action").assertExists()
        compose.onNodeWithTag("play_action").performClick()
        val launched = shadowOf(compose.activity).nextStartedActivity
        assertTrue(launched.component?.className?.endsWith("WebGameActivity") == true)
        assertEquals("Maze-Ops", launched.getStringExtra(WebGameActivity.EXTRA_TITLE))
    }

    @Test fun filtersSearchAndMenuWorkTogether() {
        compose.onNodeWithTag("game_maze-ops").assertExists()
        compose.onNodeWithContentDescription("Kryo Games logo").assertDoesNotExist()
        compose.onNodeWithTag("network_status").assertDoesNotExist()
        compose.onNodeWithTag("search_button").performClick()
        compose.onNodeWithTag("search_field").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("search_bar").assertDoesNotExist()
        controllerButton(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("search_field").assertIsSelected()
        compose.onNodeWithTag("search_field").performTextInput("does-not-exist")
        compose.onNodeWithText("No games match your search or filter.").assertExists()
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        compose.onNodeWithTag("search_field").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_B)
        compose.onNodeWithTag("search_field").assertDoesNotExist()
        compose.onNodeWithTag("game_maze-ops").assertExists()
        compose.onNodeWithTag("section_favorites").performClick()
        compose.onNodeWithTag("game_maze-ops").assertDoesNotExist()
        compose.onNodeWithTag("section_library").performClick()
        compose.onNodeWithTag("game_maze-ops").assertExists()
        compose.onNodeWithTag("view_mode").assertDoesNotExist()
        compose.onNodeWithTag("game_grid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithTag("menu_button").performClick()
        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.onNodeWithTag("menu_button").performClick()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
    }

    @Test fun deleteRemovesTheGameFromTheLibrary() {
        compose.onNodeWithTag("game_maze-ops").performClick()
        compose.onNodeWithTag("delete_action").assertIsDisplayed()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("delete_action").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        compose.onNodeWithTag("game_maze-ops").assertDoesNotExist()
        compose.onNodeWithTag("delete_action").assertDoesNotExist()
        compose.onNodeWithTag("add_game").assertIsDisplayed()
    }

    @Test fun controllerOptionsFavoritesAndBackRestoreFocus() {
        controllerButton(KeyEvent.KEYCODE_BUTTON_X)
        compose.onNodeWithTag("play_action").assertExists()
        compose.onNodeWithTag("info_action").performClick()
        compose.onNodeWithText("An in-progress maze ops build. Early look at the game — playable in the browser while it is still in development.").assertExists()
        controllerButton(KeyEvent.KEYCODE_BUTTON_B)
        compose.onNodeWithTag("play_action").assertDoesNotExist()
        compose.onNodeWithTag("game_maze-ops").assertIsSelected()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w850dp-h480dp-land-mdpi")
    fun handheldHeaderDoesNotSqueezeSearchOut() {
        compose.onNodeWithTag("search_button").assertIsDisplayed().assertWidthIsAtLeast(40.dp)
        compose.onNodeWithTag("view_mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Profile: Kidxpr").assertIsDisplayed()
        compose.onNodeWithTag("game_maze-ops").assertIsSelected()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w576dp-h432dp-land-mdpi")
    fun classicOpenRailKeepsGapBetweenRbAndSearch() {
        assertOpenRailSearchGap(atLeast = 12.dp)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h720dp-land-mdpi")
    fun classicOpenRailGapGrowsWhenThereIsRoom() {
        assertOpenRailSearchGap(atLeast = 24.dp)
        compose.onNodeWithContentDescription("Profile: Kidxpr").assertWidthIsAtLeast(130.dp)
    }

    @Test fun addMenuOffersALocalGameFolder() {
        compose.onNodeWithTag("add_game").assertIsDisplayed()
        compose.onNodeWithTag("network_status").assertDoesNotExist()
        compose.onNodeWithTag("add_game").performClick()
        compose.onNodeWithTag("add_game_action").assertIsDisplayed().assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        compose.onNodeWithText("File app").assertIsDisplayed()
    }

    @Test fun profileMenuAndSettingsFollowTheCursor() {
        compose.onNodeWithContentDescription("Profile: Kidxpr").performClick()
        compose.onNodeWithContentDescription("View profile").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithContentDescription("Log out").assertIsSelected()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        controllerButton(KeyEvent.KEYCODE_BUTTON_B)
        compose.onNodeWithTag("menu_button").performClick()
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("aspect_16_9").assertIsSelected()
        compose.onNodeWithTag("game_grid").assertDoesNotExist()
        controllerButton(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("section_library").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_START)
        compose.onNodeWithContentDescription("Exit").assertIsDisplayed()
        controllerButton(KeyEvent.KEYCODE_BUTTON_START)
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("aspect_16_9").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("aspect_4_3").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("appearance_toggle").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithContentDescription("Appearance, dark mode").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        compose.onNodeWithContentDescription("Appearance, light mode").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithContentDescription("Appearance, dark mode").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithContentDescription("Close settings").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_B)
        compose.onNodeWithTag("app_details").assertDoesNotExist()
        compose.onNodeWithTag("game_grid").assertIsDisplayed()
    }

    @Test fun gameInfoMovesBetweenPlayFavoriteAndBack() {
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        controllerButton(KeyEvent.KEYCODE_DPAD_DOWN)
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        compose.onNodeWithTag("info_play").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("info_favorite").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_BUTTON_A)
        controllerButton(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("info_back").assertIsSelected()
        controllerButton(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("game_maze-ops").assertIsSelected()
        compose.onNodeWithTag("info_play").assertDoesNotExist()
    }

    @Test fun menuShowsThisBuildVersionAndExit() {
        compose.onNodeWithContentDescription("App version 0.0.6").assertDoesNotExist()
        compose.onNodeWithTag("menu_button").performClick()
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("App version 0.0.6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit").performClick()
        assertTrue(compose.activity.isFinishing)
    }

    private fun assertOpenRailSearchGap(atLeast: androidx.compose.ui.unit.Dp) {
        val rb = compose.onNodeWithText("RB", substring = false).fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("search_button").fetchSemanticsNode().boundsInRoot
        val bell = compose.onNodeWithContentDescription("Notifications").fetchSemanticsNode().boundsInRoot
        val profile = compose.onNodeWithContentDescription("Profile: Kidxpr").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val gap = search.left - rb.right
        val minimum = with(compose.density) { atLeast.toPx() }
        assertTrue("RB and search gap was $gap", gap >= minimum - 1f)
        assertEquals(bell.left - search.right, profile.left - bell.right, 1.5f)
        assertTrue("profile clipped at ${profile.right}, root ${root.right}", profile.right <= root.right + 1f)
        assertTrue(search.right <= root.right + 1f)
    }
}
