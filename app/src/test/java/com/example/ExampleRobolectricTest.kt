package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firestore.CommentStatus
import com.example.data.firestore.DEFAULT_AI_PROMPT
import com.example.data.firestore.LanguagePreference
import com.example.data.firestore.safeDocId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context matches GR GAMER AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GR GAMER AI", appName)
    }

    @Test
    fun `safeDocId strips invalid Firestore rule characters`() {
        val cleaned = safeDocId("Ugw91.abc/def?123")
        assertTrue(cleaned.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
    }

    @Test
    fun `enums safely fall back on unknown values`() {
        assertEquals(LanguagePreference.AUTO, LanguagePreference.fromRaw("UNKNOWN_VAL"))
        assertEquals(CommentStatus.PENDING, CommentStatus.fromRaw("LEGACY_STATUS"))
        assertTrue(DEFAULT_AI_PROMPT.contains("GR GAMER AI"))
    }
}
