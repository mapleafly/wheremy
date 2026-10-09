package com.wheremy.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 到期窗口与日期换算（`RULES.md` §1.2 说"上界唯一来源"就是这里）。
 *
 * 三个细节少一个就错一天：
 * 1. `+1` 再 `-1`：提前 N 天 = 覆盖到第 N 天那一整天的**最后一毫秒**；
 * 2. `coerceAtLeast(0)`：设置读回来是脏值（负数）也不能把窗口算成过去；
 * 3. 用 `LocalDate` + 系统时区，不用毫秒加减（跨夏令时/时区变更会偏一天）。
 */
fun localStartOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()

fun expireWindowEnd(leadDays: Int, now: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
    localStartOfDay(now.plusDays(leadDays.coerceAtLeast(0).toLong() + 1), zone) - 1

/** 距到期还有几天（已过期为负）。显示分档用它，**与提醒窗口无关**。 */
fun daysUntil(expiresAt: Long, now: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
    val date = Instant.ofEpochMilli(expiresAt).atZone(zone).toLocalDate()
    return java.time.temporal.ChronoUnit.DAYS.between(now, date)
}

/** UTC 零点毫秒 → 本地零点毫秒（日期选择器给的是 UTC 零点，不换算会差一天）。 */
fun utcMillisToLocalStartOfDay(utcMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
