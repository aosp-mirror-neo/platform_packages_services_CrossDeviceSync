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

import com.android.crossdevicesync.data.DefaultTimestampProvider;
import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.DeviceNodeIdProviderImpl;
import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStore;
import com.android.crossdevicesync.data.SharedDataStoreFactory;
import com.android.crossdevicesync.network.NetworkManager.Network;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.android.submerge.Converter;
import com.google.android.submerge.TimestampProvider;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

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

    @Binds
    @SyncServiceSingleton
    abstract TimestampProvider bindsTimestampProvider(DefaultTimestampProvider defaultProvider);

    @Binds
    @SyncServiceSingleton
    abstract DeviceNodeIdProvider bindsDeviceNodeIdProvider(DeviceNodeIdProviderImpl provider);

    @Provides
    @SyncServiceSingleton
    static SharedDataStoreFactory providesSharedDataStoreFactory(
            SharedDataStoreComponent.Builder componentBuilder) {
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
