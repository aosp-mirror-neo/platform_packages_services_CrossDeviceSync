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
package com.android.crossdevicesync.services.dagger;

import com.android.crossdevicesync.metadata.MetadataPublisher;
import com.android.crossdevicesync.metadata.fake.FakeMetadataPublisher;
import com.android.crossdevicesync.notification.NotificationHelper;
import com.android.crossdevicesync.notification.fake.FakeNotificationHelper;

import dagger.Binds;
import dagger.Module;

/** Test-only dagger module for objects in sync service scope. */
@Module
public abstract class TestSyncServiceModule {
    @Binds
    @SyncServiceSingleton
    abstract MetadataPublisher bindsMetadataPublisher(FakeMetadataPublisher impl);

    @Binds
    @SyncServiceSingleton
    abstract NotificationHelper bindsNotificationHelper(FakeNotificationHelper impl);
}
