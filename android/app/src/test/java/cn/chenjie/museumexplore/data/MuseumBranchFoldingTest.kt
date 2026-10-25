package cn.chenjie.museumexplore.data

import cn.chenjie.museumexplore.model.MuseumBranch
import cn.chenjie.museumexplore.model.MuseumInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一个馆在列表里只能占一行。
 *
 * 之前的 bug：名录里留着「浙江省博物馆 · 之江馆区」这种独立条目，
 * 而 branchParents 只收父馆名、不收馆区名，于是两个馆区在列表里原样露出来，
 * 用户看到「浙江省博物馆、之江馆区、孤山馆区」三行，以为是三家不同的馆。
 */
class MuseumBranchFoldingTest {

    private fun info(name: String, city: String) = MuseumInfo(
        name = name,
        city = city,
        officialUrl = null,
        wechat = null,
        address = null,
        intro = null,
        openingHours = null,
        reservation = null,
        wikiUrl = null,
        miniProgram = null,
        type = "museum",
    )

    private val zhejiangBranches = listOf(
        MuseumBranch("浙江省博物馆", "浙江省博物馆", "杭州", "孤山路25号"),
        MuseumBranch("浙江省博物馆", "浙江省博物馆 · 之江馆区", "杭州", "江涵路300号"),
        MuseumBranch("浙江省博物馆", "浙江省博物馆 · 孤山馆区", "杭州", "孤山路25号"),
    )

    @Test
    fun `馆区记录不再作为独立条目出现`() {
        val directory = mapOf(
            "浙江省博物馆" to info("浙江省博物馆", "杭州"),
            "浙江省博物馆 · 之江馆区" to info("浙江省博物馆 · 之江馆区", "杭州"),
            "浙江省博物馆 · 孤山馆区" to info("浙江省博物馆 · 孤山馆区", "杭州"),
            "故宫博物院" to info("故宫博物院", "北京"),
        )

        val folded = MuseumRepository.foldBranchRecords(directory, zhejiangBranches)

        assertEquals(
            "浙江只剩父馆一条，故宫不受影响",
            setOf("浙江省博物馆", "故宫博物院"),
            folded.info.keys,
        )
    }

    @Test
    fun `父馆那一行不算馆区`() {
        val folded = MuseumRepository.foldBranchRecords(
            mapOf("浙江省博物馆" to info("浙江省博物馆", "杭州")),
            zhejiangBranches,
        )

        assertTrue("父馆自己不能被折掉", "浙江省博物馆" in folded.info)
        assertFalse("父馆不是子馆", "浙江省博物馆" in folded.branchNames)
        assertEquals(
            setOf("浙江省博物馆 · 之江馆区", "浙江省博物馆 · 孤山馆区"),
            folded.branchNames,
        )
    }

    @Test
    fun `馆区名能折回父馆`() {
        val folded = MuseumRepository.foldBranchRecords(
            mapOf("浙江省博物馆" to info("浙江省博物馆", "杭州")),
            zhejiangBranches,
        )

        assertEquals("浙江省博物馆", folded.aliases["浙江省博物馆 · 之江馆区"])
        assertEquals("浙江省博物馆", folded.aliases["浙江省博物馆 · 孤山馆区"])
        assertFalse("父馆不需要别名", "浙江省博物馆" in folded.aliases)
    }

    @Test
    fun `没有馆区数据时一切保持不变`() {
        val directory = mapOf("故宫博物院" to info("故宫博物院", "北京"))

        val folded = MuseumRepository.foldBranchRecords(directory, emptyList())

        assertEquals(directory.keys, folded.info.keys)
        assertTrue(folded.branchNames.isEmpty())
        assertTrue(folded.branchParents.isEmpty())
    }

    @Test
    fun `父馆本身必须留在列表里`() {
        // 曾经的 bug：把 branchParents 一起过滤掉，浙江省博物馆等 5 家馆
        // 从清单和搜索里整体消失。父馆那一行代表「整馆」，必须保留。
        val directory = mapOf(
            "浙江省博物馆" to info("浙江省博物馆", "杭州"),
            "故宫博物院" to info("故宫博物院", "北京"),
        )

        val folded = MuseumRepository.foldBranchRecords(directory, zhejiangBranches)

        assertTrue("父馆不能被折掉", "浙江省博物馆" in folded.info)
        assertTrue("父馆要能被搜索到", folded.info.keys.any { it.contains("浙江省博物馆") })
        assertFalse("父馆不是馆区", folded.branchNames.contains("浙江省博物馆"))
    }
}
