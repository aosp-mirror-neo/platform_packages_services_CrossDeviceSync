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
import android.content.SharedPreferences
import android.util.Log
import com.android.crossdevicesync.common.Clock
import dagger.Lazy
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of [NotificationHelper]. */
@Singleton
class NotificationHelperImpl
@Inject
constructor(
    private val mSharedPreferences: SharedPreferences,
    private val mClock: Clock,
    private val mNotificationManager: NotificationManagerProxy,
    private val mNotificationConfig: Lazy<NotificationConfig>,
) : NotificationHelper {

    override fun maybeShowNotification(notificationReason: String) {
        val notification = mNotificationConfig.get().notificationMap[notificationReason]
        if (notification == null) {
            Log.e(TAG, "maybeShowNotification: Unknown Reason ($notificationReason)")
            return
        }

        if (!checkAndRecordNotificationEligibility(notificationReason, notification.rateLimiters)) {
            return
        }

        // Clear any stale notifications for the same reason.
        mNotificationManager.maybeCancel(notification.id)
        // Create a channel and show the notification accordingly for the reason.
        mNotificationManager.createNotificationChannel(notification.createChannel())
        mNotificationManager.notify(notification.id, notification.createAndroidNotification())
    }

    @SuppressLint("UseKtx")
    private fun checkAndRecordNotificationEligibility(
        reason: String,
        rateLimiters: List<RateLimiter>,
    ): Boolean {
        val countKey = reason
        val dateKey = countKey + "_date"
        val countShown = mSharedPreferences.getInt(countKey, 0)

        var accumulatedShows = 0
        var limit: RateLimiter? = null
        for (limiter in rateLimiters) {
            if (limiter.timesToRepeat == RateLimiter.FOREVER) {
                limit = limiter
                break
            }
            accumulatedShows += limiter.timesToRepeat
            if (countShown < accumulatedShows) {
                limit = limiter
                break
            }
        }

        if (limit == null) {
            Log.i(
                TAG,
                "checkAndRecordNotificationEligibility($reason): Notification is not " +
                    "shown. countShown=$countShown",
            )
            return false
        }

        val now =
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(mClock.currentTimeMillis()),
                ZoneId.systemDefault(),
            )
        var savedDate: LocalDateTime? = null
        if (countShown != 0 && !limit.minimumWaitPeriod.isZero) {
            val date = mSharedPreferences.getString(dateKey, null)
            if (date != null) {
                try {
                    savedDate = LocalDateTime.parse(date)
                    // Comparison is +/- 1 hour, allowing for both battery optimization and
                    // consistency
                    val threshold = savedDate.plus(limit.minimumWaitPeriod).minusHours(1)

                    if (now.isBefore(threshold)) {
                        Log.i(
                            TAG,
                            "checkAndRecordNotificationEligibility($reason): Notification is not " +
                                "shown. countShown=$countShown now=$now savedDate=$savedDate",
                        )
                        return false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing saved date: $date", e)
                }
            }
        }

        // Update Shared Preferences to save the shown count and the shown date/time.
        mSharedPreferences
            .edit()
            .putInt(countKey, countShown + 1)
            .putString(dateKey, now.toString())
            .apply()
        Log.i(
            TAG,
            "checkAndRecordNotificationEligibility($reason): Notification is being shown. " +
                "countShown=$countShown now=$now savedDate=$savedDate",
        )
        return true
    }

    override fun dismissNotification(notificationReason: String) {
        val notification = mNotificationConfig.get().notificationMap[notificationReason]
        if (notification == null) {
            Log.e(TAG, "dismissNotification: Unknown Reason ($notificationReason)")
            return
        }

        // Clear any stale notifications for the reason.
        mNotificationManager.maybeCancel(notification.id)
    }

    @SuppressLint("UseKtx")
    override fun reset() {
        mNotificationConfig.get().notificationMap.keys.forEach { reason ->
            mSharedPreferences.edit().remove(reason).remove("${reason}_date").apply()
        }
    }

    companion object {
        private const val TAG = "NotificationHelper"
    }
}
