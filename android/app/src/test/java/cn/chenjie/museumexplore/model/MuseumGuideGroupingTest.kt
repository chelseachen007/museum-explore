package cn.chenjie.museumexplore.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目录分组的回归测试。
 *
 * 分组的价值在于「新增一节不会从目录里消失」：品类越加越多，一旦有人往 json 里加了节
 * 却忘了改布局常量，用户就该找不到了。所以这里既测正常归位，也测兜底。
 */
class MuseumGuideGroupingTest {

    private fun section(id: String) = GuideSection(id = id, title = id, summary = "", body = "")

    private fun guide(vararg ids: String) = MuseumGuide(
        title = "t",
        subtitle = "",
        intro = "",
        updatedAt = "",
        sections = ids.map(::section),
        glossary = emptyList(),
        sources = emptyList(),
    )

    @Test
    fun `已知节按心态品类现场三组归位`() {
        val groups = guide("mindset", "bronze", "kilns", "onesite_unknown", "after").grouped()
        assertEquals(listOf("心态与准备", "各品类怎么看", "现场与之后", "更多"), groups.map { it.title })
        assertEquals(listOf("mindset"), groups[0].sections.map { it.id })
        assertEquals(listOf("bronze", "kilns"), groups[1].sections.map { it.id })
        assertEquals(listOf("after"), groups[2].sections.map { it.id })
    }

    @Test
    fun `布局里没登记的节落到更多组而不是消失`() {
        val groups = guide("mindset", "brand_new_section").grouped()
        val all = groups.flatMap { group -> group.sections.map { it.id } }
        assertTrue("新节必须仍出现在目录里：$all", all.contains("brand_new_section"))
        assertEquals("更多", groups.last().title)
    }

    @Test
    fun `分组不重复也不丢节`() {
        val ids = listOf("mindset", "before", "kilns", "painting-history", "calligraphy-history", "onsite", "after", "extra")
        val flat = guide(*ids.toTypedArray()).grouped().flatMap { group -> group.sections.map { it.id } }
        assertEquals("每个节只能出现一次", ids.size, flat.size)
        assertEquals("节一个都不能少", ids.toSet(), flat.toSet())
    }

    @Test
    fun `空目录不产生空分组`() {
        assertTrue(guide().grouped().isEmpty())
    }
}
