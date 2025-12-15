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

import androidx.annotation.StringDef

/** Helper class to show notifications for Cross Device Sync features. */
interface NotificationHelper {
    /**
     * Potentially shows a notification for the given reason. The display of the notification is
     * potentially rate-limited to avoid excessive notifications to the user.
     */
    fun maybeShowNotification(@NotificationReason notificationReason: String)

    /** Dismisses the notification for the given reason if it is currently shown. */
    fun dismissNotification(@NotificationReason notificationReason: String)

    /** Clears the counters that are present for rate limiting the notifications. */
    fun reset()
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(NotificationReason.AIRPLANE_MODE_SYNCED)
annotation class NotificationReason {
    companion object {
        const val AIRPLANE_MODE_SYNCED = "apm_sync_notification"
    }
}
