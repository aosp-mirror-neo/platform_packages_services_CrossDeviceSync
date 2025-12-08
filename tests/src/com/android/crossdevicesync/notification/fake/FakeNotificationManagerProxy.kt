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

package com.android.crossdevicesync.notification.fake

import android.app.Notification
import android.app.NotificationChannel
import com.android.crossdevicesync.notification.NotificationManagerProxy
import javax.inject.Inject
import javax.inject.Singleton

/** A fake implementation of [NotificationManagerProxy] for testing. */
@Singleton
class FakeNotificationManagerProxy @Inject constructor() : NotificationManagerProxy {

    private val mNotifications = mutableMapOf<Int, Notification>()
    private val mChannels = mutableMapOf<String, NotificationChannel>()

    override fun maybeCancel(id: Int) {
        if (mNotifications.containsKey(id)) {
            mNotifications.remove(id)
        }
    }

    override fun createNotificationChannel(channel: NotificationChannel) {
        mChannels[channel.id] = channel
    }

    override fun notify(id: Int, notification: Notification) {
        mNotifications[id] = notification
    }

    /** Returns the map of active notifications keyed by ID. */
    fun getNotifications(): Map<Int, Notification> {
        return mNotifications
    }

    /** Returns the map of created notification channels keyed by Channel ID. */
    fun getChannels(): Map<String, NotificationChannel> {
        return mChannels
    }

    /** Clears all state. */
    fun clear() {
        mNotifications.clear()
        mChannels.clear()
    }
}
