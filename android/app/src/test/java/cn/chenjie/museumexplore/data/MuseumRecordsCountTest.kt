package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VisitPhoto
import cn.chenjie.museumexplore.model.VISIT_ORDER
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「已到访 N 次」和下面「我的记录」的卡片数必须一致。
 *
 * 之前的 bug：museumRecords 只保留有感想/照片的到访，
 * 用户点「再记一次参观」补了一条空到访，已到访次数 +1，卡片却没多，
 * 看起来就像操作没生效。
 */
class MuseumRecordsCountTest {

    private val museum = "浙江省博物馆"

    private fun visit(
        id: String,
        startedAt: Long? = 1_700_000_000_000L,
        note: String = "",
        photos: List<VisitPhoto> = emptyList(),
        pending: Boolean = false,
    ) = Visit(
        id = id,
        museumId = museum,
        city = "杭州",
        startedAt = startedAt,
        datePrecision = if (startedAt == null) DatePrecision.UNKNOWN else DatePrecision.EXACT,
        source = RecordSource.MANUAL,
        pendingConfirmation = pending,
        note = note,
        photos = photos,
    )

    /** 与 MuseumRepository.museumRecords 的取法一致（不含馆区介入的整馆视角）。 */
    private fun records(state: ExplorationState): List<Visit> =
        state.confirmedVisitsForMuseum(museum).sortedWith(VISIT_ORDER)

    @Test
    fun `刚补的空到访也要出现在记录里`() {
        val state = ExplorationState(
            visits = listOf(
                visit("v1", startedAt = 1000L, note = "第一次来"),
                visit("v2", startedAt = 2000L),
            ),
        )

        val list = records(state)

        assertEquals("两次到访就要有两张卡片", 2, list.size)
        assertEquals("最近一次排前面", "v2", list.first().id)
    }

    @Test
    fun `待确认到访不计入卡片数`() {
        val state = ExplorationState(
            visits = listOf(
                visit("v1", startedAt = 1000L),
                visit("v2", startedAt = 2000L, pending = true),
            ),
        )

        assertEquals("待确认的那条不算已到访，也不该占一张卡片", 1, records(state).size)
    }

    @Test
    fun `删掉感想后卡片还在`() {
        val state = ExplorationState(
            visits = listOf(visit("v1", startedAt = 1000L, note = "写过的感想")),
        )
        val cleared = state.copy(visits = state.visits.map { it.copy(note = "", photos = emptyList()) })

        assertEquals("清空内容不等于删掉这次到访", 1, records(cleared).size)
        assertEquals(1, cleared.confirmedVisitsForMuseum(museum).size)
    }

    @Test
    fun `卡片数与已到访次数用的是同一个来源`() {
        val state = ExplorationState(
            visits = listOf(
                visit("v1", startedAt = 1000L),
                visit("v2", startedAt = 2000L, note = "有内容"),
                visit("v3", startedAt = 3000L),
                visit("v4", startedAt = 4000L, pending = true),
            ),
        )

        assertEquals(
            "卡片数必须等于已到访次数",
            state.confirmedVisitsForMuseum(museum).size,
            records(state).size,
        )
    }
}
