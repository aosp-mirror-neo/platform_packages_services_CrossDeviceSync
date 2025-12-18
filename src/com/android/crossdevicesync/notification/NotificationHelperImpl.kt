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
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

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

    override fun maybeShowNotification(@NotificationReason notificationReason: String) {
        val notification = NOTIFICATION_MAP[notificationReason]
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
        mNotificationManager.createNotificationChannel(
            NotificationChannel(
                notification.channelId,
                mContext.getString(notification.channelName),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val messageString = mContext.getString(notification.message)
        mNotificationManager.notify(
            notification.id,
            AndroidNotification.Builder(mContext, notification.channelId)
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
            if (limiter.timesToRepeat == FOREVER) {
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
        val notification = NOTIFICATION_MAP[notificationReason]
        if (notification == null) {
            Log.e(TAG, "dismissNotification: Unknown Reason ($notificationReason)")
            return
        }

        // Clear any stale notifications for the reason.
        mNotificationManager.maybeCancel(notification.id)
    }

    @SuppressLint("UseKtx")
    override fun reset() {
        NOTIFICATION_MAP.keys.forEach { reason ->
            mSharedPreferences.edit().remove(reason).remove("${reason}_date").apply()
        }
    }

    companion object {
        private const val TAG = "NotificationHelper"
        /**
         * This is a proxy for the [RateLimiter.timesToRepeat] to indicate that it repeats forever
         */
        private const val FOREVER = -1

        private val NOTIFICATION_MAP =
            mapOf(
                NotificationReason.AIRPLANE_MODE_SYNCED to
                    Notification(
                        id = 1,
                        channelId = "apm_sync_channel",
                        channelName = R.string.airplane_mode_sync_notification_channel_name,
                        title = R.string.airplane_mode_sync_notification_title,
                        message = R.string.airplane_mode_sync_notification_content,
                        icon = R.drawable.ic_airplane_mode,
                        rateLimiters =
                            listOf(
                                RateLimiter(minimumWaitPeriod = Period.ZERO, timesToRepeat = 1),
                                RateLimiter(
                                    minimumWaitPeriod = Period.ofWeeks(1),
                                    timesToRepeat = 1,
                                ),
                                RateLimiter(
                                    minimumWaitPeriod = Period.ofMonths(6),
                                    timesToRepeat = FOREVER,
                                ),
                            ),
                    ),
                NotificationReason.DO_NOT_DISTURB_SYNCED to
                    Notification(
                        id = 2,
                        channelId = "dnd_sync_channel",
                        channelName = R.string.do_not_disturb_sync_notification_channel_name,
                        title = R.string.do_not_disturb_sync_notification_title,
                        message = R.string.do_not_disturb_sync_notification_content,
                        icon = com.android.internal.R.drawable.ic_settings,
                        rateLimiters =
                            listOf(
                                RateLimiter(
                                    minimumWaitPeriod = Period.ZERO,
                                    timesToRepeat = FOREVER,
                                )
                            ),
                    ),
            )

        /**
         * The Rate Limiters for the notification that should be applied in order. A Rate Limiter
         * denotes the minimum waiting period and the number of times that period applies, which
         * means the notification will be shown at most X number of times, with at least Y minimum
         * waiting period between each display of the notification. This means that any requests to
         * show a notification sooner than the recommended minimum wait period will be dropped and
         * the notification will not be shown.
         *
         * For a notification, this is a repeated list, so a different waiting period can be used
         * based on the number of times the notification is shown.
         * 1. The first waiting period must be zero which means it's shown immediately.
         * 2. The first limiter's timesToRepeat denotes how many times the notification will be
         *    shown immediately without waiting for any period after it's shown.
         * 3. Optionally, the last waiting period may set the timesToRepeat to FOREVER which
         *    indicates that we will keep showing the notification for eternity, but wait for at
         *    least it's minimum waiting period before showing it again.
         *
         * For example, if the list contains :
         * - wait = 0 days, repeat 2 times
         * - wait = 5 days, repeat = 3 times
         * - wait = 10 days, repeat = 5 times
         *
         * This means the notification is shown once twice on demand, then 3 times at least 5 days
         * apart and then 5 times at least 10 days apart and never after that.
         *
         * If instead, the last Limiter is [wait = 10 days, repeat = -1], then instead of showing
         * the notification only 5 times at least 10 days apart, it's shown forever, waiting at
         * least 10 days before showing it again when requested.
         */
        private data class RateLimiter(
            /**
             * The minimum period to wait after showing the notification to allow showing it again.
             * Zero implies no wait period, implying the notification can be shown immediately after
             * the previous notification, without any waiting period.
             */
            val minimumWaitPeriod: Period = Period.ZERO,
            /**
             * The number of times this wait period should be used. This should be -1 or positive.
             * -1 denotes this wait period must be repeated forever. For example, to show the
             * notification as needed, after at least 2 months everytime, then this RateLimiter
             * would have a minimumWaitPeriod of 2 months and a timesToRepeat of -1 (FOREVER).
             */
            val timesToRepeat: Int = FOREVER,
        ) {
            init {
                require(!minimumWaitPeriod.isNegative) { "minimumWaitPeriod can not be negative" }
                require(timesToRepeat == FOREVER || timesToRepeat > 0) {
                    "timesToShow can only be -1 (FOREVER) or >0"
                }
            }
        }

        private data class Notification(
            val id: Int,
            val channelId: String,
            @param:StringRes val channelName: Int,
            @param:StringRes val title: Int,
            @param:StringRes val message: Int,
            @param:DrawableRes val icon: Int,
            val rateLimiters: List<RateLimiter> = listOf(RateLimiter()),
        ) {
            init {
                require(rateLimiters.isNotEmpty()) { "Rate Limiters must be specified" }
                require(rateLimiters[0].minimumWaitPeriod == Period.ZERO) {
                    "First Notification must be shown immediately"
                }
                rateLimiters.forEachIndexed { index, limiter ->
                    if (index < rateLimiters.size - 1) {
                        require(limiter.timesToRepeat != FOREVER) {
                            "Rate Limiters other than the last one cannot be shown forever"
                        }
                    }
                }
            }
        }
    }
}
