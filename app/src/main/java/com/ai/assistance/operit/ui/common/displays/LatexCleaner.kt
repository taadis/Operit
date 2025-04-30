package com.ai.assistance.operit.ui.common.displays

import android.util.Log

/**
 * Utility class for cleaning and fixing LaTeX expressions.
 * Handles common formatting issues and structural problems in LaTeX syntax.
 */
object LatexCleaner {

    /**
     * Balances \left and \right commands in LaTeX
     */
    fun balanceLeftRightCommands(latex: String): String {
        // Check for mismatched \left and \right commands
        val leftCount = "\\\\left".toRegex().findAll(latex).count()
        val rightCount = "\\\\right".toRegex().findAll(latex).count()
        
        var balanced = latex
        
        if (leftCount > rightCount) {
            // Excess \left, add missing \right
            for (i in 1..(leftCount - rightCount)) {
                balanced += " \\right."
            }
            Log.d("MarkdownLatex", "Fixed mismatched \\left commands, added ${leftCount - rightCount} \\right.")
        } else if (rightCount > leftCount) {
            // Excess \right, try to fix them
            // If there are standalone \right, replace them or add corresponding \left
            val pattern = "(^|[^\\\\])\\\\right".toRegex()
            val matches = pattern.findAll(balanced).toList()
            
            // Only handle excess \right
            val toReplace = matches.take(rightCount - leftCount)
            
            // Create StringBuilder for multiple replacements
            val builder = StringBuilder(balanced)
            
            // Replace from back to front to avoid index issues
            for (match in toReplace.reversed()) {
                // Get match range (excluding first capture group)
                val startIndex = match.range.first + match.groupValues[1].length
                val endIndex = startIndex + 6 // Length of "\\right"
                
                // Insert \left. before the original position
                builder.insert(startIndex, "\\left. ")
            }
            
            balanced = builder.toString()
            Log.d("MarkdownLatex", "Fixed mismatched \\right commands, added ${rightCount - leftCount} \\left.")
        }
        
        // Handle standalone \right commands (not in \right. or other valid forms)
        val standaloneRight = "\\\\right([^\\.]|$)".toRegex()
        balanced = standaloneRight.replace(balanced) { matchResult ->
            // Replace \right with \right. to ensure validity
            "\\right." + matchResult.groupValues[1]
        }
        
        return balanced
    }
    
    /**
     * Attempts to repair LaTeX expressions based on error messages
     */
    fun repairLatexExpression(latex: String, errorMessage: String): String {
        var repaired = latex
        
        // Handle common error types
        when {
            // Handle unknown symbol or command errors
            errorMessage.contains("Unknown symbol or command") -> {
                val errorPattern = "Unknown symbol or command or predefined TeXFormula: '(.*?)'".toRegex()
                val match = errorPattern.find(errorMessage)
                
                if (match != null) {
                    val problematicCommand = match.groupValues[1]
                    
                    // Fix based on specific problem
                    when (problematicCommand) {
                        "right" -> {
                            // Fix standalone \right command
                            if (repaired.contains("\\right") && !repaired.contains("\\left")) {
                                // Add matching \left.
                                repaired = "\\left. " + repaired
                                Log.d("MarkdownLatex", "Added missing \\left command to match \\right")
                            }
                            // Ensure \right has valid delimiter
                            repaired = repaired.replace("\\right([^\\s.\\)\\]\\}]|$)".toRegex()) { 
                                "\\right." + (it.groupValues[1].takeIf { it.isNotEmpty() } ?: "")
                            }
                        }
                        "left" -> {
                            // Fix standalone \left command
                            if (repaired.contains("\\left") && !repaired.contains("\\right")) {
                                // Add matching \right.
                                repaired = repaired + " \\right."
                                Log.d("MarkdownLatex", "Added missing \\right command to match \\left")
                            }
                            // Ensure \left has valid delimiter
                            repaired = repaired.replace("\\left([^\\s.\\(\\[\\{]|$)".toRegex()) { 
                                "\\left." + (it.groupValues[1].takeIf { it.isNotEmpty() } ?: "")
                            }
                        }
                        else -> {
                            // Remove other unknown commands
                            repaired = repaired.replace("\\\\$problematicCommand".toRegex(), "")
                            Log.d("MarkdownLatex", "Removed unknown command: \\$problematicCommand")
                        }
                    }
                }
            }
            
            // Handle other error types
            errorMessage.contains("ParseException") -> {
                // Apply general fixes
                repaired = balanceLeftRightCommands(repaired)
                
                // Check other common errors, like missing braces
                repaired = repaired.replace("\\\\frac([^{]|$)".toRegex()) { 
                    "\\frac{" + it.groupValues[1] + "}{}"
                }
            }
        }
        
        return repaired
    }
} 