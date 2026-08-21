package com.bicy.note.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun Long.formatDateTime(): String = toLocalDateTime().format(dateTimeFormatter)

fun Long.formatDate(): String = toLocalDate().format(dateFormatter)

fun Long.formatTime(): String = toLocalDateTime().format(timeFormatter)

fun LocalDate.toStartMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.toEndMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).plusDays(1).toInstant().toEpochMilli()

fun formatMinuteOfDay(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}