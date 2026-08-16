package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class SkinNoteParserTest {
    @Test
    fun `parses boolean flags correctly`() {
        val text = """
            Based on your note, here are the inferred skin habits:
            moisturise: true
            spf: false
            washPost: true
            nopick: false
            other_stuff: true
        """.trimIndent()
        
        val parsed = SkinNoteParser.parseNotes(text)
        
        assertEquals(true, parsed["moisturise"])
        assertEquals(false, parsed["spf"])
        assertEquals(true, parsed["washPost"])
        assertEquals(false, parsed["nopick"])
        assertNull(parsed["other_stuff"]) // Only tracks known habits
    }
}
