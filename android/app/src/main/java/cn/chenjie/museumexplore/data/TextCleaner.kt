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
    private val mwCss = Regex("""\.mw-parser-output[^{}]*\{[^{}]*\}""")
    private val mwCssStart = Regex("""\.mw-parser-output""")
    private val cjk = Regex("[\\u3400-\\u9fff]")
    private val zeroWidth = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")
    private val coordDms = Regex("""-?\d{1,3}°\d{1,2}′\d{1,2}(?:\.\d+)?″[NSEW]""")
    private val coordDec = Regex("""-?\d{1,3}(?:\.\d+)?°[NSEW]""")
    private val coordSemi = Regex("""-?\d{1,3}(?:\.\d+)?\s*;\s*-?\d{1,3}(?:\.\d+)?\s*(?=\s|$)""")
    private val slopeSlash = Regex("""\s+/\s+""")
    private val slashPart = Regex("""(?:^|[\s，。；：、])/""")
    private val footnote = Regex(
        "\\[\\s*(?:[A-Za-z]|\\d+|(?:注|註|网|網|书|書|來源請求|来源请求|需要引用|需要引证))\\s*\\d*\\s*\\]",
    )
    private val styleLeftover = Regex(
        """(?:font-family|font-size|font-weight|line-height|display|margin|padding|background|border|white-space|list-style)\s*:|\{[^{}]*\}|color\s*:\s*#[0-9a-fA-F]{3,8}\b""",
        RegexOption.IGNORE_CASE,
    )
    private val cjkSpace = Regex("""([\u3400-\u9fff，。！？；：、“”《》])[ \t]+([\u3400-\u9fff，。！？；：、“”《》])""")

    fun clean(raw: String?): String {
        if (raw == null || raw.isBlank()) return ""
        var text: String = raw
        var previous: String
        text = scriptOrStyle.replace(text, " ")
        text = lineBreak.replace(text, "\n")
        text = tag.replace(text, "")
        text = decodeEntities(text)
        text = lineBreak.replace(text, "\n")
        text = tag.replace(text, "")
        text = citation.replace(text, "")
        text = removeMwCss(text)
        text = zeroWidth.replace(text, " ")
        text = coordDms.replace(text, " ")
        text = coordDec.replace(text, " ")
        text = coordSemi.replace(text, " ")
        text = slopeSlash.replace(text, " ")
        text = footnote.replace(text, " ")
        text = styleLeftover.replace(text, " ")
        text = horizontalSpace.replace(text, " ")
        text = slashPart.replace(text, " ")
        do {
            previous = text
            text = cjkSpace.replace(text, "$1$2")
        } while (previous != text)
        text = spaceBeforePunctuation.replace(text, "$1")
        text = Regex(" {2,}").replace(text, " ")
        text = Regex("([。！？；])\\1+").replace(text, "$1")
        text = Regex("(?<=[。！？；])[ \\t]+(?=[^\\n])").replace(text, "")
        text = Regex("^[，。！？；：、,.!?;:]+\\s*").replace(text, "")
        text = Regex("\\s*（\\s*").replace(text, "（")
        text = Regex("\\s*）").replace(text, "）")
        text = blankLinePadding.replace(text, "\n")
        text = tooManyBlankLines.replace(text, "\n\n")
        return text.lines().joinToString("\n") { it.trim() }.trim()
    }

    private fun removeMwCss(raw: String): String {
        var text = raw
        var previous: String
        do {
            previous = text
            text = mwCss.replace(text, " ")
        } while (previous != text)
        while (true) {
            val start = mwCssStart.find(text) ?: return text
            var end = text.length
            var index = start.range.last + 1
            while (index < text.length - 4) {
                if (text.substring(index, index + 5).count { cjk.matches(it.toString()) } == 5) {
                    end = index
                    break
                }
                index++
            }
            text = text.substring(0, start.range.first) + " " + text.substring(end)
        }
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
