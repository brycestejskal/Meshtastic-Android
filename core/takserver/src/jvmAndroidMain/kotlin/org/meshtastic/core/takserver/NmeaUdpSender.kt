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

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal actual fun createNmeaUdpSender(): NmeaUdpSender = NmeaUdpSender { payload ->
    val bytes = payload.encodeToByteArray()
    DatagramSocket().use { socket ->
        val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName(ATAK_NMEA_HOST), ATAK_NMEA_PORT)
        socket.send(packet)
    }
}
