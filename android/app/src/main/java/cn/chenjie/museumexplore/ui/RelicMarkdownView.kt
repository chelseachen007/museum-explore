package cn.chenjie.museumexplore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 把 [MdBlock] 列表渲染成 Compose 段落。
 *
 * 用 [BasicText] 而不是 [androidx.compose.material3.Text]，是为了让「标题 / 段落 / 列表项」
 * 三种块各自控制行高与字重，同时共用同一份样式来源。样式全部取自 MaterialTheme，
 * 不硬编码字号，换主题时正文跟着一起变。
 */
@Composable
fun MarkdownBody(blocks: List<MdBlock>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1, 2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    }
                    BasicText(
                        text = annotated(block.text),
                        style = style.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                    )
                }
                is MdBlock.Paragraph -> {
                    BasicText(
                        text = annotated(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.7,
                        ),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                is MdBlock.BulletList -> {
                    block.items.forEachIndexed { index, item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            BasicText(
                                text = if (block.ordered) "${index + 1}." else "·",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicText(
                                text = annotated(item),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单段文本，用于「简介」「一句话」这类只有一段的场合。 */
@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier, style: TextStyle? = null) {
    val blocks = RelicMarkdown.parse(source)
    if (blocks.isEmpty()) return
    val resolved = style ?: MaterialTheme.typography.bodyLarge
    BasicText(
        text = annotated(RelicMarkdown.plainText(source)),
        style = resolved,
        modifier = modifier.semantics { contentDescription = RelicMarkdown.plainText(source) },
    )
}

private fun annotated(text: String): AnnotatedString = buildAnnotatedString {
    RelicMarkdown.inlineSpans(text).forEach { span ->
        val spanStyle = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
        )
        if (span.bold || span.italic) withStyle(spanStyle) { append(span.text) } else append(span.text)
    }
}
