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
package com.android.crossdevicesync.data.dagger;

import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.IStorage;
import com.android.crossdevicesync.data.dagger.qualifier.IOThread;

import com.google.android.submerge.Converter;
import com.google.android.submerge.DataStore;
import com.google.android.submerge.NetworkInterface;
import com.google.android.submerge.TimestampProvider;
import com.google.common.util.concurrent.ListeningExecutorService;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Base module for shared data store component that is included in both production app and test
 * cases.
 */
@Module
public abstract class SharedDataStoreBaseModule {

    @SuppressWarnings("MustBeClosedChecker")
    @SharedDataStoreSingleton
    @Provides
    static DataStore<?> provideSubmergeDataStore(
            DeviceNodeIdProvider deviceNodeIdProvider,
            NetworkInterface networkInterface,
            IStorage storage,
            TimestampProvider timestampProvider,
            Converter<?> converter) {
        return new DataStore<>(
                deviceNodeIdProvider.getLocalDeviceNodeId(),
                networkInterface,
                storage,
                timestampProvider,
                converter);
    }

    @SharedDataStoreSingleton
    @IOThread
    @Binds
    abstract ExecutorService bindsIOThreadExecutorService(
            @IOThread ListeningExecutorService service);

    @SharedDataStoreSingleton
    @IOThread
    @Binds
    abstract Executor bindsIOThreadExecutor(@IOThread ExecutorService service);
}
