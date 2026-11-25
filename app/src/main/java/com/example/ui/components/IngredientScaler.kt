package com.example.ui.components

import com.example.data.model.Recipe
import kotlin.math.abs
import kotlin.math.roundToInt

object IngredientScaler {
    private val unicodeFractions = mapOf(
        '¼' to 0.25,
        '½' to 0.5,
        '¾' to 0.75,
        '⅓' to 1.0 / 3.0,
        '⅔' to 2.0 / 3.0,
        '⅛' to 0.125,
        '⅜' to 0.375,
        '⅝' to 0.625,
        '⅞' to 0.875
    )

    private val leadingNumberRegex = Regex(
        """^\s*(\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?|[¼½¾⅓⅔⅛⅜⅝⅞]|\d+\s*[¼½¾⅓⅔⅛⅜⅝⅞])(.*)$"""
    )

    fun scaleIngredient(line: String, factor: Double): String {
        if (abs(factor - 1.0) < 0.01) return line
        val match = leadingNumberRegex.find(line) ?: return line
        val qtyToken = match.groupValues[1].trim()
        val remainder = match.groupValues[2]
        val numeric = parseQuantity(qtyToken) ?: return line
        val scaled = numeric * factor
        return formatQuantity(scaled) + remainder
    }

    private fun parseQuantity(token: String): Double? {
        val trimmed = token.trim()
        // Mixed integer + unicode fraction e.g. "1½" or "1 ½"
        val lastChar = trimmed.lastOrNull()
        if (lastChar != null && unicodeFractions.containsKey(lastChar)) {
            val frac = unicodeFractions.getValue(lastChar)
            val prefix = trimmed.dropLast(1).trim()
            return if (prefix.isEmpty()) frac else (prefix.toDoubleOrNull()?.plus(frac))
        }
        // Mixed number e.g. "1 1/2"
        val spaceParts = trimmed.split(Regex("""\s+"""))
        if (spaceParts.size == 2 && spaceParts[1].contains('/')) {
            val whole = spaceParts[0].toDoubleOrNull() ?: return null
            val frac = parseFraction(spaceParts[1]) ?: return null
            return whole + frac
        }
        // Simple fraction e.g. "1/2"
        if (trimmed.contains('/')) {
            return parseFraction(trimmed)
        }
        return trimmed.toDoubleOrNull()
    }

    private fun parseFraction(raw: String): Double? {
        val parts = raw.split('/')
        if (parts.size != 2) return null
        val num = parts[0].toDoubleOrNull() ?: return null
        val den = parts[1].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        return num / den
    }

    private fun formatQuantity(value: Double): String {
        val whole = value.toInt()
        val frac = value - whole
        val nearestFraction = listOf(
            0.0 to "",
            0.125 to "⅛",
            0.25 to "¼",
            1.0 / 3.0 to "⅓",
            0.375 to "⅜",
            0.5 to "½",
            0.625 to "⅝",
            2.0 / 3.0 to "⅔",
            0.75 to "¾",
            0.875 to "⅞",
            1.0 to ""
        ).minByOrNull { abs(frac - it.first) }

        if (nearestFraction != null && abs(frac - nearestFraction.first) < 0.04) {
            return when {
                nearestFraction.first == 1.0 -> (whole + 1).toString()
                whole == 0 && nearestFraction.second.isNotEmpty() -> nearestFraction.second
                nearestFraction.second.isNotEmpty() -> "$whole${nearestFraction.second}"
                else -> whole.toString()
            }
        }
        val rounded = (value * 10.0).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private val stopWords = setOf(
        "cup", "cups", "tbsp", "tablespoon", "tablespoons", "tsp", "teaspoon", "teaspoons",
        "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds", "g", "kg", "ml", "l",
        "clove", "cloves", "can", "cans", "pinch", "dash", "slice", "slices", "piece", "pieces",
        "fresh", "dried", "ground", "chopped", "diced", "minced", "sliced", "grated", "large",
        "small", "medium", "extra", "virgin", "kosher", "sea", "to", "taste", "for", "of", "and",
        "or", "the", "a", "an", "finely", "roughly", "peeled", "halved", "optional", "divided",
        "room", "temperature", "warm", "cold", "hot", "plus", "more", "with"
    )

    /**
     * Extracts clean, common ingredient keywords from saved recipes for 1-tap ingredient filtering.
     */
    fun extractTopPantryIngredients(recipes: List<Recipe>, limit: Int = 14): List<String> {
        val counts = mutableMapOf<String, Int>()
        val displayForm = mutableMapOf<String, String>()

        for (recipe in recipes) {
            val seenInRecipe = mutableSetOf<String>()
            for (line in recipe.ingredients) {
                val cleaned = line.lowercase()
                    .replace(Regex("""\([^)]*\)"""), " ")
                    .replace(Regex("""[0-9¼½¾⅓⅔⅛⅜⅝⅞/.,\-–]+"""), " ")
                    .split(",", ";")
                    .firstOrNull()
                    ?.trim()
                    .orEmpty()

                val words = cleaned.split(Regex("""\s+"""))
                    .filter { it.length >= 3 && it !in stopWords }

                val candidates = mutableListOf<String>()
                if (words.size >= 2) {
                    candidates.add(words.takeLast(2).joinToString(" "))
                }
                words.lastOrNull()?.let { candidates.add(it) }

                for (candidate in candidates) {
                    if (seenInRecipe.add(candidate)) {
                        counts[candidate] = (counts[candidate] ?: 0) + 1
                        displayForm.putIfAbsent(
                            candidate,
                            candidate.replaceFirstChar { c -> c.uppercase() }
                        )
                    }
                }
            }
        }

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .mapNotNull { displayForm[it.key] }
            .distinctBy { it.lowercase() }
            .take(limit)
    }

    fun matchingIngredients(recipe: Recipe, ingredientFilters: Set<String>): List<String> {
        if (ingredientFilters.isEmpty()) return emptyList()
        return recipe.ingredients.filter { line ->
            val lower = line.lowercase()
            ingredientFilters.any { filter -> lower.contains(filter.lowercase()) }
        }
    }
}
