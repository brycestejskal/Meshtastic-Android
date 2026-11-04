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
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.GeoConstants.HEADING_DEG
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

/** ATAK's Network GNSS listener. Not the local TAK server port. */
internal const val ATAK_NMEA_PORT = 4349

internal const val ATAK_NMEA_HOST = "127.0.0.1"

internal const val ATAK_NMEA_INTERVAL_MS = 5_000L

/** Drop a node fix once it is older than this. Sentences themselves are stamped with the current UTC time. */
internal const val ATAK_NMEA_MAX_FIX_AGE_SECONDS = 300L

private const val KMH_PER_KNOT = 1.852
private const val COORD_MINUTE_SCALE = 1_000
private const val MINUTES_PER_DEGREE = 60
private const val CLOCK_SKEW_SECONDS = 60L

/** Connected-node fix reduced to the fields an ATAK NMEA sentence needs. */
internal data class NmeaFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int,
    val groundSpeedKmh: Int,
    val groundTrack: Int,
    val satellites: Int,
    val fixEpochSeconds: Int,
)

internal fun Node.toNmeaFix(): NmeaFix? {
    val position = validPosition ?: return null
    return NmeaFix(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = position.altitude ?: 0,
        groundSpeedKmh = position.ground_speed ?: 0,
        groundTrack = position.ground_track ?: 0,
        satellites = position.sats_in_view,
        fixEpochSeconds = position.time,
    )
}

/**
 * GGA + RMC for [node], or null when the toggle is off, the radio is disconnected, the fix is missing, or the GPS
 * timestamp is older than [ATAK_NMEA_MAX_FIX_AGE_SECONDS].
 *
 * Sentence time is [sentenceTime] (current UTC), not the GPS timestamp. ATAK marks a quiet NMEA input stale within a
 * few seconds, while the node may only publish a new fix every minute or two.
 */
internal fun atakNmeaPayload(
    enabled: Boolean,
    connected: Boolean,
    node: Node?,
    nowEpochSeconds: Long,
    sentenceTime: Instant,
): String? {
    if (!enabled || !connected) return null
    val fix = node?.toNmeaFix() ?: return null
    if (fix.fixEpochSeconds <= 0) return null
    val age = nowEpochSeconds - fix.fixEpochSeconds.toLong()
    if (age < -CLOCK_SKEW_SECONDS || age > ATAK_NMEA_MAX_FIX_AGE_SECONDS) return null
    return nmeaSentences(fix, sentenceTime)
}

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
        nmeaSentence(
            "GPGGA,$time,$lat,$latHemisphere,$lon,$lonHemisphere,1,${fix.satellites},1.0,$altitude,M,0.0,M,,",
        )
    val rmc =
        nmeaSentence("GPRMC,$time,A,$lat,$latHemisphere,$lon,$lonHemisphere,$knots,$course,$date,,,")
    return "$gga\r\n$rmc\r\n"
}

/** Ground speed on the wire is km/h. RMC wants knots. */
internal fun kmhToKnots(kmh: Int): Double = kmh / KMH_PER_KNOT

/** `ground_track` is degrees × 1e5. */
internal fun trackToDegrees(groundTrack: Int): Double = groundTrack * HEADING_DEG

/** NMEA coordinate field: `ddmm.mmm` or `dddmm.mmm`. */
internal fun nmeaCoord(absDegrees: Double, degreeDigits: Int): String {
    val wholeDegrees = absDegrees.toInt().coerceAtLeast(0)
    val minuteScaled = ((absDegrees - wholeDegrees) * MINUTES_PER_DEGREE * COORD_MINUTE_SCALE).roundToInt()
    val extraDegrees = minuteScaled / (MINUTES_PER_DEGREE * COORD_MINUTE_SCALE)
    val minutes = minuteScaled % (MINUTES_PER_DEGREE * COORD_MINUTE_SCALE)
    val degrees = wholeDegrees + extraDegrees
    val minuteWhole = minutes / COORD_MINUTE_SCALE
    val minuteFraction = minutes % COORD_MINUTE_SCALE
    return degrees.toString().padStart(degreeDigits, '0') +
        minuteWhole.toString().padStart(2, '0') +
        "." +
        minuteFraction.toString().padStart(3, '0')
}

/** XOR of the characters between `$` and `*`, as two uppercase hex digits. */
internal fun nmeaChecksum(bodyAfterDollar: String): String {
    var xor = 0
    for (ch in bodyAfterDollar) {
        xor = xor xor ch.code
    }
    return xor.toString(16).uppercase().padStart(2, '0')
}

private fun nmeaSentence(bodyAfterDollar: String): String = "$$bodyAfterDollar*${nmeaChecksum(bodyAfterDollar)}"

private fun nmeaTime(instant: Instant): String {
    val dateTime = instant.toLocalDateTime(TimeZone.UTC)
    val hundredths = dateTime.nanosecond / 10_000_000
    return "${dateTime.hour.pad2()}${dateTime.minute.pad2()}${dateTime.second.pad2()}.${hundredths.pad2()}"
}

private fun nmeaDate(instant: Instant): String {
    val dateTime = instant.toLocalDateTime(TimeZone.UTC)
    val year = dateTime.year % 100
    return "${dateTime.day.pad2()}${(dateTime.month.ordinal + 1).pad2()}${year.pad2()}"
}

private fun formatTenths(value: Double): String {
    val negative = value < 0
    val scaled = (abs(value) * 10.0).roundToInt()
    val whole = scaled / 10
    val fraction = scaled % 10
    return "${if (negative) "-" else ""}$whole.$fraction"
}

private fun Int.pad2(): String = toString().padStart(2, '0')
