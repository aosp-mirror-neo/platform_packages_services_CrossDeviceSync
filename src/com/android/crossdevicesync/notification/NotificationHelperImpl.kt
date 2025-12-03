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

import android.annotation.SuppressLint
import android.app.Notification as AndroidNotification
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.crossdevicesync.R
import com.android.crossdevicesync.common.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Implementation of [NotificationHelper]. */
@Singleton
class NotificationHelperImpl
@Inject
constructor(
    private val mContext: Context,
    private val mSharedPreferences: SharedPreferences,
    private val mClock: Clock,
    private val mNotificationManager: NotificationManagerProxy,
) : NotificationHelper {

    @SuppressLint("UseKtx")
    override fun maybeShowNotification(@NotificationReason notificationReason: String) {
        val notification = NOTIFICATION_MAP[notificationReason]
        if (notification == null) {
            Log.e(TAG, "maybeShowNotification: Unknown Reason ($notificationReason)")
            return
        }

        val countKey = notificationReason
        val dateKey = countKey + "_date"
        val countShown = mSharedPreferences.getInt(countKey, 0)
        val now =
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(mClock.currentTimeMillis()),
                ZoneId.systemDefault(),
            )
        var savedDate: LocalDateTime? = null

        if (countShown != 0) {
            val date = mSharedPreferences.getString(dateKey, null)
            if (date != null) {
                try {
                    savedDate = LocalDateTime.parse(date)
                    // The notification is always displayed the first time and if it has been at
                    // least 1 week since the first display or 6 months since the last display
                    //
                    // Comparison is +/- 1 hour, allowing for both battery optimization and
                    // consistency
                    if (
                        (countShown != 1 && now.isBefore(savedDate.plusMonths(6).minusHours(1))) ||
                            now.isBefore(savedDate.plusWeeks(1).minusHours(1))
                    ) {
                        Log.i(
                            TAG,
                            "maybeShowNotification($notificationReason): Notification discarded. " +
                                "countShown=$countShown now=$now savedDate=$savedDate",
                        )
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing saved date: $date", e)
                }
            }
        }

        // Update Shared Preferences to save the shown count and the shown date/time.
        mSharedPreferences
            .edit()
            .putInt(countKey, min(3, countShown + 1))
            .putString(dateKey, now.toString())
            .apply()
        Log.i(
            TAG,
            "maybeShowNotification($notificationReason): Notification is being shown. " +
                "countShown=$countShown now=$now savedDate=$savedDate",
        )

        // Clear any stale notifications for the same reason.
        mNotificationManager.maybeCancel(notification.id)
        // Create a channel and show the notification accordingly for the reason.
        mNotificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                mContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val messageString = mContext.getString(notification.message)
        mNotificationManager.notify(
            notification.id,
            AndroidNotification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(mContext.getString(notification.title))
                .setContentText(messageString)
                .setStyle(AndroidNotification.BigTextStyle().bigText(messageString))
                .setSmallIcon(notification.icon)
                .setVisibility(VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .build(),
        )
    }

    companion object {
        private const val TAG = "FeatureNotificationHelper"
        private const val CHANNEL_ID = "cross_device_sync_channel"

        private val NOTIFICATION_MAP =
            mapOf(
                NotificationReason.AIRPLANE_MODE_SYNCED to
                    Notification(
                        id = 1,
                        R.string.airplane_mode_sync_notification_title,
                        R.string.airplane_mode_sync_notification_content,
                        R.drawable.ic_airplane_mode,
                    )
            )

        private data class Notification(
            val id: Int,
            @param:StringRes val title: Int,
            @param:StringRes val message: Int,
            @param:DrawableRes val icon: Int,
        )
    }
}
