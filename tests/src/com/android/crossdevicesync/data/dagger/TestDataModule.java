/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.crossdevicesync.data.dagger;

import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.fake.FakeDeviceNodeIdProvider;
import com.android.crossdevicesync.data.fake.FakeTimestampProvider;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.android.submerge.TimestampProvider;

import dagger.Binds;
import dagger.Module;

/** Test-only dagger module for injecting data related bindings into the sync service scope. */
@Module(
        includes = {DataBaseModule.class},
        subcomponents = {TestSharedDataStoreComponent.class})
public abstract class TestDataModule {

    @SyncServiceSingleton
    @Binds
    abstract TimestampProvider bindsTimestampProvider(FakeTimestampProvider fakeProvider);

    @SyncServiceSingleton
    @Binds
    abstract DeviceNodeIdProvider bindsDeviceNodeIdProvider(
            FakeDeviceNodeIdProvider fakeDeviceNodeIdProvider);
}
