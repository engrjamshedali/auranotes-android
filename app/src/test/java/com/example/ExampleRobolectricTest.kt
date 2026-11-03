package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.CodeExecutionEngine
import kotlinx.coroutines.runBlocking
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
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AuraNotes", appName)
    }

    @Test
    fun `test python code execution engine`() = runBlocking {
        val code = """
            scores = [1, 2, 3]
            total = sum(scores)
            print("Total is:", total)
        """.trimIndent()
        val result = CodeExecutionEngine.executeCode("python", code)
        assertEquals("SUCCESS", result.status)
        assertTrue(result.stdout.contains("Total is: 6"))
    }

    @Test
    fun `test sql code execution engine`() = runBlocking {
        val sql = """
            CREATE TABLE test_notes (id INT, title TEXT);
            INSERT INTO test_notes VALUES (1, 'Aura');
            SELECT * FROM test_notes;
        """.trimIndent()
        val result = CodeExecutionEngine.executeCode("sql", sql)
        assertEquals("SUCCESS", result.status)
        assertTrue(result.stdout.contains("Aura"))
    }
}
