package com.example

import com.example.data.model.AuraPipelineResponse
import com.example.data.remote.GeminiOrchestrator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraMindUnitTest {

    private val orchestrator = GeminiOrchestrator()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(AuraPipelineResponse::class.java)

    @Test
    fun `schema conformity test - verify JSON schema keys match specification exactly`() {
        val jsonString = """
            {
              "summary": "A concise, single-sentence summary of the overall goal or situation described by the user.",
              "tasks": [
                {
                  "title": "Fix critical bug on landing page",
                  "priority": "High",
                  "deadline": "Friday 5 PM"
                }
              ],
              "draft_reply": "Hi Team, we have mobilized the on-call engineer to patch the landing page."
            }
        """.trimIndent()

        val parsed = adapter.fromJson(jsonString)
        assertNotNull(parsed)
        assertEquals("A concise, single-sentence summary of the overall goal or situation described by the user.", parsed?.summary)
        assertEquals(1, parsed?.tasks?.size)
        assertEquals("Fix critical bug on landing page", parsed?.tasks?.get(0)?.title)
        assertEquals("High", parsed?.tasks?.get(0)?.priority)
        assertEquals("Friday 5 PM", parsed?.tasks?.get(0)?.deadline)
        assertTrue(parsed?.draftReply?.contains("Hi Team") == true)
    }

    @Test
    fun `zero actionable context returns empty tasks and None draft reply`() {
        val result = orchestrator.runLocalFallback("", null)
        assertEquals(0, result.response.tasks.size)
        assertEquals("None", result.response.draftReply)
        assertTrue(result.response.summary.isNotEmpty())

        val parsed = adapter.fromJson(result.rawJson)
        assertNotNull(parsed)
        assertEquals(0, parsed?.tasks?.size)
        assertEquals("None", parsed?.draftReply)
        assertTrue(parsed?.summary?.isNotEmpty() == true)
        assertTrue(result.rawJson.contains("\"summary\""))
        assertTrue(result.rawJson.contains("\"tasks\""))
        assertTrue(result.rawJson.contains("\"draft_reply\""))
    }

    @Test
    fun `urgent scenario extracts high priority tasks and deadlines`() {
        val text = "URGENT: Database server crashed at 3 PM! Need to rollback commit immediately. Bob must notify customer support by Friday 5 PM."
        val result = orchestrator.runLocalFallback(text, null)

        assertTrue(result.response.tasks.isNotEmpty())
        val hasHighPriority = result.response.tasks.any { it.priority == "High" }
        assertTrue("Expected at least one High priority task", hasHighPriority)
        assertTrue(result.response.draftReply != "None")
        assertTrue(result.response.summary.isNotEmpty())
    }
}
