/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.takserver

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.meshtastic.core.model.util.GeoConstants.HEADING_DEG
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

private const val KMH_PER_KNOT = 1.852
private const val MINUTES_PER_DEGREE = 60

/** Minutes carry three decimal places: `mm.mmm`. */
private const val COORD_MINUTE_SCALE = 1_000
private const val COORD_MINUTE_FRACTION_DIGITS = 3
private const val HEX_RADIX = 16
private const val NANOS_PER_HUNDREDTH_SECOND = 10_000_000
private const val YEARS_PER_CENTURY = 100
private const val TENTHS = 10

/** `$GPGGA` and `$GPRMC`, each terminated with CR LF. Speed is knots; course is degrees. */
internal fun nmeaSentences(fix: NmeaFix, sentenceTime: Instant): String {
    val latHemisphere = if (fix.latitude >= 0) "N" else "S"
    val lonHemisphere = if (fix.longitude >= 0) "E" else "W"
    val lat = nmeaCoord(abs(fix.latitude), degreeDigits = 2)
    val lon = nmeaCoord(abs(fix.longitude), degreeDigits = 3)
    val time = nmeaTime(sentenceTime)
    val date = nmeaDate(sentenceTime)
    val altitude = formatTenths(fix.altitudeMeters.toDouble())
    val knots = formatTenths(kmhToKnots(fix.groundSpeedKmh))
    val course = formatTenths(trackToDegrees(fix.groundTrack))
    val gga =
        nmeaSentence("GPGGA,$time,$lat,$latHemisphere,$lon,$lonHemisphere,1,${fix.satellites},1.0,$altitude,M,0.0,M,,")
    val rmc = nmeaSentence("GPRMC,$time,A,$lat,$latHemisphere,$lon,$lonHemisphere,$knots,$course,$date,,,")
    return "$gga\r\n$rmc\r\n"
}

/** Ground speed on the wire is km/h. RMC wants knots. */
internal fun kmhToKnots(kmh: Int): Double = kmh / KMH_PER_KNOT

/** `ground_track` is degrees × 1e5. */
internal fun trackToDegrees(groundTrack: Int): Double = groundTrack * HEADING_DEG

/** NMEA coordinate field: `ddmm.mmm` or `dddmm.mmm`. */
internal fun nmeaCoord(absDegrees: Double, degreeDigits: Int): String {
    val scaledMinutesPerDegree = MINUTES_PER_DEGREE * COORD_MINUTE_SCALE
    val wholeDegrees = absDegrees.toInt().coerceAtLeast(0)
    val minuteScaled = ((absDegrees - wholeDegrees) * scaledMinutesPerDegree).roundToInt()
    val degrees = wholeDegrees + minuteScaled / scaledMinutesPerDegree
    val minutes = minuteScaled % scaledMinutesPerDegree
    val minuteWhole = minutes / COORD_MINUTE_SCALE
    val minuteFraction = minutes % COORD_MINUTE_SCALE
    return degrees.toString().padStart(degreeDigits, '0') +
        minuteWhole.pad2() +
        "." +
        minuteFraction.toString().padStart(COORD_MINUTE_FRACTION_DIGITS, '0')
}

/** XOR of the characters between `$` and `*`, as two uppercase hex digits. */
internal fun nmeaChecksum(bodyAfterDollar: String): String {
    var xor = 0
    for (ch in bodyAfterDollar) {
        xor = xor xor ch.code
    }
    return xor.toString(HEX_RADIX).uppercase().padStart(2, '0')
}

private fun nmeaSentence(bodyAfterDollar: String): String = "$$bodyAfterDollar*${nmeaChecksum(bodyAfterDollar)}"

private fun nmeaTime(instant: Instant): String {
    val dateTime = instant.toLocalDateTime(TimeZone.UTC)
    val hundredths = dateTime.nanosecond / NANOS_PER_HUNDREDTH_SECOND
    return "${dateTime.hour.pad2()}${dateTime.minute.pad2()}${dateTime.second.pad2()}.${hundredths.pad2()}"
}

private fun nmeaDate(instant: Instant): String {
    val dateTime = instant.toLocalDateTime(TimeZone.UTC)
    val year = dateTime.year % YEARS_PER_CENTURY
    return "${dateTime.day.pad2()}${(dateTime.month.ordinal + 1).pad2()}${year.pad2()}"
}

private fun formatTenths(value: Double): String {
    val negative = value < 0
    val scaled = (abs(value) * TENTHS).roundToInt()
    val whole = scaled / TENTHS
    val fraction = scaled % TENTHS
    return "${if (negative) "-" else ""}$whole.$fraction"
}

private fun Int.pad2(): String = toString().padStart(2, '0')
