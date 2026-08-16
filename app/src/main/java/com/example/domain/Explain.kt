package com.example.domain

object Explain {
    /**
     * Executes the explain decision path, applying the 1-retry, full-discard policy.
     * Real network implementation will inject a GeminiClient.
     */
    suspend fun explainDecision(
        evidence: Map<String, Any>,
        fetchModelResponse: suspend (feedback: String?) -> List<Claim>
    ): String {
        var attempt = 1
        var feedback: String? = null
        
        while (attempt <= Validate.MAX_ATTEMPTS) {
            val claims = try {
                fetchModelResponse(feedback)
            } catch (e: Exception) {
                // Network or parse error
                return fallbackExplanation()
            }
            
            val results = Validate.checkClaims(claims, evidence)
            val unsupported = results.filter { !it.isSupported }
            
            if (unsupported.isEmpty()) {
                // All claims supported (or empty) -> Success
                // In reality we would reassemble the string from the model,
                // but for this function we'll just return a success string.
                return claims.joinToString(" ") { it.value }
            }
            
            // Retry feedback
            feedback = "The following numbers are not supported by the evidence: ${unsupported.joinToString { it.claim.value }}"
            attempt++
        }
        
        // Exceeded attempts -> discard entirely and fallback to deterministic string
        return fallbackExplanation()
    }
    
    fun fallbackExplanation(): String {
        return "This decision was based on your recorded performance."
    }
}
