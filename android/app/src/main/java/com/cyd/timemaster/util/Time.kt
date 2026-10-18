package com.cyd.timemaster.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val ZONE: ZoneId = ZoneId.systemDefault()

fun newId(): String = UUID.randomUUID().toString()

fun nowIso(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

fun today(): String = LocalDate.now().format(DATE_FMT)

fun startOfDay(date: LocalDate = LocalDate.now()): Long =
    date.atStartOfDay(ZONE).toInstant().toEpochMilli()

fun endOfDay(date: LocalDate = LocalDate.now()): Long =
    date.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()

fun startOfWeek(date: LocalDate = LocalDate.now()): Long =
    startOfDay(date.with(DayOfWeek.MONDAY))

fun startOfMonth(date: LocalDate = LocalDate.now()): Long =
    startOfDay(date.withDayOfMonth(1))

fun epochToLocalDateTime(ms: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE)

fun epochToDateStr(ms: Long): String = epochToLocalDateTime(ms).toLocalDate().format(DATE_FMT)

/** "1小时23分" / "45分" / "30秒" */
fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分${s}秒"
        else -> "${s}秒"
    }
}

/** 计时中显示用 "HH:mm:ss" */
fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d:%02d".format(totalSec / 3600, totalSec % 3600 / 60, totalSec % 60)
}

/** "HH:mm" → 今天的 LocalTime；解析失败返回 null */
fun parseHm(text: String): LocalTime? = try {
    LocalTime.parse(text.trim())
} catch (_: Exception) {
    null
}

fun localTimeToEpochMillisToday(time: LocalTime): Long =
    time.atDate(LocalDate.now()).atZone(ZONE).toInstant().toEpochMilli()
