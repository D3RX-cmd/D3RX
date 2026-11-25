package com.example.data.remote

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

data class ImportedRecipeDraft(
    val title: String,
    val description: String,
    val sourceUrl: String,
    val sourceName: String,
    val imageUrl: String?,
    val prepMinutes: Long,
    val cookMinutes: Long,
    val servings: Long,
    val ingredients: List<String>,
    val steps: List<String>,
    val tags: List<String>,
    val extractedStructuredData: Boolean
)

class RecipeWebImporter {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun importFromUrl(rawUrl: String): Result<ImportedRecipeDraft> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeHttpUrl(rawUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Enter a valid http:// or https:// recipe URL."))

            val uri = URI(normalizedUrl)
            val host = uri.host?.removePrefix("www.") ?: "Web"

            val request = Request.Builder()
                .url(normalizedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Website returned HTTP ${response.code}. Try copying the recipe manually.")
                    )
                }
                response.body?.string().orEmpty()
            }

            if (html.isBlank()) {
                return@withContext Result.failure(IllegalStateException("The webpage returned empty content."))
            }

            val jsonLdRecipe = extractRecipeJsonLd(html)
            if (jsonLdRecipe != null) {
                val draft = buildDraftFromJsonLd(jsonLdRecipe, normalizedUrl, host, html)
                return@withContext Result.success(draft)
            }

            // Fallback to OpenGraph / meta tags
            val ogTitle = extractMetaContent(html, "og:title")
                ?: extractHtmlTitle(html)
                ?: "Imported Recipe"
            val ogDesc = extractMetaContent(html, "og:description")
                ?: extractMetaContent(html, "description")
                ?: ""
            val ogImage = extractMetaContent(html, "og:image")

            Result.success(
                ImportedRecipeDraft(
                    title = cleanHtmlText(ogTitle).take(140),
                    description = cleanHtmlText(ogDesc).take(1000),
                    sourceUrl = normalizedUrl,
                    sourceName = host.take(100),
                    imageUrl = ogImage?.trim()?.takeIf { it.startsWith("http") }?.take(1000),
                    prepMinutes = 15L,
                    cookMinutes = 20L,
                    servings = 4L,
                    ingredients = emptyList(),
                    steps = emptyList(),
                    tags = listOf("Online"),
                    extractedStructuredData = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeHttpUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else if (!trimmed.contains("://") && trimmed.contains(".")) {
            "https://$trimmed"
        } else {
            return null
        }

        return try {
            val uri = URI(withScheme)
            if (uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) {
                withScheme
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractRecipeJsonLd(html: String): JSONObject? {
        val scriptRegex = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE
        )
        for (match in scriptRegex.findAll(html)) {
            val rawJson = match.groupValues[1].trim()
            if (rawJson.isEmpty()) continue
            val found = parseJsonForRecipe(rawJson)
            if (found != null) return found
        }
        return null
    }

    private fun parseJsonForRecipe(rawJson: String): JSONObject? {
        return try {
            if (rawJson.startsWith("[")) {
                findRecipeInArray(JSONArray(rawJson))
            } else {
                val obj = JSONObject(rawJson)
                if (isRecipeType(obj)) {
                    obj
                } else if (obj.has("@graph")) {
                    val graph = obj.optJSONArray("@graph")
                    if (graph != null) findRecipeInArray(graph) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findRecipeInArray(array: JSONArray): JSONObject? {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (isRecipeType(item)) return item
            if (item.has("@graph")) {
                val nested = item.optJSONArray("@graph")?.let { findRecipeInArray(it) }
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun isRecipeType(obj: JSONObject): Boolean {
        val typeVal = obj.opt("@type") ?: return false
        return when (typeVal) {
            is String -> typeVal.equals("Recipe", ignoreCase = true)
            is JSONArray -> (0 until typeVal.length()).any {
                typeVal.optString(it).equals("Recipe", ignoreCase = true)
            }
            else -> false
        }
    }

    private fun buildDraftFromJsonLd(
        obj: JSONObject,
        url: String,
        host: String,
        html: String
    ): ImportedRecipeDraft {
        val title = cleanHtmlText(obj.optString("name"))
            .ifBlank { cleanHtmlText(extractMetaContent(html, "og:title") ?: extractHtmlTitle(html) ?: "Imported Recipe") }
            .take(140)
        val description = cleanHtmlText(obj.optString("description"))
            .ifBlank { cleanHtmlText(extractMetaContent(html, "og:description").orEmpty()) }
            .take(1000)

        val imageUrl = extractImageUrl(obj.opt("image"))
            ?: extractMetaContent(html, "og:image")

        val prepMinutes = parseIsoDurationMinutes(obj.optString("prepTime"))
        val cookMinutes = parseIsoDurationMinutes(obj.optString("cookTime"))
        val totalMinutes = parseIsoDurationMinutes(obj.optString("totalTime"))
        val finalPrep = if (prepMinutes > 0) prepMinutes else if (totalMinutes > 0 && cookMinutes == 0L) totalMinutes / 3 else 15L
        val finalCook = if (cookMinutes > 0) cookMinutes else if (totalMinutes > finalPrep) totalMinutes - finalPrep else 20L

        val servings = parseServings(obj.opt("recipeYield"))

        val ingredients = parseStringList(obj.opt("recipeIngredient")).take(100)
        val steps = parseInstructions(obj.opt("recipeInstructions")).take(100)

        val rawTags = mutableListOf<String>()
        rawTags.addAll(parseTagField(obj.opt("recipeCategory")))
        rawTags.addAll(parseTagField(obj.opt("recipeCuisine")))
        rawTags.addAll(parseTagField(obj.opt("keywords")))
        val cleanTags = rawTags
            .map { cleanHtmlText(it).trim() }
            .filter { it.length in 2..28 }
            .distinctBy { it.lowercase() }
            .take(8)

        return ImportedRecipeDraft(
            title = title,
            description = description,
            sourceUrl = url,
            sourceName = host.take(100),
            imageUrl = imageUrl?.trim()?.takeIf { it.startsWith("http") }?.take(1000),
            prepMinutes = finalPrep.coerceIn(0L, 1440L),
            cookMinutes = finalCook.coerceIn(0L, 1440L),
            servings = servings.coerceIn(1L, 100L),
            ingredients = ingredients,
            steps = steps,
            tags = cleanTags.ifEmpty { listOf("Online") },
            extractedStructuredData = ingredients.isNotEmpty() || steps.isNotEmpty()
        )
    }

    private fun extractImageUrl(value: Any?): String? {
        return when (value) {
            is String -> value
            is JSONObject -> value.optString("url").takeIf { it.isNotBlank() }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val url = extractImageUrl(value.opt(i))
                    if (!url.isNullOrBlank()) return url
                }
                null
            }
            else -> null
        }
    }

    private fun parseStringList(value: Any?): List<String> {
        return when (value) {
            is JSONArray -> (0 until value.length())
                .mapNotNull { value.optString(it)?.let(::cleanHtmlText)?.takeIf { s -> s.isNotBlank() } }
            is String -> value.lines().map(::cleanHtmlText).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun parseInstructions(value: Any?): List<String> {
        val result = mutableListOf<String>()
        fun collect(node: Any?) {
            when (node) {
                is String -> {
                    node.lines().map(::cleanHtmlText).filter { it.isNotBlank() }.forEach { result.add(it) }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        collect(node.opt(i))
                    }
                }
                is JSONObject -> {
                    val type = node.optString("@type")
                    if (type.equals("HowToSection", ignoreCase = true) && node.has("itemListElement")) {
                        collect(node.opt("itemListElement"))
                    } else {
                        val text = cleanHtmlText(node.optString("text"))
                            .ifBlank { cleanHtmlText(node.optString("name")) }
                        if (text.isNotBlank()) result.add(text)
                        if (node.has("itemListElement")) {
                            collect(node.opt("itemListElement"))
                        }
                    }
                }
            }
        }
        collect(value)
        return result
    }

    private fun parseTagField(value: Any?): List<String> {
        return when (value) {
            is String -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            is JSONArray -> (0 until value.length()).flatMap {
                value.optString(it).split(",").map { part -> part.trim() }.filter { part -> part.isNotEmpty() }
            }
            else -> emptyList()
        }
    }

    private fun parseIsoDurationMinutes(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        val regex = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(iso.trim()) ?: return 0L
        val days = match.groupValues[1].toLongOrNull() ?: 0L
        val hours = match.groupValues[2].toLongOrNull() ?: 0L
        val mins = match.groupValues[3].toLongOrNull() ?: 0L
        return days * 1440L + hours * 60L + mins
    }

    private fun parseServings(value: Any?): Long {
        val raw = when (value) {
            is Number -> return value.toLong().coerceIn(1L, 100L)
            is String -> value
            is JSONArray -> value.optString(0)
            else -> ""
        }
        val num = Regex("""\d+""").find(raw)?.value?.toLongOrNull()
        return (num ?: 4L).coerceIn(1L, 100L)
    }

    private fun extractMetaContent(html: String, key: String): String? {
        val patterns = listOf(
            Regex("""<meta[^>]+(?:property|name)=["']$key["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$key["']""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val match = p.find(html)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun extractHtmlTitle(html: String): String? {
        val match = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE).find(html)
        return match?.groupValues?.getOrNull(1)
    }

    private fun cleanHtmlText(raw: String): String {
        if (raw.isBlank()) return ""
        val decoded = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        return decoded.replace(Regex("""\s+"""), " ").trim()
    }
}
