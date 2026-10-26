package cn.chenjie.museumexplore.model

/**
 * 「博物馆怎么逛」指南。
 *
 * 内容是完全静态的通用知识，和具体文物、具体博物馆都无关，所以不塞进 [MuseumRepository]
 * 的探索状态里——它不需要版本迁移，也不会被用户改。改内容改资产文件 `museum_guide.json` 就行。
 *
 * 来源与时效写在 [sources] 里并原样展示给用户：预约规则这类东西一年能变三回，
 * 不标来源和时间，用户会当成当前有效的官方说法。
 */
data class MuseumGuide(
    val title: String,
    val subtitle: String,
    val intro: String,
    val updatedAt: String,
    val sections: List<GuideSection>,
    val glossary: List<GuideGlossaryGroup>,
    val sources: List<String>,
) {
    val isEmpty: Boolean get() = sections.isEmpty()

    /** 目录用：只保留标题和一句话摘要，不带着正文到处传。 */
    fun summaries(): List<GuideSectionSummary> = sections.map { it.summaryItem() }

    companion object {
        /** 资产缺失或解析失败时的兜底值。界面用 [isEmpty] 判断后隐藏入口。 */
        val empty = MuseumGuide(
            title = "博物馆怎么逛",
            subtitle = "",
            intro = "",
            updatedAt = "",
            sections = emptyList(),
            glossary = emptyList(),
            sources = emptyList(),
        )
    }
}

/**
 * 一节正文。
 *
 * [body] 是轻量 markdown，直接复用文物详情那套 [cn.chenjie.museumexplore.ui.RelicMarkdown] 解析器。
 * 不另写一套解析器，是为了让「指南里的格式」和「文物详情里的格式」永远一致——
 * 用户在两处看到的小标题、列表、粗体表现必须一样。
 */
data class GuideSection(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
) {
    fun summaryItem(): GuideSectionSummary = GuideSectionSummary(id = id, title = title, summary = summary)
}

data class GuideSectionSummary(
    val id: String,
    val title: String,
    val summary: String,
)

/** 生僻字速查的一组，比如「青铜器名」「地名与部族」。 */
data class GuideGlossaryGroup(
    val group: String,
    val items: List<GuideGlossaryItem>,
)

/**
 * 一条生僻字。
 *
 * [meaning] 允许为空——地名类条目里，「马王堆」这种没什么可解释的，硬编一句话反而是噪音。
 */
data class GuideGlossaryItem(
    val char: String,
    val pinyin: String,
    val meaning: String,
)
