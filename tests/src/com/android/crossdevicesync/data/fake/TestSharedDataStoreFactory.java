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
package com.android.crossdevicesync.data.fake;

import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStore;
import com.android.crossdevicesync.data.SharedDataStoreFactory;
import com.android.crossdevicesync.data.dagger.TestSharedDataStoreComponent;
import com.android.crossdevicesync.network.NetworkManager.Network;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;
import com.android.crossdevicesync.services.dagger.qualifier.UseRealSharedDataStoreImpl;

import com.google.android.submerge.Converter;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/** Test implementation of {@link SharedDataStoreFactory}. */
@SyncServiceSingleton
public class TestSharedDataStoreFactory implements SharedDataStoreFactory {
    private final boolean mUseRealImpl;
    private final TestSharedDataStoreComponent.Builder mSharedDataStoreComponentBuilder;
    private final DeviceNodeIdProvider mDeviceNodeIdProvider;
    private final Map<String, SharedDataStore<?>> mDataStores = new HashMap<>();
    private boolean mAsync;

    @Inject
    public TestSharedDataStoreFactory(
            @UseRealSharedDataStoreImpl boolean useRealImpl,
            TestSharedDataStoreComponent.Builder sharedDataStoreComponentBuilder,
            DeviceNodeIdProvider deviceNodeIdProvider) {
        mUseRealImpl = useRealImpl;
        mSharedDataStoreComponentBuilder = sharedDataStoreComponentBuilder;
        mDeviceNodeIdProvider = deviceNodeIdProvider;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull <T> SharedDataStore<T> create(
            @NotNull String name,
            @NotNull Converter<T> converter,
            @NotNull SchemaProvider<T> schemaProvider,
            @NotNull Network network) {
        SharedDataStore<?> dataStore;
        if (mUseRealImpl) {
            dataStore =
                    mSharedDataStoreComponentBuilder
                            .name(name)
                            .converter(converter)
                            .schemaProvider(schemaProvider)
                            .network(network)
                            .build()
                            .getSharedDataStore();
        } else {
            dataStore =
                    new FakeSharedDataStore<T>(name, mDeviceNodeIdProvider, schemaProvider)
                            .setAsync(mAsync);
        }
        mDataStores.put(name, dataStore);
        return (SharedDataStore<T>) dataStore;
    }

    /** Get the last created data store by name. */
    @SuppressWarnings("unchecked")
    public <T> FakeSharedDataStore<T> getDataStore(String name) {
        return (FakeSharedDataStore<T>) mDataStores.get(name);
    }

    /**
     * Make existing and future data stores async. Only works on {@link FakeSharedDataStore} and
     * doesn't work on real data stores.
     */
    public void setDataStoresAsync(boolean async) {
        if (mUseRealImpl) {
            throw new UnsupportedOperationException(
                    "setDataStoresAsync is not supported when using the real SharedDataStore"
                            + " implementation.");
        }
        mAsync = async;
        mDataStores.values().forEach(d -> ((FakeSharedDataStore<?>) d).setAsync(async));
    }
}
