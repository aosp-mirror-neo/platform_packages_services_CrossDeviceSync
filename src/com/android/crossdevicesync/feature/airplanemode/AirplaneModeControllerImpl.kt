/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.crossdevicesync.feature.airplanemode

import android.content.Context
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.provider.Settings
import android.util.Log
import com.android.crossdevicesync.dagger.qualifier.Main
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton
import java.util.concurrent.Executor
import javax.inject.Inject

/** Implementation of [AirplaneModeController]. */
@SyncServiceSingleton
class AirplaneModeControllerImpl
@Inject
internal constructor(private val context: Context, @param:Main private val executor: Executor) :
    AirplaneModeController {

    private val listeners = mutableListOf<AirplaneModeController.Listener>()
    private val contentObserver =
        object : ContentObserver(executor, /* unused= */ 0) {
            override fun onChange(selfChange: Boolean) {
                val apmEnabled = getAirplaneModeState()
                Log.d(TAG, "Local APM state changed to $apmEnabled")
                listeners.forEach { it.onAirplaneModeChanged(apmEnabled) }
            }
        }

    override fun getAirplaneModeState(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1

    override fun updateAirplaneModeState(enabled: Boolean) {
        context.getSystemService(ConnectivityManager::class.java).setAirplaneMode(enabled)
    }

    override fun registerAirplaneModeChangedListener(listener: AirplaneModeController.Listener) {
        if (listeners.isEmpty()) {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                /* notifyForDescendants= */ false,
                contentObserver,
            )
        }
        listeners.add(listener)
    }

    override fun unregisterAirplaneModeChangedListener(listener: AirplaneModeController.Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            context.contentResolver.unregisterContentObserver(contentObserver)
        }
    }

    companion object {
        private const val TAG = "AirplaneModeController"
    }
}
