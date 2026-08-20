package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One shape scale, so a card is not 12dp on one screen and 16dp on the next.
 *
 * Rounder than the Material default at the large end: the app's screens are stacks of cards on a
 * plain ground, and at 20dp a card reads as a distinct object rather than as a boxed-off region of
 * the background. Buttons stay fully round (handled per-component), which is the one place a pill is
 * worth the extra pixels — it makes the primary action unmistakably tappable.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
