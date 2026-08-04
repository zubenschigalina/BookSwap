package com.example.bookswap.ui.exchange

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Date/time formatting for the exchange screens. `minSdk` is 24, so the codebase stays on
 * [java.util.Date] / [Calendar] rather than `java.time` (which would need desugaring).
 */
private val dateTimeFormat: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

fun Date.formatMeetingDateTime(): String = dateTimeFormat.format(this)

/** Combines a date-picker's UTC-midnight millis with a time-picker's hour/minute. */
fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Date {
    // Material's DatePicker reports the selection as UTC midnight, so read the calendar
    // fields back out in UTC before rebuilding the instant in the device's zone —
    // otherwise a user east/west of UTC can land on the previous or next day.
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateMillis
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
}

/**
 * Human-readable countdown used on the cancellation notice, e.g. "1h 20m".
 * Returns null once the meeting time has passed.
 */
fun timeUntilLabel(meetingTime: Date, now: Date = Date()): String? {
    val millis = meetingTime.time - now.time
    if (millis <= 0) return null
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
