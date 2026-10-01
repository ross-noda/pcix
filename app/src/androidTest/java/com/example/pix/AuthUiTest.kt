package com.example.pix

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.pix.ui.AuthScreen
import com.example.pix.ui.AuthFormViewModel
import com.example.pix.ui.MarkdownDescription
import com.example.pix.ui.theme.PixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AuthUiTest {
    @get:Rule val rule=createComposeRule()
    private val app=ApplicationProvider.getApplicationContext<PixApplication>()
    private fun label(id:Int)=app.getString(id)
    private fun ratio(a:Color,b:Color):Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun authReadableInLightDarkAndSystemWithMaskedPassword() {
        var mode by mutableIntStateOf(1)
        val model=AuthFormViewModel(app)
        model.email="readable@example.com";model.password="Private sample"
        var background=Color.Unspecified
        rule.setContent {
            PixTheme(mode=mode,textSize=2) {
                val scheme=MaterialTheme.colorScheme
                SideEffect {
                    background=scheme.background
                    assertTrue(ratio(scheme.onBackground,scheme.background)>=4.5f)
                    assertTrue(ratio(scheme.onSurfaceVariant,scheme.background)>=4.5f)
                    assertTrue(ratio(scheme.onPrimary,scheme.primary)>=4.5f)
                }
                AuthScreen(model)
            }
        }
        for(value in listOf(1,2,0)) {
            rule.runOnIdle {mode=value};rule.waitForIdle()
            rule.onNodeWithContentDescription("email").assertIsDisplayed()
            rule.onNodeWithText(label(R.string.password)).assertIsDisplayed()
            if(value==1) assertTrue(background.luminance()>.8f)
            if(value==2) assertTrue(background.luminance()<.1f)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/pix-auth-mode-$value.png")
                .use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
            val image=rule.onRoot().captureToImage().asAndroidBitmap()
            File(app.cacheDir,"auth-mode-$value.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        rule.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password)).assertExists()
        rule.onNodeWithText(label(R.string.auth_show_password)).performClick()
        rule.onNodeWithText("Private sample").assertExists()
        rule.onNodeWithText(label(R.string.auth_hide_password)).performClick()
        rule.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password)).assertExists()
        rule.onNodeWithText(label(R.string.auth_need_account)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.confirm_password)).assertExists()
    }

    @Test fun markdownAlwaysEditableAndChecklistEditOnlySource() {
        var source by mutableStateOf("## Heading\n- [ ] One\n- [ ] Two")
        rule.setContent {PixTheme(mode=1) {MarkdownDescription("sample",source,{source=it})}}
        rule.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-markdown-live.png")
            .use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        val offset=source.indexOf("[ ]")+1
        rule.onNodeWithTag("markdown-toggle-$offset").performClick()
        rule.runOnIdle {assertEquals("## Heading\n- [x] One\n- [ ] Two",source)}
        rule.onNodeWithTag("markdown-mode").assertDoesNotExist()
        rule.onNodeWithTag("detail-notes").assertTextContains(source)
        rule.onNodeWithTag("detail-notes").performTextClearance()
        rule.onNodeWithTag("markdown-checklist").performClick()
        rule.onNodeWithTag("detail-notes").performTextInput("New item")
        rule.runOnIdle {assertEquals("- [ ] New item",source)}
    }
}
