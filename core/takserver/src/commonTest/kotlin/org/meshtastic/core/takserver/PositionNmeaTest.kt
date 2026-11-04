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
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PositionNmeaTest {
    @Test
    fun `checksum matches the published GGA example`() {
        val body = "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"
        assertEquals("47", nmeaChecksum(body))
    }

    @Test
    fun `coordinates convert to degrees and decimal minutes`() {
        assertEquals("4730.000", nmeaCoord(47.5, degreeDigits = 2))
        assertEquals("12200.000", nmeaCoord(122.0, degreeDigits = 3))
        assertEquals("01131.000", nmeaCoord(11.0 + 31.0 / 60.0, degreeDigits = 3))
    }

    @Test
    fun `speed converts from kilometers per hour to knots`() {
        assertEquals(10.0, kmhToKnots(18_520) / 1_000.0, 0.001)
        assertEquals(90.0, trackToDegrees(9_000_000), 0.0001)
    }

    @Test
    fun `sentences carry knots course and a live checksum`() {
        val instant = Instant.fromEpochSeconds(1_762_214_225)
        val payload = nmeaSentences(sampleFix(), instant)
        val lines = payload.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("\$GPGGA,"))
        assertTrue(lines[1].startsWith("\$GPRMC,"))
        assertTrue(lines[0].contains(",4730.000,N,12200.000,W,1,8,1.0,15.0,M,0.0,M,,"))
        assertTrue(lines[1].contains(",10.3,90.0,"))
        lines.forEach { line ->
            val star = line.indexOf('*')
            assertEquals(nmeaChecksum(line.substring(1, star)), line.substring(star + 1))
        }
    }

    @Test
    fun `payload is sent only for a connected fresh local fix`() {
        val now = 1_700_000_000L
        val instant = Instant.fromEpochSeconds(now)
        val node = nodeAt(time = now.toInt() - 30)

        assertNull(
            atakNmeaPayload(
                enabled = false,
                connected = true,
                node = node,
                nowEpochSeconds = now,
                sentenceTime = instant,
            ),
        )
        assertNull(
            atakNmeaPayload(
                enabled = true,
                connected = false,
                node = node,
                nowEpochSeconds = now,
                sentenceTime = instant,
            ),
        )
        assertNull(
            atakNmeaPayload(
                enabled = true,
                connected = true,
                node = null,
                nowEpochSeconds = now,
                sentenceTime = instant,
            ),
        )
        assertNull(
            atakNmeaPayload(
                enabled = true,
                connected = true,
                node = nodeAt(time = 0),
                nowEpochSeconds = now,
                sentenceTime = instant,
            ),
        )
        assertNull(
            atakNmeaPayload(
                enabled = true,
                connected = true,
                node = nodeAt(time = (now - ATAK_NMEA_MAX_FIX_AGE_SECONDS - 1).toInt()),
                nowEpochSeconds = now,
                sentenceTime = instant,
            ),
        )
        val payload =
            atakNmeaPayload(
                enabled = true,
                connected = true,
                node = node,
                nowEpochSeconds = now,
                sentenceTime = instant,
            )
        assertTrue(payload?.startsWith("\$GPGGA,") == true)
    }

    private fun sampleFix() = NmeaFix(
        latitude = 47.5,
        longitude = -122.0,
        altitudeMeters = 15,
        groundSpeedKmh = 19,
        groundTrack = 9_000_000,
        satellites = 8,
        fixEpochSeconds = 1_700_000_000,
    )

    private fun nodeAt(time: Int) = Node(
        num = 1,
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = 475_000_000
                wb.longitude_i = -1_220_000_000
                wb.altitude = 15
                wb.time = time
                wb.sats_in_view = 8
                wb.ground_speed = 19
                wb.ground_track = 9_000_000
            }
            .build(),
    )
}
