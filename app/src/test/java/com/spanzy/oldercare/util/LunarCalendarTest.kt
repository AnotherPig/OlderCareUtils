package com.spanzy.oldercare.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCalendarTest {

    @Test
    fun `测试2026年3月30日农历转换`() {
        val lunar = LunarCalendar.toLunar(2026, 3, 30)
        // 2026年3月30日是农历二月十二
        assertEquals("二月十二", lunar)
    }

    @Test
    fun `测试2024年2月10日农历新年`() {
        val lunar = LunarCalendar.toLunar(2024, 2, 10)
        assertEquals("正月初一", lunar)
    }

    @Test
    fun `测试2023年1月22日农历新年`() {
        val lunar = LunarCalendar.toLunar(2023, 1, 22)
        assertEquals("正月初一", lunar)
    }

    @Test
    fun `测试闰月处理 - 2023年闰二月`() {
        // 2023年有闰二月，验证闰月和正常二月的区分
        val normalFeb = LunarCalendar.toLunar(2023, 2, 20)
        val leapFeb = LunarCalendar.toLunar(2023, 3, 22) // 闰二月初一
        // 正常二月和闰二月应不同
        org.junit.Assert.assertNotEquals(normalFeb, leapFeb)
    }

    @Test
    fun `测试范围边界 - 1900年1月31日`() {
        val lunar = LunarCalendar.toLunar(1900, 1, 31)
        assertEquals("正月初一", lunar)
    }

    @Test
    fun `测试范围边界 - 2100年12月31日`() {
        val lunar = LunarCalendar.toLunar(2100, 12, 31)
        // 应返回有效农历日期
        assertTrue(lunar.isNotEmpty())
    }
}
