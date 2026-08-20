package com.example.domain

import kotlin.math.abs

/**
 * Checking an answer's numbers against the evidence that produced it. Pure — no network, no
 * provider, no second model.
 *
 * WHAT THIS IS. A containment layer for one claim class. Every number the evidence packet contains
 * is indexed as it is built, so checking a claim is a lookup, not a judgement. Nothing here scores
 * plausibility, and nothing here asks a model whether a model was right.
 *
 * WHAT THIS IS NOT, said plainly because the failure mode of a validator is people trusting it past
 * its edges:
 *
 *   It checks that a number EXISTS in the evidence. It does not check that the number was used for
 *   the right thing. Evidence holding `sets: 6` will accept "6 kg", "6 days" and "rep 6", because
 *   binding a number to its referent means parsing the sentence, and parsing the sentence is the
 *   line this layer does not cross.
 *
 *   It sees digits. A model that writes "two thirds of your sets" makes an unchecked claim. The
 *   answer to that is upstream — the explain prompt asks for digits — not a word-number parser here.
 *
 *   It says nothing about whether a sentence with no numbers in it is true.
 *
 * Ported from the legacy `validate.js`.
 */
enum class ClaimStatus { SUPPORTED, UNSUPPORTED, EXEMPT }

data class ProvenanceEntry(val id: String, val value: Double, val derived: String? = null)

data class CheckedClaim(
    val claim: Claim,
    val status: ClaimStatus,
    val source: String? = null,
    /** Which rule admitted it: "exact", "decimal" or "percent". */
    val rule: String? = null,
    val why: String? = null,
)

data class ClaimVerdict(
    val ok: Boolean,
    val checked: List<CheckedClaim>,
    val unsupported: List<CheckedClaim>,
    /**
     * Written for the model, not for a person: this is what a single retry is told, and it names the
     * offending numbers and nothing else. No corrected values are supplied — handing back a "right"
     * number would be inventing evidence to fix invented evidence.
     */
    val feedback: String?,
)

object Validate {
    /**
     * One retry, then stop. A loop that keeps paying for attempts until one passes is a cost attack
     * on the user's own key, and a model that failed twice on the same evidence will fail again.
     */
    const val MAX_ATTEMPTS = 2

    private fun round(v: Double, dp: Int): Double {
        val f = Math.pow(10.0, dp.toDouble())
        return Math.round((v + 1e-12) * f) / f
    }

    private fun eq(a: Double, b: Double) = abs(a - b) < 1e-9

    /**
     * Every number in the evidence packet, with the path it came from.
     *
     * Derived from the packet itself rather than listed by hand, so a field added to the evidence is
     * automatically checkable and cannot fall out of sync with a duplicate list somewhere. Booleans
     * and non-numeric strings are skipped, since a claim can only be checked against a quantity.
     *
     * One derived entry, and only one: `from`/`to` siblings also yield their difference. It is there
     * because "that is 2.5 kg more than last time" is a correct, natural sentence about a decision
     * whose difference is not itself stored, and it is the ONLY arithmetic admitted — a validator
     * that accepts any combination of any two evidence numbers accepts almost any number at all.
     */
    fun provenance(
        evidence: Any?,
        path: String = "",
        out: MutableList<ProvenanceEntry> = mutableListOf(),
    ): List<ProvenanceEntry> {
        when (evidence) {
            is Number -> out.add(ProvenanceEntry(path, evidence.toDouble()))
            is String -> evidence.toDoubleOrNull()?.let { out.add(ProvenanceEntry(path, it)) }
            is List<*> -> evidence.forEachIndexed { i, v -> provenance(v, "$path[$i]", out) }
            is Map<*, *> -> {
                for ((k, v) in evidence) {
                    provenance(v, if (path.isEmpty()) "$k" else "$path.$k", out)
                }
                // Only when the load actually moved. A hold has from == to, and admitting that zero
                // would put a 0 in the index that supports any "0" claim anywhere in the answer —
                // exactly the collapse the decimal rule refuses to allow.
                val from = num(evidence["from"])
                val to = num(evidence["to"])
                if (from != null && to != null && !eq(from, to)) {
                    out.add(ProvenanceEntry("$path.delta", abs(to - from), derived = "to - from"))
                }
            }
        }
        return out
    }

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    /**
     * Does one indexed value support this claim, and by which rule?
     *
     * The rules, all deterministic, all written down here because a rounding policy nobody wrote
     * down is a rounding policy that drifts:
     *
     *   exact       An integer in evidence must be quoted exactly. 6 supports "6" and nothing else.
     *   decimal     A non-integer may be quoted at its own precision or rounder, but never at zero
     *               decimals — 0.38 supports "0.38" and "0.4", and must NOT support "0", which is
     *               what unrestricted rounding would allow.
     *   percent     A value may be written as a percentage of itself: 0.38 → "38%", 0.333… → "33%"
     *               or "33.3%". Rounding is half-up at the precision the claim was written to.
     *               "40%" from 0.38 fails, because two-significant-figure rounding is not a rule
     *               anyone declared.
     */
    private fun rule(value: Double, claim: Claim): String? {
        if (claim.kind == ClaimKind.PERCENTAGE) {
            return if (eq(round(value * 100, claim.decimals), claim.value)) "percent" else null
        }
        if (eq(value, Math.floor(value))) return if (eq(value, claim.value)) "exact" else null
        // Non-integer: the claim must carry at least one decimal place of its own, so that rounding
        // cannot collapse a real quantity to a number that says something else.
        return if (claim.decimals >= 1 && eq(round(value, claim.decimals), claim.value)) "decimal"
        else null
    }

