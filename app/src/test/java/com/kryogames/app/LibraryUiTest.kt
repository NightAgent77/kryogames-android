package com.kryogames.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.KeyEvent
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

    private fun controllerButton(code: Int) {
        compose.runOnUiThread {
            val now = SystemClock.uptimeMillis()
            compose.activity.onKeyDown(code, KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            compose.activity.onKeyUp(code, KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        }
        compose.waitForIdle()
    }

    @Test fun renderAndActivateActuallyFocusedGame() {
        compose.onNodeWithTag("game_maze-ops").assertIsFocused()
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
        compose.onNodeWithTag("search_field").performTextInput("does-not-exist")
        compose.onNodeWithText("No games match your search or filter.").assertExists()
        compose.onNodeWithTag("search_field").assertIsFocused()
        compose.onNodeWithTag("search_field").performTextClearance()
        compose.onNodeWithTag("game_maze-ops").assertExists()
        compose.onNodeWithTag("filter_Android").performClick()
        compose.onNodeWithTag("game_maze-ops").assertDoesNotExist()
        compose.onNodeWithTag("filter_All").performClick()
        compose.onNodeWithTag("game_maze-ops").assertExists()
        compose.onNodeWithTag("menu_button").performClick()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithTag("menu_button").performClick()
        compose.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test fun controllerOptionsFavoritesAndBackRestoreFocus() {
        controllerButton(KeyEvent.KEYCODE_BUTTON_X)
        compose.onNodeWithTag("play_action").assertExists()
        compose.onNodeWithTag("info_action").performClick()
        compose.onNodeWithText("An in-progress maze ops build. Early look at the game — playable in the browser while it is still in development.").assertExists()
        controllerButton(KeyEvent.KEYCODE_BUTTON_B)
        compose.onNodeWithTag("play_action").assertExists()
        controllerButton(KeyEvent.KEYCODE_BUTTON_B)
        compose.onNodeWithTag("game_maze-ops").assertIsFocused()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w850dp-h480dp-land-mdpi")
    fun handheldHeaderDoesNotSqueezeSearchOut() {
        compose.onNodeWithTag("search_field").assertIsDisplayed().assertWidthIsAtLeast(140.dp)
        compose.onNodeWithContentDescription("Profile: Kidxpr").assertIsDisplayed()
        compose.onNodeWithTag("game_maze-ops").assertIsFocused()
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
        val add = compose.onNodeWithTag("add_game").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("network_status").fetchSemanticsNode().boundsInRoot
        assertTrue("add sits left of status (${add.right} vs ${status.left})", add.right <= status.left + 1f)
        compose.onNodeWithTag("add_game").performClick()
        compose.onNodeWithTag("add_game_action").assertIsDisplayed()
        compose.onNodeWithText("Add a game").assertIsDisplayed()
    }

    @Test fun settingsShowsThisBuildVersion() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("app_details").assertIsDisplayed()
        compose.onNodeWithText("APP DETAILS").assertIsDisplayed()
        compose.onNodeWithContentDescription("App version 0.0.1").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("app_details").assertDoesNotExist()
    }

    private fun assertOpenRailSearchGap(atLeast: androidx.compose.ui.unit.Dp) {
        compose.onNodeWithContentDescription("Settings").assertExists()
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
