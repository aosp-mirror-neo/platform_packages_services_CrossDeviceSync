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
package com.android.crossdevicesync.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.UserHandle
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of [NotificationManagerProxy]. */
@Singleton
class NotificationManagerProxyImpl @Inject constructor(mContext: Context) :
    NotificationManagerProxy {

    private val mNotificationManager: NotificationManager =
        mContext.getSystemService(NotificationManager::class.java)

    override fun createNotificationChannel(channel: NotificationChannel) {
        mNotificationManager.createNotificationChannel(channel)
    }

    override fun notify(id: Int, notification: Notification, user: UserHandle) {
        mNotificationManager.notifyAsUser(/* tag */ null, id, notification, user)
    }

    override fun cancel(id: Int, user: UserHandle) {
        mNotificationManager.cancelAsUser(/* tag */ null, id, user)
    }
}
