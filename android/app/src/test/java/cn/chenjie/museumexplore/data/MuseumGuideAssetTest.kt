package cn.chenjie.museumexplore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「博物馆怎么逛」的资产格式守卫。
 *
 * 这个模块的正文由 `tools/build_guide.py` 生成，App 端只做解析不做校验。所以一旦脚本生成的
 * 内容跑偏（节 id 重复、正文用了渲染器不支持的语法、生僻字缺拼音），App 不会崩，
 * 但用户会看到一堆原样显示的 `|` 和 `---`。这类「不报错但难看」的问题只能在测试里拦。
 */
class MuseumGuideAssetTest {

    private val asset: File = run {
        // 单元测试的工作目录是模块目录（android/app），assets 相对它定位。
        val candidates = listOf(
            File("src/main/assets/museum_guide.json"),
            File("android/app/src/main/assets/museum_guide.json"),
        )
        candidates.firstOrNull { it.exists() }
            ?: error("找不到 museum_guide.json，检查测试工作目录：${File(".").absolutePath}")
    }

    private val raw: String = asset.readText(Charsets.UTF_8)

    @Test
    fun `asset parses as json with sections and glossary`() {
        assertTrue("资产文件不该是空的", raw.length > 1000)
        assertTrue("正文里不该出现未转义的换行导致的截断", raw.trim().endsWith("}"))
        // 用最朴素的断言兜住结构：真正的解析在 MuseumRepository，这里只防止生成脚本整个坏掉。
        assertTrue(raw.contains("\"sections\""))
        assertTrue(raw.contains("\"glossary\""))
    }

    @Test
    fun `section ids are unique and stable looking`() {
        val ids = Regex("\"id\": \"([a-z_]+)\"").findAll(raw).map { it.groupValues[1] }.toList()
        assertTrue("至少要有 8 节，现在只有 ${ids.size}", ids.size >= 8)
        assertEquals("节 id 不能重复：$ids", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue("节 id 只允许小写字母和下划线：$id", Regex("^[a-z][a-z_]*$").matches(id))
        }
    }

    @Test
    fun `body never uses markdown features the renderer does not support`() {
        // RelicMarkdown 只认标题、列表、粗体斜体。表格、引用、代码块、分割线写了会原样显示成符号。
        val bodies = Regex("\"body\": \"((?:[^\"\\\\]|\\\\.)*)\"").findAll(raw).map { it.groupValues[1] }.toList()
        assertTrue("没有解析到任何正文", bodies.isNotEmpty())
        bodies.forEachIndexed { index, body ->
            assertFalse("第 ${index + 1} 节正文里有表格分隔行 |---|", body.contains("---|"))
            assertFalse("第 ${index + 1} 节正文里有引用块 >", Regex("(?m)^>\\s").containsMatchIn(body))
            assertFalse("第 ${index + 1} 节正文里有代码块 ```", body.contains("```"))
            assertFalse("第 ${index + 1} 节正文里有 HTML 标签", Regex("<[a-zA-Z/][^>]*>").containsMatchIn(body))
        }
    }

    @Test
    fun `深度专题三节都在且能挂到目录分组`() {
        // 这三节是通用品类之外的「知识地图」，删掉或改 id 会让目录少一块且不易察觉。
        listOf("kilns", "painting-history", "calligraphy-history").forEach { id ->
            assertTrue("缺了专题节 $id", raw.contains("\"id\": \"$id\""))
        }
    }

    @Test
    fun `glossary entries always carry a reading`() {
        val entries = Regex("\\{\\s*\"char\": \"[^\"]+\",\\s*\"pinyin\": \"([^\"]*)\"").findAll(raw).toList()
        assertTrue("生僻字速查不该是空的", entries.isNotEmpty())
        entries.forEach { match ->
            assertTrue("有生僻字缺拼音", match.groupValues[1].isNotBlank())
        }
    }
}
