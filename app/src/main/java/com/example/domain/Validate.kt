package com.example.domain

import kotlin.math.abs
import kotlin.math.roundToInt

object Validate {
    const val MAX_ATTEMPTS = 2

    // Rules: exact, decimal, percent
    fun checkClaims(claims: List<Claim>, evidence: Map<String, Any>): List<ClaimResult> {
        val provenance = buildProvenance(evidence)
        
        return claims.map { claim ->
            if (claim.type == "recommendation") {
                ClaimResult(claim, isSupported = true, reason = "recommendation is exempt")
            } else {
                val matches = provenance.any { p ->
                    val pNum = p.toDoubleOrNull()
                    val cNum = claim.value.toDoubleOrNull()
                    if (pNum == null || cNum == null) {
                        p == claim.value
                    } else {
                        when (claim.rule) {
                            "exact" -> abs(pNum - cNum) < 0.001
                            "decimal" -> abs(pNum - cNum) < 0.1
                            "percent" -> abs(pNum * 100 - cNum) < 1.0 || abs(pNum - cNum) < 0.01 // In case percent is passed as 0.8
                            else -> abs(pNum - cNum) < 0.001
                        }
                    }
                }
                ClaimResult(claim, isSupported = matches, reason = if (matches) "matched provenance" else "unsupported")
            }
        }
    }

    private fun buildProvenance(evidence: Map<String, Any>, prefix: String = ""): List<String> {
        val list = mutableListOf<String>()
        for ((k, v) in evidence) {
            when (v) {
                is Number -> list.add(v.toString())
                is String -> if (v.toDoubleOrNull() != null) list.add(v)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    list.addAll(buildProvenance(v as Map<String, Any>, "$prefix$k."))
                    
                    // specific rule: |to - from|
                    if (v.containsKey("to") && v.containsKey("from")) {
                        val to = (v["to"] as? Number)?.toDouble() ?: (v["to"] as? String)?.toDoubleOrNull()
                        val from = (v["from"] as? Number)?.toDouble() ?: (v["from"] as? String)?.toDoubleOrNull()
                        if (to != null && from != null) {
                            list.add(abs(to - from).toString())
                        }
                    }
                }
                is List<*> -> {
                    v.forEachIndexed { i, item ->
                        if (item is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            list.addAll(buildProvenance(item as Map<String, Any>, "$prefix$k[$i]."))
                        } else if (item is Number) {
                            list.add(item.toString())
                        }
                    }
                }
            }
        }
        return list
    }
}

data class Claim(
    val value: String,
    val type: String, // e.g. "recommendation", "fact"
    val rule: String  // e.g. "exact", "decimal", "percent"
)

data class ClaimResult(
    val claim: Claim,
    val isSupported: Boolean,
    val reason: String
)
