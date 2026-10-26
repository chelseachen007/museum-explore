package cn.chenjie.museumexplore.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情正文渲染规则的回归测试。
 *
 * 这份测试的用法是「先写坏样本，再改渲染器」：正文是交给别人批量写的数据资产，
 * 一旦渲染规则悄悄变了，写作者不会收到任何提示，只会看到页面上少了一段。
 */
class RelicMarkdownTest {

    @Test
    fun `空输入不产生任何块`() {
        assertEquals(emptyList<MdBlock>(), RelicMarkdown.parse(""))
        assertEquals(emptyList<MdBlock>(), RelicMarkdown.parse("   \n\n  \n"))
    }

    @Test
    fun `标题按井号数分级`() {
        val blocks = RelicMarkdown.parse("## 它的前世\n### 出土那一刻\n#### 再往下")
        assertEquals(
            listOf(
                MdBlock.Heading(2, "它的前世"),
                MdBlock.Heading(3, "出土那一刻"),
                MdBlock.Heading(4, "再往下"),
            ),
            blocks,
        )
    }

    @Test
    fun `五级标题按普通段落处理`() {
        assertEquals(listOf(MdBlock.Paragraph("##### 太深了")), RelicMarkdown.parse("##### 太深了"))
    }

    @Test
    fun `空行分段`() {
        val blocks = RelicMarkdown.parse("第一段\n\n第二段")
        assertEquals(listOf(MdBlock.Paragraph("第一段"), MdBlock.Paragraph("第二段")), blocks)
    }

    @Test
    fun `单换行在段落内保留为软换行`() {
        assertEquals(listOf(MdBlock.Paragraph("上半句\n下半句")), RelicMarkdown.parse("上半句\n下半句"))
    }

    @Test
    fun `无序列表合并成一个块`() {
        val blocks = RelicMarkdown.parse("- 鼎\n- 簋\n* 爵")
        assertEquals(1, blocks.size)
        val list = blocks.single() as MdBlock.BulletList
        assertEquals(false, list.ordered)
        assertEquals(listOf("鼎", "簋", "爵"), list.items)
    }

    @Test
    fun `有序列表合并成一个块`() {
        val list = RelicMarkdown.parse("1. 预约\n2. 看讲解").single() as MdBlock.BulletList
        assertEquals(true, list.ordered)
        assertEquals(listOf("预约", "看讲解"), list.items)
    }

    @Test
    fun `有序与无序相邻时各自成块`() {
        val blocks = RelicMarkdown.parse("- 无序\n1. 有序")
        assertEquals(2, blocks.size)
        assertEquals(false, (blocks[0] as MdBlock.BulletList).ordered)
        assertEquals(true, (blocks[1] as MdBlock.BulletList).ordered)
    }

    @Test
    fun `列表项之间的空行不打断列表`() {
        val list = RelicMarkdown.parse("- 第一条\n\n- 第二条").single() as MdBlock.BulletList
        assertEquals(listOf("第一条", "第二条"), list.items)
    }

    @Test
    fun `标题结束列表`() {
        val blocks = RelicMarkdown.parse("- 一条\n## 新章节")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.BulletList)
        assertTrue(blocks[1] is MdBlock.Heading)
    }

    @Test
    fun `缩进续行并回上一列表项`() {
        val list = RelicMarkdown.parse("- 长句子前半\n  长句子后半").single() as MdBlock.BulletList
        assertEquals(listOf("长句子前半长句子后半"), list.items)
    }

    @Test
    fun `粗体优先于斜体解析`() {
        val spans = RelicMarkdown.inlineSpans("这是**后母戊鼎**的故事")
        assertEquals(
            listOf(
                InlineSpan("这是", bold = false, italic = false),
                InlineSpan("后母戊鼎", bold = true, italic = false),
                InlineSpan("的故事", bold = false, italic = false),
            ),
            spans,
        )
    }

    @Test
    fun `斜体单独生效`() {
        val spans = RelicMarkdown.inlineSpans("*九秋风露越窑开*")
        assertEquals(listOf(InlineSpan("九秋风露越窑开", bold = false, italic = true)), spans)
    }

    @Test
    fun `未闭合标记原样保留`() {
        val spans = RelicMarkdown.inlineSpans("**没闭合的粗体")
        assertEquals("**没闭合的粗体", spans.joinToString("") { it.text })
        assertTrue(spans.none { it.bold })
    }

    @Test
    fun `纯文本去掉标记`() {
        val text = RelicMarkdown.plainText("## 小标题\n\n正文**重点**\n\n- 甲\n- 乙")
        assertEquals("小标题\n\n正文重点\n\n· 甲\n· 乙", text)
    }

    @Test
    fun `认不出的语法当普通段落`() {
        val blocks = RelicMarkdown.parse("| 表格 | 不支持 |\n> 引用也不支持")
        assertEquals(listOf(MdBlock.Paragraph("| 表格 | 不支持 |\n> 引用也不支持")), blocks)
    }
}
