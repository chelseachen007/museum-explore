package cn.chenjie.museumexplore.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * 手动填写参观日期时的一句话输入。
 *
 * 为什么不给日期选择器：用户手上只有「上个月第三个周末去的」这种模糊记忆，
 * 翻日历比直接打几个字慢；而年月日三个数字的格式到处都在用，人人会写。
 *
 * 纯函数，便于单测覆盖：`2026-10-03` / `2026/10/3` / `2026年10月3日` / `10-03` / `10月3日`。
 * 没写年份时，今年已经过去的那天按今年算，还没到的按去年算——记录的是参观，
 * 不可能是「未来某天」。解析不了就返回 null，由界面提示格式，绝不猜成今天。
 */
object VisitDateInput {

    /** 写成 `2026-10-03` 交给用户看到的格式。`年/月/日` 之间允许直接留空格。 */
    private val FULL = Regex("""(\d{4})\s*[-/.年\s]\s*(\d{1,2})\s*[-/.月\s]\s*(\d{1,2})\s*日?""")

    /** 只写了月和日：`10-03` / `10月3日`。 */
    private val MONTH_DAY = Regex("""(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?""")

    /**
     * 解析成当天 12:00 的时间戳。
     *
     * 取中午而不是零点：同一天里用户在哪一刻保存都不影响「是不是同一天」的判断，
     * 也不会因为时区或夏令时把它推到前一天。
     */
    fun parse(text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)

        FULL.matchEntire(raw)?.let { match ->
            val date = runCatching {
                LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }.getOrNull() ?: return null
            return toNoon(date, zone)
        }
        MONTH_DAY.matchEntire(raw)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            // 没写年份：今年还没到的那天只能是去年，因为记的是已经发生过的事。
            val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
            val date = if (thisYear.isAfter(today)) thisYear.minusYears(1) else thisYear
            return toNoon(date, zone)
        }
        return null
    }

    /** 把时间戳反向写成 `2026-10-03`，填进输入框让用户在此基础上改。 */
    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
        return "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
    }

    private fun toNoon(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
}
