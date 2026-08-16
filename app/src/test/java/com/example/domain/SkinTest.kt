package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class SkinTest {
    @Test
    fun `no custom products exist`() {
        assertEquals(4, Skin.HABITS.size)
        assertEquals("spf", Skin.HABITS[0].id)
        assertEquals("washPost", Skin.HABITS[1].id)
        assertEquals("moisturise", Skin.HABITS[2].id)
        assertEquals("nopick", Skin.HABITS[3].id)
    }
}
