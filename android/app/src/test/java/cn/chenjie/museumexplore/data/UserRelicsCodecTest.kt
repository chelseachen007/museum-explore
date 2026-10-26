package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.UserRelic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自建国宝的编解码。
 *
 * 最关键的一条约束是 id 必须为负数：正数是 195 件永禁文物的编号，
 * 一旦撞号，「已看到」标记就会挂到错误的文物上。这条在这里用测试钉死。
 */
class UserRelicsCodecTest {
    @Test
    fun roundTripsAllFields() {
        val relic = UserRelic(
            id = -3,
            name = "越王勾践剑",
            museum = "湖北省博物馆",
            city = "武汉",
            era = "春秋晚期",
            category = "青铜器",
            reason = "展柜前排了很久",
            story = "剑身菱形暗格纹，出土时几乎无锈。",
            photo = "photo_1_ab.jpg",
            museumUrl = "https://example.com/jian",
            createdAt = 1_700_000_000_000L,
        )

        val decoded = UserRelicsCodec.decode(UserRelicsCodec.encode(listOf(relic)))

        assertEquals(listOf(relic), decoded)
    }

    @Test
    fun dropsPositiveIdThatWouldCollideWithThe195List() {
        val raw = """
            {"schemaVersion":1,"relics":[
              {"id":7,"name":"撞号记录","museum":"","city":""},
              {"id":-1,"name":"合法记录","museum":"","city":""}
            ]}
        """.trimIndent()

        val decoded = UserRelicsCodec.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals(-1, decoded.first().id)
    }

    @Test
    fun skipsBlankNameAndKeepsTheRest() {
        val raw = """
            {"schemaVersion":1,"relics":[
              {"id":-1,"name":"   ","museum":"","city":""},
              {"id":-2,"name":"有效","museum":"","city":""}
            ]}
        """.trimIndent()

        val decoded = UserRelicsCodec.decode(raw)

        assertEquals(listOf(-2), decoded.map { it.id })
    }

    @Test
    fun treatsNullLiteralAsMissing() {
        val raw = """
            {"schemaVersion":1,"relics":[
              {"id":-1,"name":"有效","museum":"","city":"","era":"null","photo":null}
            ]}
        """.trimIndent()

        val relic = UserRelicsCodec.decode(raw).single()

        assertNull(relic.era)
        assertNull(relic.photo)
    }

    @Test
    fun invalidJsonYieldsEmptyListInsteadOfThrowing() {
        assertTrue(UserRelicsCodec.decode("{不是 JSON").isEmpty())
        assertTrue(UserRelicsCodec.decode(null).isEmpty())
        assertTrue(UserRelicsCodec.decode("").isEmpty())
    }

    @Test
    fun dropsDuplicateIds() {
        val raw = """
            {"schemaVersion":1,"relics":[
              {"id":-1,"name":"第一次","museum":"","city":""},
              {"id":-1,"name":"第二次","museum":"","city":""}
            ]}
        """.trimIndent()

        val decoded = UserRelicsCodec.decode(raw)

        assertEquals(listOf("第一次"), decoded.map { it.name })
    }
}
