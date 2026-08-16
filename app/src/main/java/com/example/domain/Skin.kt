package com.example.domain

object Skin {
    data class Habit(val id: String, val label: String, val why: String)

    val HABITS = listOf(
        Habit("spf", "Sunscreen", "..."),
        Habit("washPost", "Washed after training", "..."),
        Habit("moisturise", "Moisturised", "..."),
        Habit("nopick", "Left it alone", "...")
    )
}
