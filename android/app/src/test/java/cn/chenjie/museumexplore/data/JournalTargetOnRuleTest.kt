package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.DatePrecision
import cn.chenjie.museumexplore.model.ExplorationState
import cn.chenjie.museumexplore.model.RecordSource
import cn.chenjie.museumexplore.model.Visit
import cn.chenjie.museumexplore.model.VISIT_ORDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「从清单写感想」的落点规则。
 *
 * 用户报过的问题：「从博物馆清单里面，直接写感想，没有访问时间」。
 * 现在清单每一行都能直接写，日期也能填，但填不填、那天有没有记录，
 * 决定了这条感想变成「一次新的参观」还是「补进某一次」——这里锁住这个规则。
 *
 * `journalTargetOn` 依赖仓库读取本地存储，单测里用同一份规则重算一遍：
 * 两者必须给出同样的答案，否则又会回到「写进 A、读出来是 B」的老 bug。
 */
class JournalTargetOnRuleTest {

    private val museum = "浙江省博物馆"
    private val noon = 12 * 60 * 60 * 1000L

    /** 与 MuseumRepository.journalTargetOn 的实现一一对应。 */
    private fun target(state: ExplorationState, museumId: String, startedAt: Long?): Pair<String?, Boolean> {
        if (startedAt == null) return null to true
        val sameDay = state.confirmedVisitsForMuseumFamily(museumId)
            .sortedWith(VISIT_ORDER)
            .firstOrNull { it.hasExactDate && sameDayOf(it.startedAt!!, startedAt) }
        return if (sameDay != null) sameDay.id to false else null to true
    }

    /** 只比较「同一天」，两个时间戳都落在中午，直接比差值即可。 */
    private fun sameDayOf(first: Long, second: Long): Boolean =
        first / noon == second / noon

    private fun visit(
        id: String,
        museumId: String = museum,
        startedAt: Long? = 1000L,
        note: String = "",
    ) = Visit(
        id = id,
        museumId = museumId,
        city = "杭州",
        startedAt = startedAt,
        datePrecision = if (startedAt == null) DatePrecision.UNKNOWN else DatePrecision.EXACT,
        source = RecordSource.MANUAL,
        note = note,
    )

    @Test
    fun `同一天已有记录时补进那一条而不是再建一张卡片`() {
        val state = ExplorationState(visits = listOf(visit("v1", startedAt = noon)))

        val (id, create) = target(state, museum, noon)

        assertEquals("v1", id)
        assertTrue("不该新建", !create)
    }

    @Test
    fun `换了一天就是另一趟参观`() {
        val state = ExplorationState(visits = listOf(visit("v1", startedAt = noon)))

        val (id, create) = target(state, museum, noon + 24 * 60 * 60 * 1000L)

        assertNull("不该写进前一天那次", id)
        assertTrue(create)
    }

    @Test
    fun `日期留空也建一条新记录而不是塞回上一趟`() {
        // 用户清空日期通常意味着「记不清哪天」，那是另一趟参观。
        val state = ExplorationState(visits = listOf(visit("v1", startedAt = noon, note = "上次来的")))

        val (id, create) = target(state, museum, null)

        assertNull(id)
        assertTrue("留空日期也要落成一条记录", create)
    }

    @Test
    fun `馆区那天的记录算同一天`() {
        val branch = "$museum · 之江馆区"
        val state = ExplorationState(visits = listOf(visit("v1", museumId = branch, startedAt = noon)))

        // 清单里选了「整馆」写感想，那天馆区已经记过一次，仍属于同一次参观。
        val (id, create) = target(state, museum, noon)

        assertEquals("v1", id)
        assertTrue(!create)
    }

    @Test
    fun `上次那天取最近一次有确切日期的参观`() {
        // 清单页「上次那天」按钮的数据来源。日期待补充的记录排在 VISIT_ORDER 末尾，
        // 直接取最后一条会拿到它——那就等于「上次那天」不可用。
        val state = ExplorationState(
            visits = listOf(
                visit("v1", startedAt = 2 * noon),
                visit("v2", startedAt = null),
                visit("v3", startedAt = 7 * noon),
            ),
        )

        val last = state.confirmedVisitsForMuseum(museum)
            .filter { it.hasExactDate }
            .maxByOrNull { it.startedAt!! }
            ?.startedAt

        assertEquals("取最近一次真的去过的日子", 7 * noon, last)
    }

    @Test
    fun `没有任何记录时新建`() {
        val (id, create) = target(ExplorationState(), museum, noon)

        assertNull(id)
        assertTrue(create)
    }
}
