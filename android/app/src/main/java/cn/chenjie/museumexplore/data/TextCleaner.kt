package cn.chenjie.museumexplore.data

/**
 * Data imported from Wikipedia and official sites occasionally contains HTML
 * fragments, entities and citation markers. Keep display text plain and tidy.
 */
object TextCleaner {
    private val scriptOrStyle = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>")
    private val lineBreak = Regex("(?i)<br\\s*/?>|</p\\s*>")
    private val tag = Regex("(?is)<[^>]+>")
    private val numericEntity = Regex("&#(x?[0-9a-fA-F]+);")
    private val citation = Regex("\\[\\s*\\d+\\s*\\]")
    private val horizontalSpace = Regex("[\\t\\x0B\\f\\r\\u00A0\\u2007\\u202F]+")
    private val spaceBeforePunctuation = Regex("\\s+([，。！？；：、,.!?;:])")
    private val tooManyBlankLines = Regex("\\n\\s*\\n+")
    private val blankLinePadding = Regex("[ \\t]+\\n")

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw
        text = scriptOrStyle.replace(text, " ")
        text = lineBreak.replace(text, "\n")
        text = tag.replace(text, "")
        text = decodeEntities(text)
        text = lineBreak.replace(text, "\n")
        text = tag.replace(text, "")
        text = citation.replace(text, "")
        text = spaceBeforePunctuation.replace(text, "$1")
        text = horizontalSpace.replace(text, " ")
        text = blankLinePadding.replace(text, "\n")
        text = tooManyBlankLines.replace(text, "\n\n")
        return text.lines().joinToString("\n") { it.trim() }.trim()
    }

    private fun decodeEntities(raw: String): String {
        var text = raw
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&middot;", "·")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&lsquo;", "‘")
            .replace("&rsquo;", "’")
        text = numericEntity.replace(text) { match ->
            val token = match.groupValues[1]
            val codePoint = if (token.startsWith("x", ignoreCase = true)) {
                token.drop(1).toIntOrNull(16)
            } else {
                token.toIntOrNull()
            }
            codePoint?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
        }
        return text
    }
}
