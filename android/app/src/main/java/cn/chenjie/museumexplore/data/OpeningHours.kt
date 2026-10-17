package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.OpeningStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * 从馆方开放时间文本里保守推断「今天能不能去」。
 *
 * 原则（需求文档 3.3 不制造假确定性）：读不出明确时段就返回 [OpeningStatus.UNKNOWN]，
 * 页面显示「待官方确认」，绝不默认「开放中」。
 */
object OpeningHours {
    private val RANGE = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})\s*[—–~～\-至]\s*(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val LAST_ENTRY = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})\s*停止(入馆|入场|进馆|售票)""")
    private val CLOSED_WEEKDAY = Regex("""周([一二三四五六日天])闭馆""")
    private val WEEKDAY_INDEX = mapOf(
        '一' to DayOfWeek.MONDAY,
        '二' to DayOfWeek.TUESDAY,
        '三' to DayOfWeek.WEDNESDAY,
        '四' to DayOfWeek.THURSDAY,
        '五' to DayOfWeek.FRIDAY,
        '六' to DayOfWeek.SATURDAY,
        '日' to DayOfWeek.SUNDAY,
        '天' to DayOfWeek.SUNDAY,
    )

    data class DayPlan(
        val status: OpeningStatus,
        val detail: String?,
        val openMinute: Int?,
        val lastEntryMinute: Int?,
        val closeMinute: Int?,
    )

    fun plan(openingHours: String?, closedDays: String?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): DayPlan {
        val moment = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = moment.dayOfWeek
        val minutesNow = moment.hour * 60 + moment.minute
        val text = openingHours.orEmpty()
        val closedText = listOfNotNull(openingHours, closedDays).joinToString(" ")

        if (closedText.isBlank()) return DayPlan(OpeningStatus.UNKNOWN, null, null, null, null)

        val closedWeekdays = CLOSED_WEEKDAY.findAll(closedText)
            .mapNotNull { WEEKDAY_INDEX[it.groupValues[1].firstOrNull()] }
            .toSet()
        if (today in closedWeekdays) {
            return DayPlan(OpeningStatus.CLOSED_TODAY, "今天闭馆", null, null, null)
        }
        val segment = selectSeasonalSegment(text, moment.monthValue)
        val range = RANGE.find(segment) ?: return DayPlan(OpeningStatus.UNKNOWN, null, null, null, null)
        val openMinute = range.groupValues[1].toInt() * 60 + range.groupValues[2].toInt()
        val closeMinute = range.groupValues[3].toInt() * 60 + range.groupValues[4].toInt()
        val lastEntry = LAST_ENTRY.find(segment)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
            ?.takeIf { it in openMinute..closeMinute }
        val entryDeadline = lastEntry ?: closeMinute

        val status = when {
            minutesNow < openMinute -> OpeningStatus.BEFORE_OPEN
            minutesNow > closeMinute -> OpeningStatus.CLOSED_TODAY
            minutesNow > entryDeadline -> OpeningStatus.CLOSING_SOON
            minutesNow >= entryDeadline - SOON_WINDOW_MINUTES -> OpeningStatus.CLOSING_SOON
            else -> OpeningStatus.OPEN
        }
        val detail = when (status) {
            OpeningStatus.BEFORE_OPEN -> "${format(openMinute)} 开馆"
            OpeningStatus.CLOSING_SOON -> "${format(entryDeadline)} 停止入馆"
            OpeningStatus.CLOSED_TODAY -> "今日 ${format(openMinute)}—${format(closeMinute)} 开放，现在已闭馆"
            else -> "${format(entryDeadline)} 停止入馆"
        }
        return DayPlan(status, detail, openMinute, entryDeadline, closeMinute)
    }

    fun status(openingHours: String?, closedDays: String?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): OpeningStatus =
        plan(openingHours, closedDays, nowMillis, zone).status

    private fun selectSeasonalSegment(text: String, month: Int): String {
        if (!text.contains("旺季") || !text.contains("淡季")) return text
        val peakIndex = text.indexOf("旺季")
        val offIndex = text.indexOf("淡季")
        val peak = text.substring(peakIndex, if (offIndex > peakIndex) offIndex else text.length)
        val off = text.substring(offIndex, if (peakIndex > offIndex) peakIndex else text.length)
        val preferPeak = month in 4..10
        val preferred = if (preferPeak) peak else off
        return if (RANGE.containsMatchIn(preferred)) preferred else text
    }

    private fun format(minuteOfDay: Int): String = "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    const val SOON_WINDOW_MINUTES = 60L

    /** 供测试与调试：文本里能解析出的最早开放时段。 */
    fun hasReliableHours(openingHours: String?): Boolean = openingHours != null && RANGE.containsMatchIn(openingHours)
}
