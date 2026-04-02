package com.spanzy.oldercare.util

/**
 * 农历算法工具类
 * 支持 1900-2100 年的公历转农历
 * 基于查找表算法，数据来源于标准天文年历
 *
 * 算法参考: coolerfall/Android-LunarView
 */
object LunarCalendar {

    // 农历数据表 (1900-2100)
    // 每个元素压缩存储一年信息：
    // - 低4位：闰月月份 (0xF表示无闰月)
    // - 5-16位：每月大小 (1为大月30天，0为小月29天)，从正月到十二月
    // - 17-20位：闰月大小 (0xF为30天，否则29天)
    private val lunarInfo = intArrayOf(
        0x4bd8, 0x4ae0, 0xa570, 0x54d5, 0xd260, 0xd950, 0x5554, 0x56af, 0x9ad0, 0x55d2, // 1900-1909
        0x4ae0, 0xa5b6, 0xa4d0, 0xd250, 0xd295, 0xb54f, 0xd6a0, 0xada2, 0x95b0, 0x4977, // 1910-1919
        0x497f, 0xa4b0, 0xb4b5, 0x6a50, 0x6d40, 0xab54, 0x2b6f, 0x9570, 0x52f2, 0x4970, // 1920-1929
        0x6566, 0xd4a0, 0xea50, 0x6a95, 0x5adf, 0x2b60, 0x86e3, 0x92ef, 0xc8d7, 0xc95f, // 1930-1939
        0xd4a0, 0xd8a6, 0xb55f, 0x56a0, 0xa5b4, 0x25df, 0x92d0, 0xd2b2, 0xa950, 0xb557, // 1940-1949
        0x6ca0, 0xb550, 0x5355, 0x4daf, 0xa5b0, 0x4573, 0x52bf, 0xa9a8, 0xe950, 0x6aa0, // 1950-1959
        0xaea6, 0xab50, 0x4b60, 0xaae4, 0xa570, 0x5260, 0xf263, 0xd950, 0x5b57, 0x56a0, // 1960-1969
        0x96d0, 0x4dd5, 0x4ad0, 0xa4d0, 0xd4d4, 0xd250, 0xd558, 0xb540, 0xb6a0, 0x95a6, // 1970-1979
        0x95bf, 0x49b0, 0xa974, 0xa4b0, 0xb27a, 0x6a50, 0x6d40, 0xaf46, 0xab60, 0x9570, // 1980-1989
        0x4af5, 0x4970, 0x64b0, 0x74a3, 0xea50, 0x6b58, 0x5ac0, 0xab60, 0x96d5, 0x92e0, // 1990-1999
        0xc960, 0xd954, 0xd4a0, 0xda50, 0x7552, 0x56a0, 0xabb7, 0x25d0, 0x92d0, 0xcab5, // 2000-2009
        0xa950, 0xb4a0, 0xbaa4, 0xad50, 0x55d9, 0x4ba0, 0xa5b0, 0x5176, 0x52bf, 0xa930, // 2010-2019
        0x7954, 0x6aa0, 0xad50, 0x5b52, 0x4b60, 0xa6e6, 0xa4e0, 0xd260, 0xea65, 0xd530, // 2020-2029
        0x5aa0, 0x76a3, 0x96d0, 0x4afb, 0x4ad0, 0xa4d0, 0xd0b6, 0xd25f, 0xd520, 0xdd45, // 2030-2039
        0xb5a0, 0x56d0, 0x55b2, 0x49b0, 0xa577, 0xa4b0, 0xaa50, 0xb255, 0x6d2f, 0xada0, // 2040-2049
        0x4b63, 0x937f, 0x49f8, 0x4970, 0x64b0, 0x68a6, 0xea5f, 0x6b20, 0xa6c4, 0xaaef, // 2050-2059
        0x92e0, 0xd2e3, 0xc960, 0xd557, 0xd4a0, 0xda50, 0x5d55, 0x56a0, 0xa6d0, 0x55d4, // 2060-2069
        0x52d0, 0xa9b8, 0xa950, 0xb4a0, 0xb6a6, 0xad50, 0x55a0, 0xaba4, 0xa5b0, 0x52b0, // 2070-2079
        0xb273, 0x6930, 0x7337, 0x6aa0, 0xad50, 0x4b55, 0x4b6f, 0xa570, 0x54e4, 0xd260, // 2080-2089
        0xe968, 0xd520, 0xdaa0, 0x6aa6, 0x56df, 0x4ae0, 0xa9d4, 0xa4d0, 0xd150, 0xf252, // 2090-2099
        0xd520  // 2100
    )

