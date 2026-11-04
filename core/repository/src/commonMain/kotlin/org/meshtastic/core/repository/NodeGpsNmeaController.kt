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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope

/**
 * Repeats the connected node's GPS to ATAK for as long as the scope passed to [start] lives. Lives here rather than in
 * core:takserver so core:service can start it without depending on core:takserver, and so a platform with no UDP
 * socket (wasmJs) can supply a no-op.
 */
fun interface NodeGpsNmeaController {
    fun start(scope: CoroutineScope)
}
