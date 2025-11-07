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

/** Manages and provides airplane mode state. */
interface AirplaneModeController {
    /** Returns the current airplane mode state. */
    fun getAirplaneModeState(): Boolean

    /** Updates the current airplane mode state. */
    fun updateAirplaneModeState(enabled: Boolean)

    /** Registers a listener for airplane mode state changes. */
    fun registerAirplaneModeChangedListener(listener: Listener)

    /** Unregisters a listener for airplane mode state changes. */
    fun unregisterAirplaneModeChangedListener(listener: Listener)

    /** Listener for airplane mode state changes. */
    interface Listener {
        /** Called when the airplane mode state changes. */
        fun onAirplaneModeChanged(enabled: Boolean)
    }
}
