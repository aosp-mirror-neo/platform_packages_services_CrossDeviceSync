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

import com.android.crossdevicesync.data.DefaultTimestampProvider;
import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.DeviceNodeIdProviderImpl;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.android.submerge.TimestampProvider;

import dagger.Binds;
import dagger.Module;

/**
 * Dagger module for injecting data related bindings in the sync service scope. Production-only
 * bindings should live here.
 *
 * <p>Note: if a binding is needed in both production and test code, please put it in {@link
 * DataBaseModule} instead.
 */
@Module(
        includes = {DataBaseModule.class},
        subcomponents = {SharedDataStoreComponent.class})
public abstract class DataModule {

    @SyncServiceSingleton
    @Binds
    abstract TimestampProvider bindsTimestampProvider(DefaultTimestampProvider defaultProvider);

    @SyncServiceSingleton
    @Binds
    abstract DeviceNodeIdProvider bindsDeviceNodeIdProvider(DeviceNodeIdProviderImpl provider);
}
