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

import com.android.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.crossdevicesync.data.DefaultSharedDataStoreHandle;
import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStoreFactory;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.SubmergeSchemaUpgradeHelper;
import com.android.crossdevicesync.data.SubmergeSharedDataStore;
import com.android.crossdevicesync.data.dagger.qualifier.DataStoreLock;
import com.android.crossdevicesync.data.dagger.qualifier.DataStoreName;
import com.android.crossdevicesync.data.storage.IStorage;

import com.google.android.submerge.Converter;
import com.google.android.submerge.TimestampProvider;

import dagger.Module;
import dagger.Provides;

/**
 * Base module for shared data store component that is included in both production app and test
 * cases.
 */
@Module
interface SharedDataStoreBaseModule {

    @Provides
    @SharedDataStoreSingleton
    static SubmergeSchemaUpgradeHelper<?> provideSubmergeSchemaUpgradeHelper(
            IStorage storage, Converter<?> converter, @DataStoreName String dataStoreName) {
        return new SubmergeSchemaUpgradeHelper<>(storage, converter, dataStoreName);
    }

    @Provides
    static SubmergeSharedDataStore<?> provideSubmergeSharedDataStore(
            @DataStoreName String name,
            @DataStoreLock Object lock,
            DeviceNodeIdProvider deviceNodeIdProvider,
            IStorage storage,
            FrameworkStatsLogProxy frameworkStatsLogProxy,
            TimestampProvider timestampProvider,
            Converter<?> converter,
            SchemaProvider<?> schemaProvider,
            SubmergeSchemaUpgradeHelper<?> schemaUpgradeHelper,
            SharedDataStoreHandle<?> handle) {
        return new SubmergeSharedDataStore(
                name,
                lock,
                deviceNodeIdProvider,
                storage,
                frameworkStatsLogProxy,
                timestampProvider,
                converter,
                schemaProvider,
                schemaUpgradeHelper,
                handle);
    }

    @Provides
    @SharedDataStoreSingleton
    static DefaultSharedDataStoreHandle<?> provideDefaultSharedDataStoreHandle(
            @DataStoreName String name,
            @DataStoreLock Object lock,
            DeviceNodeIdProvider nodeIdProvider,
            IStorage storage,
            SharedDataStoreFactory<?> dataStoreFactory) {
        return new DefaultSharedDataStoreHandle(
                name, lock, nodeIdProvider, storage, dataStoreFactory);
    }

    @Provides
    @DataStoreLock
    @SharedDataStoreSingleton
    static Object providesDataStoreLock() {
        return new Object();
    }
}
