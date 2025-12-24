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

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.Settings
import android.provider.Settings.Global.Wearable.TETHERED_CONFIG_RESTRICTED
import android.provider.Settings.Global.Wearable.TETHERED_CONFIG_STANDALONE
import android.provider.Settings.Global.Wearable.TETHERED_CONFIG_UNKNOWN
import android.provider.Settings.Global.Wearable.TETHER_CONFIG_STATE
import android.util.Log
import com.android.crossdevicesync.dagger.qualifier.Main
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of [WatchChecker]. */
@Singleton
class WatchCheckerImpl
@Inject
internal constructor(private val mContext: Context, @Main executor: Executor) : WatchChecker {

    private val listeners = mutableListOf<WatchChecker.Listener>()
    private val contentObserver =
        object : ContentObserver(executor, /* unused= */ 0) {
            override fun onChange(selfChange: Boolean) {
                val isKids = isKidsWatch()
                if (DEBUG) {
                    Log.d(TAG, "Kids watch state changed to $isKids")
                }
                listeners.forEach { it.onKidsWatchChanged(isKids) }
            }
        }

    override val isWatch: Boolean =
        mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    override fun isKidsWatch(): Boolean {
        if (!isWatch) {
            return false
        }
        val config =
            Settings.Global.getInt(
                mContext.contentResolver,
                TETHER_CONFIG_STATE,
                TETHERED_CONFIG_UNKNOWN,
            )
        return config == TETHERED_CONFIG_RESTRICTED || config == TETHERED_CONFIG_STANDALONE
    }

    override fun registerKidsWatchChangeListener(listener: WatchChecker.Listener) {
        if (!isWatch) {
            return
        }
        if (listeners.isEmpty()) {
            mContext.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(TETHER_CONFIG_STATE),
                /* notifyForDescendants= */ false,
                contentObserver,
            )
        }
        listeners.add(listener)
    }

    override fun unregisterKidsWatchChangeListener(listener: WatchChecker.Listener) {
        if (!isWatch) {
            return
        }
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            mContext.contentResolver.unregisterContentObserver(contentObserver)
        }
    }

    private companion object {
        const val TAG = "WatchChecker"
        val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }
}
