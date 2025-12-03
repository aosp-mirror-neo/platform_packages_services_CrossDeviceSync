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

package com.android.crossdevicesync.notification.dagger

import com.android.crossdevicesync.notification.NotificationHelper
import com.android.crossdevicesync.notification.NotificationManagerProxy
import com.android.crossdevicesync.notification.fake.FakeNotificationHelper
import com.android.crossdevicesync.notification.fake.FakeNotificationManagerProxy
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface TestNotificationModule {
    @Binds
    @Singleton
    fun bindNotificationManagerProxy(fake: FakeNotificationManagerProxy): NotificationManagerProxy

    @Binds @Singleton fun bindNotificationHelper(fake: FakeNotificationHelper): NotificationHelper
}
