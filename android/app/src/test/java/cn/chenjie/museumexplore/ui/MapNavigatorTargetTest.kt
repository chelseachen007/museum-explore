package cn.chenjie.museumexplore.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导航关键词的拼法。
 *
 * 用户的要求：没有具体地址的馆，就用博物馆名去导航。
 * 所以缺地址不是错误状态，只需要把关键词收敛成馆名。
 */
class MapNavigatorTargetTest {

    @Test
    fun `有地址时馆名加地址`() {
        assertEquals(
            "浙江省博物馆 杭州市西湖区孤山路25号",
            MapNavigator.searchTarget("浙江省博物馆", "杭州市西湖区孤山路25号"),
        )
    }

    @Test
    fun `没地址时只用馆名`() {
        assertEquals("上海外滩美术馆", MapNavigator.searchTarget("上海外滩美术馆", null))
    }

    @Test
    fun `地址是空串或纯空白也退回馆名`() {
        assertEquals("故宫博物院", MapNavigator.searchTarget("故宫博物院", ""))
        assertEquals("故宫博物院", MapNavigator.searchTarget("故宫博物院", "   "))
    }

    @Test
    fun `地址里已经含馆名就不重复拼`() {
        assertEquals(
            "浙江省博物馆（孤山路25号）",
            MapNavigator.searchTarget("浙江省博物馆", "浙江省博物馆（孤山路25号）"),
        )
    }

    @Test
    fun `前后空白会被裁掉`() {
        assertEquals("南京博物院", MapNavigator.searchTarget("  南京博物院  ", null))
        assertEquals(
            "南京博物院 中山东路321号",
            MapNavigator.searchTarget(" 南京博物院 ", " 中山东路321号 "),
        )
    }
}
