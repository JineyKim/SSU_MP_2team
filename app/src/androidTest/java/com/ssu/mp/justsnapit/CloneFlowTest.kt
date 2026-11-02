package com.ssu.mp.justsnapit

import com.ssu.mp.justsnapit.data.StickerStore
import com.ssu.mp.justsnapit.data.erase
import com.ssu.mp.justsnapit.data.outlined

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CloneFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun collectionAndCameraNavigation() {
        compose.waitForIdle()
        compose.onNodeWithText("Snap It!").assertExists()
        capture("collection.png")
        compose.onNodeWithContentDescription("카메라").performClick()
        compose.onNodeWithContentDescription("촬영").assertExists()
        compose.onNodeWithContentDescription("수집함").performClick()
        compose.onNodeWithContentDescription("보기 전환").performClick()
        compose.onNodeWithContentDescription("사진 추가").assertExists()
    }

    @Test fun transparentOutlineAndPersistence() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 30..69) for (y in 20..79) bitmap.setPixel(x, y, Color.RED)
        val result = outlined(bitmap)
        assertEquals(68, result.width)
        assertEquals(88, result.height)
        assertEquals(Color.RED, result.getPixel(34, 44))
        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
        val erased = erase(bitmap, 50f, 50f, 10f)
        assertEquals(0, Color.alpha(erased.getPixel(50, 50)))
        assertEquals(Color.RED, bitmap.getPixel(50, 50))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = StickerStore(context)
        val before = store.load()
        val added = store.add(result, before)
        try {
            val reloaded = StickerStore(context).load()
            assertTrue(reloaded.any { it.id == added.id && it.bitmap.width == 68 })
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(added.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(added.title).performTouchInput {
                swipe(center, center.copy(y = center.y - 180f), 400)
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(added.title).performClick()
            compose.onNodeWithText("닫기").assertExists()
            capture("detail.png")
        } finally { store.delete(added, store.load()) }
        assertFalse(store.load().any { it.id == added.id })
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
