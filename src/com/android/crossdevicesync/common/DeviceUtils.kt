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
package com.android.crossdevicesync.common

/** Utility to check if the current device is a watch and its specific type. */
interface DeviceUtils {
    /** Returns true if the device is a watch. */
    val isWatch: Boolean

    /** Returns true if the device is a kids watch. */
    fun isKidsWatch(): Boolean

    /** Registers a listener to be notified when the kids watch status changes. */
    fun registerKidsWatchChangeListener(listener: Listener)

    /** Unregisters a previously registered listener. */
    fun unregisterKidsWatchChangeListener(listener: Listener)

    /** Listener for kids watch status changes. */
    interface Listener {
        /** Called when the kids watch status changes. */
        fun onKidsWatchChanged(isKidsWatch: Boolean)
    }
}
