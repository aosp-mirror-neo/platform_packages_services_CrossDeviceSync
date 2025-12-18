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
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.crossdevicesync.ApplicationComponentTestBase
import com.android.crossdevicesync.R
import com.android.crossdevicesync.notification.NotificationReason.Companion.AIRPLANE_MODE_SYNCED
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationHelperImplTest : ApplicationComponentTestBase() {

    @Before
    override fun setUp() {
        super.setUp()
    }

    @Test
    fun testMaybeShowNotification_firstTime_showsNotificationWithCorrectParams() {
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)

        // Verify Channel
        assertThat(mFakeNotificationManagerProxy.getChannels().containsKey("apm_sync_channel"))
            .isTrue()
        val channel = mFakeNotificationManagerProxy.getChannels()["apm_sync_channel"]!!
        assertThat(channel.name)
            .isEqualTo(mContext.getString(R.string.airplane_mode_sync_notification_channel_name))
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)

        // Verify Notification
        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isTrue()
        val notification = mFakeNotificationManagerProxy.getNotifications()[1]!!

        assertThat(notification.channelId).isEqualTo("apm_sync_channel")
        assertThat(notification.smallIcon.resId).isEqualTo(R.drawable.ic_airplane_mode)
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(notification.flags and Notification.FLAG_LOCAL_ONLY).isNotEqualTo(0)

        val extras = notification.extras
        assertThat(extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo(mContext.getString(R.string.airplane_mode_sync_notification_title))
        assertThat(extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo(mContext.getString(R.string.airplane_mode_sync_notification_content))
        assertThat(extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            .isEqualTo(mContext.getString(R.string.airplane_mode_sync_notification_content))

        // Verify SharedPrefs
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(1)
    }

    @Test
    fun testMaybeShowNotification_secondTimeWithinWeek_doesNotShowNotification() {
        // First time
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // Advance clock by 1 day
        mFakeClock.advanceTime(24 * 60 * 60 * 1000)

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)

        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isFalse()
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(1)
    }

    @Test
    fun testMaybeShowNotification_secondTimeAfterWeek_showsNotification() {
        // First time
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // Advance clock by 8 days
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)

        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isTrue()
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(2)
    }

    @Test
    fun testMaybeShowNotification_thirdTimeWithin6Months_doesNotShowNotification() {
        // 1st time
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // 2nd time (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // Advance clock by 1 day (still within 6 months of last display)
        mFakeClock.advanceTime(1L * 24 * 60 * 60 * 1000)

        // Should not show again, as less than 6 months passed from second notification and count is
        // 2
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)

        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isFalse()
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(2)
    }

    @Test
    fun testMaybeShowNotification_thirdTimeAfter6Months_showsNotification() {
        // 1st
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // 2nd (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(2)

        // Advance clock by 6 months + 1 day
        mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000)

        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)

        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isTrue()
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(3)
    }

    @Test
    fun testMaybeShowNotification_hundredthTimeBefore6Months_doesNotShowNotification() {
        // Show 99 times
        // 1st
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // 2nd (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // 3rd - 99th (after 6 months)
        repeat(97) {
            mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000) // Advance by ~6 months + 1 day
            mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
            mFakeNotificationManagerProxy.clear()
        }

        // 100th (before 6 months)
        mFakeClock.advanceTime(1L * 24 * 60 * 60 * 1000) // Advance by 1 day
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)

        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isFalse()
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(99)
    }

    @Test
    fun testMaybeShowNotification_hundredthTimeAfter6Months_showsNotification() {
        // 1st
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // 2nd (after 8 days)
        mFakeClock.advanceTime(8L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        mFakeNotificationManagerProxy.clear()

        // 3rd - 99th (after 6 months)
        repeat(97) {
            mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000)
            mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
            mFakeNotificationManagerProxy.clear()
        }

        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(99)

        // 100th (after another 6 months)
        mFakeClock.advanceTime(185L * 24 * 60 * 60 * 1000)
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)

        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isTrue()
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(100)
    }

    @Test
    fun testClearAllCounters_clearsSharedPrefs() {
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        assertThat(mSharedPreferences.getInt(AIRPLANE_MODE_SYNCED, 0)).isEqualTo(1)

        mNotificationHelperImpl.reset()

        assertThat(mSharedPreferences.contains(AIRPLANE_MODE_SYNCED)).isFalse()
        assertThat(mSharedPreferences.contains(AIRPLANE_MODE_SYNCED + "_date")).isFalse()
    }

    @Test
    fun testDismissNotification_dismissesNotification() {
        mNotificationHelperImpl.maybeShowNotification(AIRPLANE_MODE_SYNCED)
        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isTrue()

        mNotificationHelperImpl.dismissNotification(AIRPLANE_MODE_SYNCED)
        assertThat(mFakeNotificationManagerProxy.getNotifications().containsKey(1)).isFalse()
    }
}
