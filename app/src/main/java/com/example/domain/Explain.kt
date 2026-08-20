package com.example.domain

/**
 * "Why did it decide that?" — the flow, without the provider.
 *
 * The model call arrives as an injected [ask], so everything here is testable with no key, no
 * network and no mocking library: the policy is what needs proving, not that HTTP works.
 *
 * THE ORDER MATTERS AND IS THE POINT:
 *
 *   evidence → ask → validate → (one retry with feedback) → validate → render, or fall back
 *
 * Nothing reaches the caller until it has passed. And when it does not pass, the fallback is not an
 * apology — it is the same explanation assembled from the same numbers by arithmetic, which is what
 * the screen would have shown anyway. A failed model call costs the person nothing.
 *
 * Ported from the legacy `explain.js`.
 */
data class ModelAnswer(
    /** What the app recorded. Every number here must appear in the evidence. */
    val observed: List<String>,
    /** What the model reads into it. Its own words, but any number still has to be traceable. */
    val meaning: String,
    /** One thing to do next, or empty. A number here is a proposal, so it is exempt. */
    val suggestion: String = "",
)

enum class ExplainStatus {
    /** Validated. [Explanation.answer] is safe to show. */
    OK,

    /**
     * The model produced unsupported numbers twice. The answer is DISCARDED — not shown, not
     * trimmed, not partially rendered. [Explanation.plain] is what the caller shows.
     */
    UNVERIFIED,

    /** No answer came back at all: no key, no network, refusal, malformed JSON. */
    UNAVAILABLE,

    /** Nothing was ever recorded to explain, and nothing is invented to fill the gap. */
    NO_EVIDENCE,
}

data class Explanation(
    val status: ExplainStatus,
    val answer: ModelAnswer?,
    /** The deterministic line, correct by construction. Always safe to show. */
    val plain: String?,
    val attempts: Int,
    val unsupported: List<Claim> = emptyList(),
) {
    /** What to put on screen: the model's words when they held up, the app's own when they did not. */
    val display: String
        get() = if (status == ExplainStatus.OK && answer != null) Explain.render(answer)
        else plain ?: Explain.fallbackExplanation()
}

object Explain {
    /**
     * Ask, check, retry once, or fall back.
     *
     * @param evidence the packet the model may see. Null means nothing was recorded.
     * @param plain    the explanation the app can give with no model at all — every number in it read
     *                 straight off the engine's own verdict, so it is correct by construction.
     * @param ask      (evidence, feedback) -> answer. Feedback is non-null only on the retry, and it
     *                 names what was wrong — never a corrected number.
     */
    suspend fun explainDecision(
        evidence: Map<String, Any?>?,
        plain: String? = null,
        ask: suspend (evidence: Map<String, Any?>, feedback: String?) -> ModelAnswer?,
    ): Explanation {
        if (evidence == null) return Explanation(ExplainStatus.NO_EVIDENCE, null, plain, 0)

        val index = Validate.provenance(evidence)
        var feedback: String? = null
        var last: ClaimVerdict? = null

        for (attempt in 1..Validate.MAX_ATTEMPTS) {
            val answer = try {
                ask(evidence, feedback)
            } catch (e: Exception) {
                // Network, parse, refusal — all the same from here: no answer to check.
                null
            } ?: return Explanation(ExplainStatus.UNAVAILABLE, null, plain, attempt)

            val check = Validate.checkAnswer(answer, index)
            if (check.ok) return Explanation(ExplainStatus.OK, answer, plain, attempt)

            // Same evidence, plus precisely what was wrong. Never a corrected number — supplying one
            // would be inventing evidence to patch invented evidence.
            feedback = check.feedback
            last = check
        }

        return Explanation(
            ExplainStatus.UNVERIFIED,
            null,
            plain,
            Validate.MAX_ATTEMPTS,
            last?.unsupported?.map { it.claim } ?: emptyList(),
        )
    }

    /** The validated answer as one block of prose, in the order the model was asked to write it. */
    fun render(answer: ModelAnswer): String = listOfNotNull(
        answer.observed.joinToString(" ").ifBlank { null },
        answer.meaning.ifBlank { null },
        answer.suggestion.ifBlank { null },
    ).joinToString(" ")

    /** Last resort, for a caller that has no deterministic line of its own. */
    fun fallbackExplanation(): String = "This decision was based on your recorded performance."
}
