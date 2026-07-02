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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.axion.compose.preferences.BasePreference
import com.android.axion.compose.preferences.ListPreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.PreferencePosition
import com.android.axion.compose.preferences.SliderPreference
import com.android.axion.compose.preferences.SwitchPreference
import com.android.axion.compose.theme.AxionTheme
import com.android.settings.R

class WifiTetherAdvancedSettings : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AxionTheme {
                AdvancedTetherScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.wifi_hotspot_advanced_title)
    }

    @Composable
    private fun AdvancedTetherScreen() {
        val context = LocalContext.current
        val controller = remember { WifiTetherAdvancedController(context) }
        val state by controller.state.collectAsStateWithLifecycle()

        DisposableEffect(controller) {
            controller.start()
            onDispose { controller.stop() }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                item {
                    PreferenceGroup {
                        item {
                            ListPreference(
                                title = stringResource(R.string.wifi_hotspot_shutdown_timeout_title),
                                summary = controller.timeoutLabel(state.shutdownTimeout),
                                options = controller.timeoutOptions,
                                value = state.shutdownTimeout.toString(),
                                onValueChange = { controller.setShutdownTimeout(it.toLong()) },
                            )
                        }
                        item {
                            SwitchPreference(
                                title = stringResource(R.string.wifi_hotspot_hidden_ssid_title),
                                summary = stringResource(R.string.wifi_hotspot_hidden_ssid_summary),
                                checked = state.hiddenSsid,
                                onCheckedChange = { controller.setHiddenSsid(it) },
                            )
                        }
                        if (state.supportClientLimit) {
                            item {
                                SliderPreference(
                                    title = stringResource(R.string.wifi_hotspot_client_limit_title),
                                    summary = "",
                                    value = state.maxNumberOfClients.toFloat(),
                                    onValueChange = { controller.updateMaxNumberOfClients(it.toInt()) },
                                    onValueChangeFinished = { controller.setMaxNumberOfClients(state.maxNumberOfClients) },
                                    valueRange = 1f..state.maxSupportedClients.toFloat(),
                                    displayValue = state.maxNumberOfClients.toString(),
                                )
                            }
                        }
                    }
                }

                item {
                    PreferenceGroup(
                        title = stringResource(R.string.wifi_hotspot_connected_devices_title),
                    ) {
                        if (state.connectedDevices.isEmpty()) {
                            item {
                                BasePreference(
                                    title = stringResource(R.string.wifi_hotspot_connected_devices_none),
                                    icon = Icons.Outlined.Devices,
                                    position = PreferencePosition.Single,
                                )
                            }
                        } else {
                            state.connectedDevices.forEachIndexed { index, device ->
                                item {
                                    val displayName = device.hostname
                                        ?: device.ipAddress
                                        ?: stringResource(R.string.wifi_hotspot_connected_devices_unknown)
                                    BasePreference(
                                        title = displayName,
                                        summary = device.macAddress,
                                        icon = Icons.Outlined.Devices,
                                        widget = {
                                            FilledTonalIconButton(
                                                onClick = { controller.blockClient(device.macAddress) },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Block,
                                                    contentDescription = stringResource(R.string.wifi_hotspot_block_client),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.blockedDevices.isNotEmpty()) {
                    item {
                        PreferenceGroup(
                            title = stringResource(R.string.wifi_hotspot_blocked_devices_title),
                        ) {
                            state.blockedDevices.forEachIndexed { index, mac ->
                                item {
                                    BasePreference(
                                        title = mac,
                                        icon = Icons.Outlined.Block,
                                        widget = {
                                            FilledTonalIconButton(
                                                onClick = { controller.unblockClient(mac) },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.LockOpen,
                                                    contentDescription = stringResource(R.string.wifi_hotspot_unblock_client),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
