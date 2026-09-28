package io.github.devasenan134.cauldron.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// Days travel as "YYYY-MM-DD" (ISO), like on the website.

fun today(): String = LocalDate.now().toString()
fun addDays(day: String, n: Long): String = LocalDate.parse(day).plusDays(n).toString()
fun weekStart(day: String): String = LocalDate.parse(day).with(DayOfWeek.MONDAY).toString()
fun weekdayShort(day: String): String = LocalDate.parse(day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
fun weekdayLong(day: String): String = LocalDate.parse(day).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
fun monthDay(day: String): String = LocalDate.parse(day).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
fun daysAgo(day: String): Long = ChronoUnit.DAYS.between(LocalDate.parse(day), LocalDate.now())
