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

import androidx.annotation.NonNull;

import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStore;
import com.android.crossdevicesync.data.SharedDataStoreFactory;
import com.android.crossdevicesync.data.fake.FakeDeviceNodeIdProvider;
import com.android.crossdevicesync.data.fake.FakeTimestampProvider;
import com.android.crossdevicesync.network.NetworkManager.Network;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.android.submerge.Converter;
import com.google.android.submerge.TimestampProvider;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

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

    @Provides
    @SyncServiceSingleton
    static SharedDataStoreFactory providesSharedDataStoreFactory(
            TestSharedDataStoreComponent.Builder componentBuilder) {
        return new SharedDataStoreFactory() {
            @NonNull
            @SuppressWarnings("unchecked")
            @Override
            public <T> SharedDataStore<T> create(
                    @NonNull String name,
                    @NonNull Converter<T> converter,
                    @NonNull SchemaProvider<T> schemaProvider,
                    @NonNull Network network) {
                return (SharedDataStore<T>)
                        componentBuilder
                                .name(name)
                                .converter(converter)
                                .schemaProvider(schemaProvider)
                                .network(network)
                                .build()
                                .getSharedDataStore();
            }
        };
    }
}
