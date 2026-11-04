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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import kotlin.time.Instant

/** Repeats the connected node's GPS to ATAK for as long as [scope] lives. */
fun interface NodeGpsNmeaController {
    fun start(scope: CoroutineScope)
}

/**
 * While the TAK setting is on and the radio is connected, sends the local node's fix to `127.0.0.1:4349` every
 * [ATAK_NMEA_INTERVAL_MS]. Independent of the local TAK server: that server publishes map contacts, and this feed is
 * what ATAK's Location Input Manager accepts as its own GPS.
 */
class NodeGpsNmeaForwarder(
    private val enabled: StateFlow<Boolean>,
    private val connectionState: StateFlow<ConnectionState>,
    private val ourNodeInfo: StateFlow<Node?>,
    private val dispatchers: CoroutineDispatchers,
    private val sender: NmeaUdpSender = createNmeaUdpSender(),
    private val nowEpochSeconds: () -> Long = { nowSeconds },
) : NodeGpsNmeaController {
    override fun start(scope: CoroutineScope) {
        scope.launch(dispatchers.io) {
            while (isActive) {
                val now = nowEpochSeconds()
                val payload =
                    atakNmeaPayload(
                        enabled = enabled.value,
                        connected = connectionState.value is ConnectionState.Connected,
                        node = ourNodeInfo.value,
                        nowEpochSeconds = now,
                        sentenceTime = Instant.fromEpochSeconds(now),
                    )
                if (payload != null) {
                    safeCatching { sender.send(payload) }
                        .onFailure { Logger.w { "Could not send node GPS NMEA to ATAK" } }
                }
                delay(ATAK_NMEA_INTERVAL_MS)
            }
        }
    }
}