    /**
     * Check one claim against the whole index.
     *
     * A recommendation is not checked at all. "Try three sets of five next time" proposes numbers; it
     * does not report them, and requiring a proposal to appear in the evidence would reject correct
     * advice. This exemption is the single biggest hole a determined model could walk through, and it
     * is why the structured path classifies by FIELD rather than by the sentence's wording.
     */
    fun checkClaim(claim: Claim, index: List<ProvenanceEntry>): CheckedClaim {
        if (claim.claimed == Claimed.RECOMMENDATION) {
            return CheckedClaim(claim, ClaimStatus.EXEMPT, why = "a suggested number, not a report")
        }
        for (entry in index) {
            val by = rule(entry.value, claim)
            if (by != null) return CheckedClaim(claim, ClaimStatus.SUPPORTED, entry.id, by)
        }
        return CheckedClaim(claim, ClaimStatus.UNSUPPORTED)
    }

    /**
     * A structured answer, where the class of each statement is known rather than guessed.
     *
     * This is the strong path, and the reason the explain flow asks for JSON. `observed` and
     * `meaning` are statements about what happened and must be traceable; `suggestion` proposes
     * something and is exempt. No word list, no sentence heuristics, no ambiguity about which is
     * which — the model put each sentence in a labelled box and the label is what decides.
     */
    fun checkAnswer(answer: ModelAnswer?, index: List<ProvenanceEntry>): ClaimVerdict {
        val parts = buildList {
            answer?.observed?.forEach { addAll(Claims.extract(it, Claimed.OBSERVED)) }
            addAll(Claims.extract(answer?.meaning, Claimed.INFERENCE))
            addAll(Claims.extract(answer?.suggestion, Claimed.RECOMMENDATION))
        }
        return verdict(parts.map { checkClaim(it, index) })
    }

    /** Free-text answer: classify a sentence at a time, then check. The conversational path. */
    fun checkProse(text: String?, index: List<ProvenanceEntry>): ClaimVerdict =
        verdict(Claims.extractProse(text).map { checkClaim(it, index) })

    private fun verdict(checked: List<CheckedClaim>): ClaimVerdict {
        val unsupported = checked.filter { it.status == ClaimStatus.UNSUPPORTED }
        return ClaimVerdict(
            ok = unsupported.isEmpty(),
            checked = checked,
            unsupported = unsupported,
            feedback = if (unsupported.isEmpty()) null else
                "These numbers are not in the evidence you were given: " +
                    unsupported.joinToString(", ") { it.claim.raw } +
                    ". Use only numbers that appear there, or say the figure is not available.",
        )
    }
}
