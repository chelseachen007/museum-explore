package cn.chenjie.museumexplore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.chenjie.museumexplore.model.GuideSection
import cn.chenjie.museumexplore.model.MuseumGuide

/**
 * 「博物馆怎么逛」目录页。
 *
 * 设计取向：这是**进场前十分钟才会打开**的东西，所以首页只给目录和一句话摘要，
 * 不把 4800 字全铺出来。用户要的是「我现在该看哪一节」，不是从头读到尾。
 *
 * 生僻字速查默认折叠：它是查的时候用的，不是读的时候用的。
 */
@Composable
fun MuseumGuideScreen(
    guide: MuseumGuide,
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GuideHeader(guide, onBack)
            guide.grouped().forEach { group ->
                GuideGroupHeader(group.title)
                group.sections.forEach { section ->
                    GuideSectionRow(section, onOpen = { onOpenSection(section.id) })
                }
            }
            if (guide.glossary.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                GlossaryCard(guide)
            }
            if (guide.sources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                GuideSources(guide)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 单节正文页。
 *
 * 正文直接交给 [MarkdownBody] 渲染——和文物详情共用同一套解析与样式，
 * 保证两处的小标题层级、列表缩进、行高完全一致。
 */
@Composable
fun GuideSectionScreen(
    guide: MuseumGuide,
    section: GuideSection,
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
) {
    val index = guide.sections.indexOfFirst { it.id == section.id }
    val next = guide.sections.getOrNull(index + 1)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GuideHeader(guide, onBack, title = section.title, subtitle = section.summary)
            Column(Modifier.padding(horizontal = 22.dp)) {
                MarkdownBody(RelicMarkdown.parse(section.body))
            }
            if (next != null) {
                Spacer(Modifier.height(18.dp))
                Card(
                    onClick = { onOpenSection(next.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("下一节", style = MaterialTheme.typography.labelMedium)
                            Text(next.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun GuideHeader(
    guide: MuseumGuide,
    onBack: () -> Unit,
    title: String = guide.title,
    subtitle: String = guide.subtitle,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("‹ 返回", color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "FIELD GUIDE · 观展手册",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (guide.intro.isNotBlank() && title == guide.title) {
            Text(
                guide.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .7f))
    }
}

/**
 * 目录里的分组小标题。
 *
 * 不做成可折叠的：手册总共十几节，默认全折叠的目录等于让用户多点十几下。
 */
@Composable
private fun GuideGroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 22.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun GuideSectionRow(section: GuideSection, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (section.summary.isNotBlank()) {
                    Text(section.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

/**
 * 生僻字速查。
 *
 * 用两列网格而不是表格：表格在窄屏上会挤成一列字加一列拼音，读拼音时眼睛要横跳。
 * 两列时每组天然对齐，扫一眼就能找到要查的那个字。
 */
@Composable
private fun GlossaryCard(guide: MuseumGuide) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("生僻字速查", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (expanded) "点一下收起" else "展柜铭牌上的字八成都在这里 · 共 ${guide.glossary.sumOf { it.items.size }} 条",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    guide.glossary.forEach { group ->
                        Text(
                            group.group,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                        // 每行两条：条目数不保证是偶数，最后一行单条时右侧留空，不做拉伸。
                        group.items.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                pair.forEach { item ->
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(item.char, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                item.pinyin,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        if (item.meaning.isNotBlank()) {
                                            Text(
                                                item.meaning,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        // hint 讲「它落在哪一门知识里」，比读音更能回答「这是什么字」。
                                        if (item.hint.isNotBlank()) {
                                            Text(
                                                item.hint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = .85f),
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSources(guide: MuseumGuide) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (guide.updatedAt.isNotBlank()) "资料来源 · 更新于 ${guide.updatedAt}" else "资料来源",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            guide.sources.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
