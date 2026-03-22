/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.wifi.tether

import android.content.Context
import android.net.MacAddress
import android.net.TetheredClient
import android.net.TetheringManager
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiClient
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerExecutor
import android.os.Looper
import com.android.settings.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectedDevice(
    val macAddress: String,
    val hostname: String?,
    val ipAddress: String?,
)

data class TetherAdvancedState(
    val hiddenSsid: Boolean = false,
    val shutdownTimeout: Long = SoftApConfiguration.DEFAULT_TIMEOUT,
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val blockedDevices: List<String> = emptyList(),
)

class WifiTetherAdvancedController(context: Context) {

    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val tetheringManager = context.getSystemService(TetheringManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = HandlerExecutor(mainHandler)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<TetherAdvancedState> = _state.asStateFlow()

    private var softApClients: List<WifiClient> = emptyList()
    private var tetheredClients: Collection<TetheredClient> = emptyList()

    val timeoutOptions: List<Pair<String, String>> = listOf(
        "-1" to context.getString(R.string.wifi_hotspot_shutdown_timeout_default),
        TIMEOUT_5_MIN.toString() to context.getString(R.string.wifi_hotspot_shutdown_timeout_5min),
        TIMEOUT_10_MIN.toString() to context.getString(R.string.wifi_hotspot_shutdown_timeout_10min),
        TIMEOUT_30_MIN.toString() to context.getString(R.string.wifi_hotspot_shutdown_timeout_30min),
        TIMEOUT_1_HOUR.toString() to context.getString(R.string.wifi_hotspot_shutdown_timeout_1hour),
    )

    private val softApCallback = object : WifiManager.SoftApCallback {
        override fun onConnectedClientsChanged(clients: List<WifiClient>) {
            softApClients = clients
            updateConnectedDevices()
        }

        override fun onStateChanged(state: Int, failureReason: Int) {
            if (state != WifiManager.WIFI_AP_STATE_ENABLED) {
                softApClients = emptyList()
                updateConnectedDevices()
            }
        }
    }

    private val tetheringCallback = object : TetheringManager.TetheringEventCallback {
        override fun onClientsChanged(clients: Collection<TetheredClient>) {
            tetheredClients = clients
            updateConnectedDevices()
        }
    }

    fun start() {
        _state.value = readState()
        wifiManager.registerSoftApCallback(mainExecutor, softApCallback)
        tetheringManager.registerTetheringEventCallback(mainExecutor, tetheringCallback)
    }

    fun stop() {
        wifiManager.unregisterSoftApCallback(softApCallback)
        tetheringManager.unregisterTetheringEventCallback(tetheringCallback)
    }

    fun setHiddenSsid(hidden: Boolean) {
        val config = SoftApConfiguration.Builder(wifiManager.softApConfiguration)
            .setHiddenSsid(hidden)
            .build()
        if (wifiManager.setSoftApConfiguration(config)) {
            _state.value = _state.value.copy(hiddenSsid = hidden)
        }
    }

    fun setShutdownTimeout(timeoutMillis: Long) {
        val config = SoftApConfiguration.Builder(wifiManager.softApConfiguration)
            .setShutdownTimeoutMillis(timeoutMillis)
            .build()
        if (wifiManager.setSoftApConfiguration(config)) {
            _state.value = _state.value.copy(shutdownTimeout = timeoutMillis)
        }
    }

    fun blockClient(mac: String) {
        val macAddress = MacAddress.fromString(mac)
        val config = wifiManager.softApConfiguration
        val blocked = ArrayList(config.blockedClientList)
        if (!blocked.contains(macAddress)) {
            blocked.add(macAddress)
        }
        val newConfig = SoftApConfiguration.Builder(config)
            .setBlockedClientList(blocked)
            .build()
        if (wifiManager.setSoftApConfiguration(newConfig)) {
            _state.value = _state.value.copy(
                blockedDevices = blocked.map { it.toString() }
            )
        }
    }

    fun unblockClient(mac: String) {
        val macAddress = MacAddress.fromString(mac)
        val config = wifiManager.softApConfiguration
        val blocked = ArrayList(config.blockedClientList)
        blocked.remove(macAddress)
        val newConfig = SoftApConfiguration.Builder(config)
            .setBlockedClientList(blocked)
            .build()
        if (wifiManager.setSoftApConfiguration(newConfig)) {
            _state.value = _state.value.copy(
                blockedDevices = blocked.map { it.toString() }
            )
        }
    }

    fun timeoutLabel(timeout: Long): String =
        timeoutOptions.find { it.first == timeout.toString() }?.second
            ?: timeoutOptions.first().second

    private fun updateConnectedDevices() {
        val devices = softApClients.map { client ->
            val mac = client.macAddress.toString()
            val tethered = tetheredClients
                .firstOrNull { it.macAddress == client.macAddress }
            val addressInfo = tethered?.addresses?.firstOrNull()
            ConnectedDevice(
                macAddress = mac,
                hostname = tethered?.addresses?.firstNotNullOfOrNull { it.hostname },
                ipAddress = addressInfo?.address?.address?.hostAddress,
            )
        }
        _state.value = _state.value.copy(connectedDevices = devices)
    }

    private fun readState(): TetherAdvancedState {
        val config = wifiManager.softApConfiguration
        return TetherAdvancedState(
            hiddenSsid = config.isHiddenSsid,
            shutdownTimeout = config.shutdownTimeoutMillis,
            blockedDevices = config.blockedClientList.map { it.toString() },
        )
    }

    companion object {
        private const val TIMEOUT_5_MIN = 300_000L
        private const val TIMEOUT_10_MIN = 600_000L
        private const val TIMEOUT_30_MIN = 1_800_000L
        private const val TIMEOUT_1_HOUR = 3_600_000L
    }
}
