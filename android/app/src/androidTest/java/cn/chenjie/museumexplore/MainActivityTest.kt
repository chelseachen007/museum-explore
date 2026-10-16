package cn.chenjie.museumexplore

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun collectionScreenShowsPrimaryContent() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("国宝总览").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("国宝总览").fetchSemanticsNode()
        composeRule.onNodeWithText("全部国宝", substring = true).fetchSemanticsNode()
    }

    @Test
    fun collectionScreenCanCaptureScreenshot() {
        val image = composeRule.onNodeWithText("国宝总览").captureToImage()
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
    }
}
