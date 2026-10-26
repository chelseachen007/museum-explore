package cn.chenjie.museumexplore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 日期一句话输入。
 *
 * 用户报过的问题：「没有访问时间，也没有地方可以改」——填感想时根本没法说是哪天去的。
 * 现在日期就在编辑面板里，输入解析必须宽容但不能瞎猜：解析不了就返回 null，
 * 由界面提示格式，绝不默认成今天（那会把旧账全记成今天）。
 */
class VisitDateInputTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「今天」为 2026-10-26，避免测试随真实日期漂移。 */
    private val now: Long = LocalDate.of(2026, 10, 26).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    private fun local(epoch: Long?): LocalDate? = epoch?.let { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it), zone) }

    @Test
    fun `完整日期多种写法都能认`() {
        listOf("2026-10-03", "2026/10/3", "2026.10.3", "2026年10月3日", "2026 10 03").forEach { input ->
            assertEquals(input, LocalDate.of(2026, 10, 3), local(VisitDateInput.parse(input, now, zone)))
        }
    }

    @Test
    fun `只写月日时按最近一次过去的那天算`() {
        // 今年 3 月 5 日已经过去 → 今年。
        assertEquals(LocalDate.of(2026, 3, 5), local(VisitDateInput.parse("3-5", now, zone)))
        assertEquals(LocalDate.of(2026, 3, 5), local(VisitDateInput.parse("3月5日", now, zone)))
        // 12 月 1 日还没到 → 只能是去年那次参观。
        assertEquals(LocalDate.of(2025, 12, 1), local(VisitDateInput.parse("12-01", now, zone)))
        // 今天本身算今年。
        assertEquals(LocalDate.of(2026, 10, 26), local(VisitDateInput.parse("10-26", now, zone)))
    }

    @Test
    fun `解析不了就返回 null 而不是今天`() {
        listOf("", "  ", "上个月", "2026-13-01", "2-30", "abc", "2026").forEach { input ->
            assertNull("不应该把「$input」猜成某一天", VisitDateInput.parse(input, now, zone))
        }
    }

    @Test
    fun `回填格式固定为年月日`() {
        assertEquals("2026-10-03", VisitDateInput.format(LocalDate.of(2026, 10, 3).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), zone))
    }

    @Test
    fun `解析结果落在当天中午`() {
        val epoch = VisitDateInput.parse("2026-10-03", now, zone)
        assertNotNull(epoch)
        val time = java.time.Instant.ofEpochMilli(epoch!!).atZone(zone).toLocalTime()
        assertEquals(12, time.hour)
    }
}
