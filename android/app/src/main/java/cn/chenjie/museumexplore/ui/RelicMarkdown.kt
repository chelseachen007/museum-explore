package cn.chenjie.museumexplore.ui

/**
 * 国宝详情正文用的轻量 Markdown 子集。
 *
 * 只支持写作时真正会用到的东西，不支持就不支持，不要为了「顺便」把渲染库引进来：
 * - `## 小标题` / `###` / `####` 三级标题
 * - 有序、无序列表（支持一层缩进续行）
 * - `**粗体**`、`*斜体*`
 * - 空行分段
 *
 * 之所以自己做而不引第三方 Markdown 库：正文是数据资产，格式必须由 [cn.chenjie.museumexplore.data]
 * 下的校验脚本和这里的解析规则一一对上。引一个通用库，校验通过的数据和渲染结果就对不齐了。
 *
 * 解析不抛异常：认不出的行一律当普通段落，宁可多显示，不能白屏。
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletList(val ordered: Boolean, val items: List<String>) : MdBlock
}

/** 行内样式：把 `**粗体**` / `*斜体*` 拆成片段，UI 层逐段拼 AnnotatedString。 */
data class InlineSpan(val text: String, val bold: Boolean, val italic: Boolean)

object RelicMarkdown {
    private val HEADING = Regex("^(#{1,4})\\s+(.*)$")
    private val BULLET = Regex("^[-*+]\\s+(.*)$")
    private val ORDERED = Regex("^\\d+[.)]\\s+(.*)$")

    /**
     * 把正文切成块。
     *
     * 容错约定（有单元测试兜着）：
     * - `#` 开头的行最多认四级标题，五级及以上按段落处理。
     * - 列表项之间夹的空行不打断列表；遇到标题或普通段落才结束列表。
     * - 空输入返回空列表，UI 层据此隐藏整块内容，而不是渲染一个空标题。
     */
    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()
        // 列表缓冲：同一个列表里连续的项先攒起来，遇到别的块类型再一次性提交。
        val listItems = mutableListOf<String>()
        var listOrdered = false

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
            paragraph.clear()
        }

        fun flushList() {
            if (listItems.isNotEmpty()) blocks += MdBlock.BulletList(listOrdered, listItems.toList())
            listItems.clear()
        }

        fun flushAll() {
            flushParagraph()
            flushList()
        }

        for (rawLine in source.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                // 空行只结束段落。列表里的空行常用来隔开「一小段说明 + 一组子项」，
                // 直接当列表结束会让同一份内容的排版结果随空行有无而变。
                flushParagraph()
                continue
            }
            val heading = HEADING.find(line)
            if (heading != null && heading.groupValues[1].length <= 4) {
                flushAll()
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                continue
            }
            val bullet = BULLET.find(line)
            if (bullet != null) {
                flushParagraph()
                if (listItems.isNotEmpty() && listOrdered) flushList()
                listOrdered = false
                listItems += bullet.groupValues[1].trim()
                continue
            }
            val ordered = ORDERED.find(line)
            if (ordered != null) {
                flushParagraph()
                if (listItems.isNotEmpty() && !listOrdered) flushList()
                listOrdered = true
                listItems += ordered.groupValues[1].trim()
                continue
            }
            // 普通文本行。列表进行中且这一行是缩进续行，就并回上一项，避免长句被硬换行切碎。
            if (listItems.isNotEmpty() && rawLine.startsWith("  ")) {
                listItems[listItems.lastIndex] = listItems.last() + line
                continue
            }
            flushList()
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line)
        }
        flushAll()
        return blocks
    }

    /**
     * 拆行内样式。
     *
     * 顺序很关键：先认 `**`，再认 `*`。反过来的话 `**粗体**` 会被当成「斜体 + 星号」。
     * 未闭合的标记原样保留，不让用户看到一个吞掉半句话的渲染结果。
     */
    fun inlineSpans(text: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        var index = 0
        val buffer = StringBuilder()

        fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                spans += InlineSpan(buffer.toString(), bold = false, italic = false)
                buffer.clear()
            }
        }

        while (index < text.length) {
            if (text.startsWith("**", index)) {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    flushBuffer()
                    spans += InlineSpan(text.substring(index + 2, end), bold = true, italic = false)
                    index = end + 2
                    continue
                }
            }
            if (text[index] == '*') {
                val end = text.indexOf('*', index + 1)
                if (end > index + 1) {
                    flushBuffer()
                    spans += InlineSpan(text.substring(index + 1, end), bold = false, italic = true)
                    index = end + 1
                    continue
                }
            }
            buffer.append(text[index])
            index++
        }
        flushBuffer()
        return spans
    }

    /** 去掉所有标记后的纯文本，用于无障碍朗读和「分享摘要」这类不能带符号的场合。 */
    fun plainText(source: String): String = parse(source).joinToString("\n\n") { block ->
        when (block) {
            is MdBlock.Heading -> stripInline(block.text)
            is MdBlock.Paragraph -> stripInline(block.text)
            is MdBlock.BulletList -> block.items.joinToString("\n") { "· ${stripInline(it)}" }
        }
    }.trim()

    private fun stripInline(text: String): String = inlineSpans(text).joinToString("") { it.text }
}
