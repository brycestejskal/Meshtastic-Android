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

import org.meshtastic.core.model.Node
import kotlin.time.Instant

/** ATAK's Network GNSS listener. Not the local TAK server port. */
internal const val ATAK_NMEA_PORT = 4349

internal const val ATAK_NMEA_HOST = "127.0.0.1"

internal const val ATAK_NMEA_INTERVAL_MS = 5_000L

/** Drop a node fix once it is older than this. Sentences themselves are stamped with the current UTC time. */
internal const val ATAK_NMEA_MAX_FIX_AGE_SECONDS = 300L

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
): String? = node
    ?.toNmeaFix()
    ?.takeIf { enabled && connected && it.isFreshAt(nowEpochSeconds) }
    ?.let { nmeaSentences(it, sentenceTime) }

private fun NmeaFix.isFreshAt(nowEpochSeconds: Long): Boolean {
    val age = nowEpochSeconds - fixEpochSeconds
    return fixEpochSeconds > 0 && age in -CLOCK_SKEW_SECONDS..ATAK_NMEA_MAX_FIX_AGE_SECONDS
}
