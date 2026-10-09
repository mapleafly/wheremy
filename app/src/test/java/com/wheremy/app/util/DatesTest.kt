package com.wheremy.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 到期窗口（`RULES.md` §1.2）：上界只有这一个来源，三个细节少一个就错一天。
 */
class DatesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 10, 9)

    @Test
    fun `提前 N 天覆盖到第 N 天的最后一毫秒`() {
        val end = expireWindowEnd(leadDays = 3, now = today, zone = zone)

        // 第 3 天那一整天的最后一毫秒
        val day3End = localStartOfDay(today.plusDays(4), zone) - 1
        assertEquals(day3End, end)

        // 正好"提前 3 天到期"的那件必须落进窗口（直接 plusDays(3) 的零点会把它漏掉）
        val exactlyThreeDays = localStartOfDay(today.plusDays(3), zone)
        assertTrue("提前 3 天到期的那件要在窗口里", exactlyThreeDays <= end)
    }

    @Test
    fun `已过期的同样落进窗口`() {
        val end = expireWindowEnd(leadDays = 3, now = today, zone = zone)
        val yesterday = localStartOfDay(today.minusDays(1), zone)
        assertTrue("已过期也算'该看了'", yesterday <= end)
    }

    @Test
    fun `脏值负数不会把窗口算到过去`() {
        val end = expireWindowEnd(leadDays = -5, now = today, zone = zone)
        val todayEnd = localStartOfDay(today.plusDays(1), zone) - 1
        assertEquals("coerceAtLeast(0) 之后就是'今天结束'", todayEnd, end)
    }

    @Test
    fun `daysUntil 的三个区间`() {
        assertEquals(-3L, daysUntil(localStartOfDay(today.minusDays(3), zone), today, zone))
        assertEquals(0L, daysUntil(localStartOfDay(today, zone), today, zone))
        assertEquals(28L, daysUntil(localStartOfDay(today.plusDays(28), zone), today, zone))
    }

    @Test
    fun `UTC 零点换回本地零点`() {
        // 2026-10-09T00:00:00Z 在东八区是当天 08:00，换回来要落在这天本地零点
        val utc = java.time.Instant.parse("2026-10-09T00:00:00Z").toEpochMilli()
        val local = utcMillisToLocalStartOfDay(utc, zone)
        assertEquals(localStartOfDay(LocalDate.of(2026, 10, 9), zone), local)
    }
}
