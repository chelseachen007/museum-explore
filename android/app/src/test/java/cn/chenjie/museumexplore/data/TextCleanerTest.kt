package cn.chenjie.museumexplore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCleanerTest {
    @Test
    fun stripsTagsEntitiesAndCitations() {
        val raw = "<p>上海博物馆&nbsp;位于<b>人民广场</b> [ 2 ]。<br/>馆藏&hellip;丰富</p>"

        assertEquals("上海博物馆位于人民广场。\n馆藏…丰富", TextCleaner.clean(raw))
    }

    @Test
    fun keepsReadableParagraphs() {
        val raw = "第一段&lt;br&gt;换行\n\n\n第二段"

        assertEquals("第一段\n换行\n\n第二段", TextCleaner.clean(raw))
    }

    @Test
    fun stripsMediaWikiStylesCoordinatesAndFootnotes() {
        val raw = ".mw-parser-output.latitude{white-space:nowrap} 30°15′12″N 120°08′20″E ﻿ / ﻿ " +
            "30.25333°N 120.13889°E ﻿ / ﻿ 30.25333; 120.13889 浙江省博物馆 [ 注 1 ]，始建于1929年。"

        assertEquals("浙江省博物馆，始建于1929年。", TextCleaner.clean(raw))
    }

    @Test
    fun keepsBodyTextWhenStyleBlockIsTruncated() {
        val raw = "西藏博物馆（藏語：.mw-parser-output.uchen{font-family:\"Qomolangma\"}.mw-parser-output.ume{font-family:\"Betsu\"}"

        assertEquals("西藏博物馆（藏語：", TextCleaner.clean(raw))
    }

    @Test
    fun keepsOrdinarySlash() {
        assertEquals("门票 10元/人", TextCleaner.clean("门票 10元/人"))
    }
}
