/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.app.Notification as AndroidNotification
import android.app.Notification.Action
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.android.crossdevicesync.R
import com.android.crossdevicesync.common.DeviceUtils
import java.time.Period
import javax.inject.Inject

class NotificationConfig
@Inject
constructor(private val mContext: Context, private val mDeviceUtils: DeviceUtils) {
    internal val notificationMap =
        mapOf(
            NotificationReason.AIRPLANE_MODE_SYNCED to
                Notification(
                    id = 1,
                    ::createApmSyncedNotificationChannel,
                    ::createApmSyncedAndroidNotification,
                    rateLimiters =
                        listOf(
                            RateLimiter(minimumWaitPeriod = Period.ZERO, timesToRepeat = 1),
                            RateLimiter(minimumWaitPeriod = Period.ofWeeks(1), timesToRepeat = 1),
                            RateLimiter(
                                minimumWaitPeriod = Period.ofMonths(6),
                                timesToRepeat = RateLimiter.FOREVER,
                            ),
                        ),
                ),
            NotificationReason.DO_NOT_DISTURB_SYNCED to
                Notification(
                    id = 2,
                    ::createDndSyncedNotificationChannel,
                    ::createDndSyncedAndroidNotification,
                    rateLimiters =
                        listOf(
                            RateLimiter(
                                /* minimumWaitPeriod= */ Period.ZERO,
                                /* timesToRepeat= */ RateLimiter.FOREVER,
                            )
                        ),
                ),
        )

    private fun createApmSyncedNotificationChannel() =
        NotificationChannel(
            APM_SYNC_NOTIFICATION_CHANNEL_ID,
            mContext.getString(R.string.airplane_mode_sync_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )

    private fun createApmSyncedAndroidNotification(): AndroidNotification {
        val messageString = mContext.getString(R.string.airplane_mode_sync_notification_content)
        val builder =
            AndroidNotification.Builder(mContext, APM_SYNC_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(mContext.getString(R.string.airplane_mode_sync_notification_title))
                .setSmallIcon(R.drawable.ic_airplane_mode)
                .setContentText(messageString)
                .setStyle(AndroidNotification.BigTextStyle().bigText(messageString))
                .setVisibility(VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setLocalOnly(true)
        val intent =
            PendingIntent.getActivity(
                mContext,
                PendingIntent.FLAG_UPDATE_CURRENT,
                Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE,
            )
        // On Watch, tapping on the notification expands it. So show this as an action instead.
        if (mDeviceUtils.isWatch) {
            val actionLabel = mContext.getString(R.string.apm_sync_notification_settings_action)
            builder.setActions(Action.Builder(/* icon */ null, actionLabel, intent).build())
        } else {
            builder.setContentIntent(intent)
        }
        return builder.build()
    }

    private fun createDndSyncedNotificationChannel() =
        NotificationChannel(
            DND_SYNC_NOTIFICATION_CHANNEL_ID,
            mContext.getString(R.string.do_not_disturb_sync_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )

    private fun createDndSyncedAndroidNotification(): AndroidNotification {
        val messageString = mContext.getString(R.string.do_not_disturb_sync_notification_content)
        return AndroidNotification.Builder(mContext, "dnd_sync_channel")
            .setContentTitle(mContext.getString(R.string.do_not_disturb_sync_notification_title))
            .setContentText(messageString)
            .setStyle(AndroidNotification.BigTextStyle().bigText(messageString))
            .setSmallIcon(com.android.internal.R.drawable.ic_settings)
            .setVisibility(VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .build()
    }

    companion object {
        const val APM_SYNC_NOTIFICATION_CHANNEL_ID = "apm_sync_channel"
        const val DND_SYNC_NOTIFICATION_CHANNEL_ID = "dnd_sync_channel"
    }
}

/**
 * Details about the notification being displayed.
 *
 * @param id The ID of the [AndroidNotification]
 * @param createChannel Creates a [NotificationChannel] for showing the notification
 * @param createAndroidNotification Creates the [AndroidNotification] to be shown
 * @param rateLimiters Determines how frequently to show this notification
 */
data class Notification(
    val id: Int,
    val createChannel: () -> NotificationChannel,
    val createAndroidNotification: () -> AndroidNotification,
    val rateLimiters: List<RateLimiter> = listOf(RateLimiter()),
) {
    init {
        require(rateLimiters.isNotEmpty()) { "Rate Limiters must be specified" }
        require(rateLimiters[0].minimumWaitPeriod == Period.ZERO) {
            "First Notification must be shown immediately"
        }
        rateLimiters.forEachIndexed { index, limiter ->
            if (index < rateLimiters.size - 1) {
                require(limiter.timesToRepeat != RateLimiter.FOREVER) {
                    "Rate Limiters other than the last one cannot be shown forever"
                }
            }
        }
    }
}

/**
 * The Rate Limiters for the notification that should be applied in order. A Rate Limiter denotes
 * the minimum waiting period and the number of times that period applies, which means the
 * notification will be shown at most X number of times, with at least Y minimum waiting period
 * between each display of the notification. This means that any requests to show a notification
 * sooner than the recommended minimum wait period will be dropped and the notification will not be
 * shown.
 *
 * For a notification, this is a repeated list, so a different waiting period can be used based on
 * the number of times the notification is shown.
 * 1. The first waiting period must be zero which means it's shown immediately.
 * 2. The first limiter's timesToRepeat denotes how many times the notification will be shown
 *    immediately without waiting for any period after it's shown.
 * 3. Optionally, the last waiting period may set the timesToRepeat to FOREVER which indicates that
 *    we will keep showing the notification for eternity, but wait for at least it's minimum waiting
 *    period before showing it again.
 *
 * For example, if the list contains :
 * - wait = 0 days, repeat 2 times
 * - wait = 5 days, repeat = 3 times
 * - wait = 10 days, repeat = 5 times
 *
 * This means the notification is shown once twice on demand, then 3 times at least 5 days apart and
 * then 5 times at least 10 days apart and never after that.
 *
 * If instead, the last Limiter is [wait = 10 days, repeat = -1], then instead of showing the
 * notification only 5 times at least 10 days apart, it's shown forever, waiting at least 10 days
 * before showing it again when requested.
 */
data class RateLimiter(
    /**
     * The minimum period to wait after showing the notification to allow showing it again. Zero
     * implies no wait period, implying the notification can be shown immediately after the previous
     * notification, without any waiting period.
     */
    val minimumWaitPeriod: Period = Period.ZERO,
    /**
     * The number of times this wait period should be used. This should be -1 or positive. -1
     * denotes this wait period must be repeated forever. For example, to show the notification as
     * needed, after at least 2 months everytime, then this RateLimiter would have a
     * minimumWaitPeriod of 2 months and a timesToRepeat of -1 (FOREVER).
     */
    val timesToRepeat: Int = FOREVER,
) {
    init {
        require(!minimumWaitPeriod.isNegative) { "minimumWaitPeriod can not be negative" }
        require(timesToRepeat == FOREVER || timesToRepeat > 0) {
            "timesToShow can only be -1 (FOREVER) or >0"
        }
    }

    companion object {
        /**
         * This is a proxy for the [RateLimiter.timesToRepeat] to indicate that it repeats forever
         */
        const val FOREVER = -1
    }
}
