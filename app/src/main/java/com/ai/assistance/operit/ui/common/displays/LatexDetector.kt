package com.ai.assistance.operit.ui.common.displays

/**
 * Utility class for detecting LaTeX content within text.
 * Provides methods to analyze text and determine if it likely contains LaTeX expressions.
 */
object LatexDetector {
    
    /**
     * Enhanced version to check if content might be a LaTeX expression.
     * More precisely identifies LaTeX content, reducing false positives and negatives.
     */
    fun isLikelyLatexContent(content: String): Boolean {
        // If content is empty or too short, can't be LaTeX
        if (content.isBlank() || content.length < 2) {
            return false
        }
        
        // Check if it's a Markdown link or image
        // If content contains URL format characters, likely a link rather than LaTeX
        if (content.contains("http") || content.contains("www.") || content.contains(".com") || 
            content.contains(".org") || content.contains(".net") || content.contains(".io")) {
            return false
        }
        
        // If it contains obvious LaTeX command prefixes, almost certainly is LaTeX
        if (content.contains("\\")) {
            // Check common LaTeX commands
            val commonLatexCommands = listOf(
                "\\frac", "\\sqrt", "\\sum", "\\int", "\\prod", 
                "\\mathbf", "\\mathrm", "\\mathcal", "\\text", 
                "\\begin", "\\end", "\\left", "\\right",
                "\\alpha", "\\beta", "\\gamma", "\\delta", "\\theta", "\\pi",
                "\\infty", "\\partial", "\\nabla", "\\times", "\\div"
            )
            
            for (cmd in commonLatexCommands) {
                if (content.contains(cmd)) {
                    return true
                }
            }
        }
        
        // Check math symbols and structures
        val mathStructures = listOf(
            "{", "}", // Braces
            "^", "_",  // Super and subscripts
            "=", "+", "-", "*", "/", ">", "<", // Basic operators
            "\\leq", "\\geq", "\\neq", "\\approx", // Math relations
            "\\cdot", "\\bullet", "\\circ", // Math symbols
            "\\to", "\\rightarrow", "\\leftarrow" // Arrows
        )
        
        // Check if it contains math features - need at least two features to confirm as LaTeX
        var mathFeatureCount = 0
        for (structure in mathStructures) {
            if (content.contains(structure)) {
                mathFeatureCount++
                if (mathFeatureCount >= 2) { 
                    return true
                }
            }
        }
        
        // Check math formula characteristic patterns
        val mathPatterns = listOf(
            Regex("[a-zA-Z]_\\{[^\\}]+\\}"), // Subscript: x_{...}
            Regex("[a-zA-Z]\\^\\{[^\\}]+\\}"), // Superscript: x^{...}
            Regex("[a-zA-Z]_[0-9a-zA-Z]"), // Simple subscript: x_i
            Regex("[a-zA-Z]\\^[0-9a-zA-Z]"), // Simple superscript: x^2
            Regex("\\{[^\\}]+\\}"), // Braces: {...}
            Regex("[a-zA-Z]'") // Derivative symbol: f'
        )
        
        for (pattern in mathPatterns) {
            if (pattern.containsMatchIn(content)) {
                return true
            }
        }
        
        // If content contains common math variable naming styles (single lowercase letter with super/subscripts)
        // For example: x_1, y^2, z_{max}
        if (Regex("[a-z]_[0-9a-zA-Z]").containsMatchIn(content) || 
            Regex("[a-z]\\^[0-9a-zA-Z]").containsMatchIn(content)) {
            return true
        }
        
        // Check if it contains common fraction forms
        if (content.contains("/") && !content.contains("//") && !content.contains("http")) {
            val parts = content.split("/")
            if (parts.size == 2 && parts[0].trim().isNotEmpty() && parts[1].trim().isNotEmpty()) {
                // If it's a simple fraction form, like "a/b"
                return true
            }
        }
        
        // Not likely a LaTeX expression
        return false
    }
} 