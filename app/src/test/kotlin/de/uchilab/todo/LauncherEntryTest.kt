package de.uchilab.todo

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.uchilab.todo.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherEntryTest {

    @Test
    fun `merged manifest has exactly one exported launcher activity which is MainActivity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)

        val entries = context.packageManager.queryIntentActivities(intent, 0)

        assertEquals(1, entries.size)
        val info = entries.single().activityInfo
        assertTrue(info.exported)
        assertEquals(MainActivity::class.java.name, info.name)
    }
}