    // 农历月份名称
    private val chineseMonths = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    // 农历日期名称（1-30）
    private val chineseDays = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    /**
     * 公历转农历
     * @param year 公历年
     * @param month 公历月 (1-12)
     * @param day 公历日
     * @return 农历字符串，格式如 "三月初二"，闰月加"闰"字如 "闰二月十五"
     */
    fun toLunar(year: Int, month: Int, day: Int): String {
        // 参数校验
        if (year < 1900 || year > 2100) {
            return "超出范围"
        }

        // 基准日期：1900年1月31日是农历正月初一
        // 使用UTC时区避免时区问题
        val baseDate = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
        baseDate.set(1900, 0, 31, 0, 0, 0)
        baseDate.set(java.util.Calendar.MILLISECOND, 0)

        val targetDate = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
        targetDate.set(year, month - 1, day, 0, 0, 0)
        targetDate.set(java.util.Calendar.MILLISECOND, 0)

        // 计算天数差（毫秒转天数）
        var offset = ((targetDate.timeInMillis - baseDate.timeInMillis) / 86400000).toInt()

        // 计算农历年份
        var lunarYear = 1900
        var daysInLunarYear = getLunarYearDays(lunarYear)
        while (lunarYear < 2100 && offset >= daysInLunarYear) {
            offset -= daysInLunarYear
            lunarYear++
            daysInLunarYear = getLunarYearDays(lunarYear)
        }

        // offset 现在是在当前农历年内的天数

        // 计算农历月份
        val leapMonth = getLunarLeapMonth(lunarYear)
        var lunarMonth = 1
        var isLeap = false
        var leapDec = false
        var daysInMonth = 0

        while (lunarMonth < 13 && offset > 0) {
            // 获取当月天数
            if (isLeap && leapDec) {
                daysInMonth = getLunarLeapDays(lunarYear)
                leapDec = false
            } else {
                daysInMonth = getLunarMonthDays(lunarYear, lunarMonth)
            }

            if (offset < daysInMonth) {
                break
            }

            offset -= daysInMonth

            if (leapMonth == lunarMonth && !isLeap) {
                leapDec = true
                isLeap = true
            } else {
                if (isLeap) {
                    isLeap = false
                }
                lunarMonth++
            }
        }

        val lunarDay = offset + 1

        // 格式化输出
        val monthStr = if (isLeap) "闰${chineseMonths[lunarMonth - 1]}" else chineseMonths[lunarMonth - 1]
        val dayStr = chineseDays[lunarDay - 1]

        return "$monthStr$dayStr"
    }

    /**
     * 获取闰月月份
     * @param lunarYear 农历年
     * @return 闰月月份（0表示无闰月）
     */
    private fun getLunarLeapMonth(lunarYear: Int): Int {
        val leapMonth = lunarInfo[lunarYear - 1900] and 0xf
        return if (leapMonth == 0xf) 0 else leapMonth
    }

    /**
     * 获取闰月天数
     * @param lunarYear 农历年
     * @return 闰月天数（0表示无闰月）
     */
    private fun getLunarLeapDays(lunarYear: Int): Int {
        return if (lunarYear < 2100 && getLunarLeapMonth(lunarYear) > 0) {
            // 检查下一年对应的bit是否为0xF
            if ((lunarInfo[lunarYear - 1899] and 0xf) == 0xf) 30 else 29
        } else {
            0
        }
    }

    /**
     * 获取农历年份总天数
     * @param lunarYear 农历年
     * @return 总天数
     */
    private fun getLunarYearDays(lunarYear: Int): Int {
        // 农历年至少有 12 * 29 = 348 天
        var totalDays = 348

        // 检查每个月是否为大月（30天）
        var i = 0x8000
        while (i > 0x8) {
            totalDays += if ((lunarInfo[lunarYear - 1900] and i) != 0) 1 else 0
            i = i shr 1
        }

        // 加上闰月天数
        return totalDays + getLunarLeapDays(lunarYear)
    }

    /**
     * 获取农历月份天数
     * @param lunarYear 农历年
     * @param lunarMonth 农历月份 (1-12)
     * @return 月份天数
     */
    private fun getLunarMonthDays(lunarYear: Int, lunarMonth: Int): Int {
        return if ((lunarInfo[lunarYear - 1900] and (0x10000 shr lunarMonth)) != 0) 30 else 29
    }
}
