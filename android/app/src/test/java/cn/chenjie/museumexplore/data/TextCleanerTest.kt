package cn.chenjie.museumexplore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCleanerTest {
    @Test
    fun stripsTagsEntitiesAndCitations() {
        val raw = "<p>上海博物馆&nbsp;位于<b>人民广场</b> [ 2 ]。<br/>馆藏&hellip;丰富</p>"

        assertEquals("上海博物馆 位于人民广场。\n馆藏…丰富", TextCleaner.clean(raw))
    }

    @Test
    fun keepsReadableParagraphs() {
        val raw = "第一段&lt;br&gt;换行\n\n\n第二段"

        assertEquals("第一段\n换行\n\n第二段", TextCleaner.clean(raw))
    }
}
