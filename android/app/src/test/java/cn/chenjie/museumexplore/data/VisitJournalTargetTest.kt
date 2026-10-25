package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VisitPhoto
import cn.chenjie.museumexplore.model.VISIT_ORDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「再记一次参观」后感想和照片必须还能看到。
 *
 * 之前的 bug：第一次到访写了感想和照片（挂在第一条 visit 上），
 * 再记一次参观只追加了一条空壳 visit，页面读取时按日期取「最新一条」，
 * 拿到的是空壳，于是用户看到「我的记录」是空的，以为第一次的记录丢了。
 * 记录目标改成「有 journal 的那条」之后，写和读才对得上。
 */
class VisitJournalTargetTest {

    private val museum = "浙江省博物馆"

    private fun visit(
        id: String,
        museumId: String = museum,
        startedAt: Long? = 1_700_000_000_000L,
        note: String = "",
        photos: List<VisitPhoto> = emptyList(),
    ) = Visit(
        id = id,
        museumId = museumId,
        city = "杭州",
        startedAt = startedAt,
        datePrecision = if (startedAt == null) DatePrecision.UNKNOWN else DatePrecision.EXACT,
        source = RecordSource.MANUAL,
        note = note,
        photos = photos,
    )

    /** 与 MuseumRepository 里的取法一致：有 journal 的那条优先，否则取最近一条。 */
    private fun pickJournalTarget(visits: List<Visit>): Visit? =
        visits.sortedWith(VISIT_ORDER).firstOrNull { it.hasJournal }
            ?: visits.sortedWith(VISIT_ORDER).firstOrNull()

    @Test
    fun `多条到访时回填有内容的那条而不是最新的空壳`() {
        val first = visit("v1", startedAt = 1000L, note = "第一次来，看了越窑青瓷")
        val second = visit("v2", startedAt = 2000L)

        val target = pickJournalTarget(listOf(first, second))

        assertEquals("有内容的记录优先", "v1", target?.id)
        assertTrue(target!!.hasJournal)
    }

    @Test
    fun `都没内容时退回最近一条`() {
        val first = visit("v1", startedAt = 1000L)
        val second = visit("v2", startedAt = 2000L)

        val target = pickJournalTarget(listOf(first, second))

        assertEquals("v2", target?.id)
    }

    @Test
    fun `只有照片也算有内容`() {
        val first = visit("v1", startedAt = 1000L, photos = listOf(VisitPhoto("a.jpg", 45)))
        val second = visit("v2", startedAt = 2000L)

        assertEquals("v1", pickJournalTarget(listOf(first, second))?.id)
    }

    @Test
    fun `馆区记录不会干扰整馆记录的目标`() {
        val whole = visit("v1", museumId = museum, startedAt = 1000L, note = "整馆那次")
        val branchVisit = visit("v2", museumId = "$museum · 之江馆区", startedAt = 2000L)

        // 整馆视角按 targetId 精确取，不该把馆区的记录算进来。
        val wholeOnly = listOf(whole, branchVisit).filter { it.museumId == museum }
        assertEquals("v1", pickJournalTarget(wholeOnly)?.id)
    }
}
