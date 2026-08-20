package com.example.domain

/**
 * Pulling the checkable parts out of an answer. Pure — no network, no provider, no second model.
 *
 * This finds NUMBERS, and only numbers, because a number is the one kind of claim a machine can
 * check without understanding the sentence around it. "Your depth held up better this week" is
 * unverifiable by any means short of another judgement; "in 4 of your last 6 sets" is arithmetic.
 *
 * WHY NOT PARSE MEANING. The obvious next step — work out what each number REFERS to, so "6 kg" can
 * be rejected when the 6 in evidence was a set count — needs to understand the sentence, and
 * anything that understands the sentence is a second model marking the first one's homework. The
 * line drawn here is deliberate: extraction is mechanical, and where mechanism runs out it stops
 * rather than guessing.
 *
 * Ported from the legacy `claims.js`.
 */
enum class ClaimKind { NUMBER, PERCENTAGE }

/**
 * What class of statement a number sat in.
 *
 * OBSERVED and INFERENCE must be traceable to evidence. RECOMMENDATION is exempt: a suggested
 * number is a proposal, not a claim about anything that happened.
 */
enum class Claimed { OBSERVED, INFERENCE, RECOMMENDATION }

data class Claim(
    val raw: String,
    val value: Double,
    val kind: ClaimKind,
    /** Digits written after the point — the precision the claim itself asserts. */
    val decimals: Int,
    val at: Int,
    val context: String,
    val claimed: Claimed,
    val sentence: String? = null,
)

object Claims {
    /** Numbers as a person writes them: 60, 62.5, -1.3, 38%, 0.34. */
    private val NUMBER = Regex("(-?\\d+(?:\\.\\d+)?)(\\s*%)?")

    /** A full stop ends a sentence only when whitespace or the end follows — otherwise "0.38" splits
     *  in half and one true claim becomes two fabricated ones, "0" and "38". */
    private val ENDER = Regex("([.!?]+(?=\\s|$)|\\n+)\\s*")

    /** Enough either side to see what the number was doing, for a human reading a failure report. */
    private const val CONTEXT = 40

    /**
     * Phrases that make a sentence a suggestion rather than a report.
     *
     * A word list, which means a heuristic, which means wrong sometimes. It errs toward treating a
     * sentence as a REPORT (the stricter reading) by requiring the cue to appear — silence is not
     * taken as permission. The structured path ([Validate.checkAnswer]) does not rely on this at
     * all: there, advice arrives in its own field and the classification is exact.
     */
    private val ADVICE_CUES = listOf(
        "try", "aim", "consider", "next time", "next session", "suggest", "recommend",
        "could ", "you might", "worth ", "go for", "stick to", "stay at", "drop to", "build up",
        "rest ", "wait ", "start with", "work up", "should ",
    )

    data class Sentence(val text: String, val start: Int)

    fun sentences(text: String): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var start = 0
        for (m in ENDER.findAll(text)) {
            val end = m.range.first + m.value.length
            out.add(Sentence(text.substring(start, end), start))
            start = end
        }
        if (start < text.length) out.add(Sentence(text.substring(start), start))
        return out.filter { it.text.isNotBlank() }
    }

    /** Does this sentence read as a recommendation rather than a report of what happened? */
    fun isAdvice(sentence: String): Boolean {
        val s = sentence.lowercase()
        return ADVICE_CUES.any { s.contains(it) }
    }

    /**
     * Every number in a piece of text, with what it looked like and where it sat.
     *
     * [ClaimKind] is how the number was WRITTEN, not what it means. The evidence side decides what
     * may legitimately produce each.
     */
    fun extract(text: String?, claimed: Claimed = Claimed.INFERENCE): List<Claim> {
        val src = text ?: return emptyList()
        return NUMBER.findAll(src).mapNotNull { m ->
            val digits = m.groupValues[1]
            val value = digits.toDoubleOrNull() ?: return@mapNotNull null
            val at = m.range.first
            Claim(
                raw = m.value.trim(),
                value = value,
                kind = if (m.groupValues[2].isNotEmpty()) ClaimKind.PERCENTAGE else ClaimKind.NUMBER,
                decimals = digits.substringAfter('.', "").length,
                at = at,
                context = src.substring(
                    maxOf(0, at - CONTEXT),
                    minOf(src.length, at + m.value.length + CONTEXT),
                ).trim(),
                claimed = claimed,
            )
        }.toList()
    }

    /**
     * Every number in a free-text answer, classified a sentence at a time.
     *
     * Used for the conversational path, where there is no structure to read the class off. A number
     * inside an advice sentence is marked RECOMMENDATION and exempted; everything else has to hold
     * up.
     */
    fun extractProse(text: String?): List<Claim> =
        sentences(text ?: "").flatMap { s ->
            extract(s.text, if (isAdvice(s.text)) Claimed.RECOMMENDATION else Claimed.INFERENCE)
                .map { it.copy(at = s.start + it.at, sentence = s.text.trim()) }
        }
}
