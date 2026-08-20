package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's own palette. Not the Material baseline, and not a copy of the web app's.
 *
 * THE RULE THAT DECIDES EVERY COLOUR HERE: the accent marks the ONE thing to act on, and nothing
 * else. A screen where four things are cobalt is a screen with no next action, which is exactly the
 * opposite of what the coach is for. Surfaces stay neutral so the accent stays meaningful.
 *
 *   Cobalt (primary)    the next best action, the live control, the selected tab. Never decoration.
 *   Teal (secondary)    a steady state — done today, on track, nothing needed. Deliberately NOT a
 *                       neon green: "fine" should not shout louder than "do this".
 *   Sand (tertiary)     eating and intake, which needed to not be another blue.
 *   Red (error)         only real problems, never a low number. A short night is not an error.
 *
 * Both schemes are hand-paired rather than generated from one seed, because the accent has to hold
 * contrast against ink AND against paper, and a single seed cannot do both: cobalt at #4C8DFF is
 * right on #101215 and fails on white, so light mode darkens it to #1D4ED8 instead of tinting it.
 *
 * Greys are cool but not blue-tinted, so the cobalt reads as the only chromatic thing on screen.
 */

// ---- dark: ink ground -------------------------------------------------------------------------
//
// The first version of this ladder stepped background -> surface -> surfaceVariant by about 6-8
// per channel each — background 0x101215, surface 0x16191D, surfaceVariant 0x1A1E23. On an actual
// screen that is not "subtle", it is invisible: nothing reads as a raised card, borders vanish into
// the panels they're supposed to outline, and the whole app looks like one flat near-black rectangle.
// This ladder steps by 15-25 per channel instead, which is closer to what Material's own dark
// tonal-elevation scale uses, so a card actually looks like it is sitting a level above the page.
val InkBackground = Color(0xFF0D0F12)
val InkSurface = Color(0xFF1C1F24)
/** Raised card — the next-action hero sits on this, one step up from plain surface. */
val InkSurfaceVariant = Color(0xFF262B33)
val InkOutline = Color(0xFF454C56)
val InkOutlineVariant = Color(0xFF323841)
val InkOnSurface = Color(0xFFEDEFF2)
val InkOnSurfaceVariant = Color(0xFFAEB6C0)

val CobaltDark = Color(0xFF5B9AFF)
val OnCobaltDark = Color(0xFF06183A)
val CobaltContainerDark = Color(0xFF1B3970)
val OnCobaltContainerDark = Color(0xFFCEDDFF)

val TealDark = Color(0xFF54DCC8)
val OnTealDark = Color(0xFF062B26)
// Was darker than the surface it sits on (0x0F1D1B against a 0x1A1E23 card) — a "selected" state
// that reads as a hole punched in the card instead of a highlight. Raised onto the same footing as
// CobaltContainerDark: a real lift off surfaceVariant, not a dip below it.
val TealContainerDark = Color(0xFF184A42)
val OnTealContainerDark = Color(0xFFBFF3E8)

val SandDark = Color(0xFFE6C692)
val OnSandDark = Color(0xFF2A1F0A)
val SandContainerDark = Color(0xFF4A3A1C)
val OnSandContainerDark = Color(0xFFF6E7C4)

val RedDark = Color(0xFFFF8585)
val OnRedDark = Color(0xFF2A0A0A)
val RedContainerDark = Color(0xFF572026)
val OnRedContainerDark = Color(0xFFFFD6D6)

// ---- light: paper ground ----------------------------------------------------------------------
val PaperBackground = Color(0xFFFBFAF9)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceVariant = Color(0xFFF3F2F0)
val PaperOutline = Color(0xFFD7D5D1)
val PaperOutlineVariant = Color(0xFFE4E3E0)
val PaperOnSurface = Color(0xFF16181B)
val PaperOnSurfaceVariant = Color(0xFF5C6169)

val CobaltLight = Color(0xFF1D4ED8)
val OnCobaltLight = Color(0xFFFFFFFF)
val CobaltContainerLight = Color(0xFFDCE7FF)
val OnCobaltContainerLight = Color(0xFF0A2A75)

val TealLight = Color(0xFF0B6E60)
val OnTealLight = Color(0xFFFFFFFF)
val TealContainerLight = Color(0xFFD6F3EC)
val OnTealContainerLight = Color(0xFF04352E)

val SandLight = Color(0xFF8A6A22)
val OnSandLight = Color(0xFFFFFFFF)
val SandContainerLight = Color(0xFFF6E9C9)
val OnSandContainerLight = Color(0xFF3A2A05)

val RedLight = Color(0xFFB91C1C)
val OnRedLight = Color(0xFFFFFFFF)
val RedContainerLight = Color(0xFFFEE2E2)
val OnRedContainerLight = Color(0xFF5C0A0A)
