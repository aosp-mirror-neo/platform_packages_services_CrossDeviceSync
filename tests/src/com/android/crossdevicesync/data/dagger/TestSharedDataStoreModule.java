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

import com.android.crossdevicesync.common.fake.SynchronousExecutorService;
import com.android.crossdevicesync.data.DefaultSharedDataStoreHandle;
import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStore;
import com.android.crossdevicesync.data.SharedDataStoreFactory;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.SubmergeSharedDataStore;
import com.android.crossdevicesync.data.dagger.qualifier.DataStoreName;
import com.android.crossdevicesync.data.dagger.qualifier.IOThread;
import com.android.crossdevicesync.data.fake.FakeSharedDataStore;
import com.android.crossdevicesync.data.fake.FakeSharedDataStoreHandle;
import com.android.crossdevicesync.data.fake.FakeStorage;
import com.android.crossdevicesync.data.fake.TestSharedDataStoreFactory;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.services.dagger.qualifier.UseRealSharedDataStoreHandleImpl;
import com.android.crossdevicesync.services.dagger.qualifier.UseRealSharedDataStoreImpl;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Provider;

/** Test-only dagger module for injecting objects in the {@link SharedDataStoreSingleton} scope. */
@Module(includes = {SharedDataStoreBaseModule.class})
public abstract class TestSharedDataStoreModule {
    @SharedDataStoreSingleton
    @Binds
    abstract IStorage bindsIStorage(FakeStorage storage);

    @SharedDataStoreSingleton
    @IOThread
    @Provides
    static ListeningExecutorService provideIOThreadListeningExecutorService() {
        return MoreExecutors.listeningDecorator(new SynchronousExecutorService());
    }

    @Provides
    static FakeSharedDataStore<?> provideFakeSharedDataStore(
            @DataStoreName String name,
            DeviceNodeIdProvider deviceNodeIdProvider,
            FakeStorage storage,
            SchemaProvider<?> schemaProvider,
            SharedDataStoreHandle<?> handle) {
        return new FakeSharedDataStore(name, deviceNodeIdProvider, schemaProvider, storage, handle);
    }

    @Provides
    @SharedDataStoreSingleton
    static FakeSharedDataStoreHandle<?> providesFakeSharedDataStoreHandle(
            @DataStoreName String name,
            DeviceNodeIdProvider nodeIdProvider,
            FakeStorage storage,
            @IOThread ListeningExecutorService executor,
            SharedDataStoreFactory<?> dataStoreFactory) {
        return new FakeSharedDataStoreHandle(
                name, nodeIdProvider, storage, executor, dataStoreFactory);
    }

    @Provides
    @SharedDataStoreSingleton
    static SharedDataStoreHandle<?> providesSharedDataStoreHandle(
            Provider<FakeSharedDataStoreHandle<?>> fakeHandle,
            Provider<DefaultSharedDataStoreHandle<?>> realHandle,
            @UseRealSharedDataStoreHandleImpl boolean useRealImpl) {
        return useRealImpl ? realHandle.get() : fakeHandle.get();
    }

    @Provides
    static SharedDataStore<?> providesSharedDataStore(
            Provider<FakeSharedDataStore<?>> fakeDataStore,
            Provider<SubmergeSharedDataStore<?>> realDataStore,
            @UseRealSharedDataStoreImpl boolean useRealImpl) {
        return useRealImpl ? realDataStore.get() : fakeDataStore.get();
    }

    @Provides
    @SharedDataStoreSingleton
    static TestSharedDataStoreFactory<?> providesTestSharedDataStoreFactory(
            Provider<SharedDataStore<?>> provider) {
        return new TestSharedDataStoreFactory(provider);
    }

    @Binds
    @SharedDataStoreSingleton
    abstract SharedDataStoreFactory<?> bindsSharedDataStoreFactory(
            TestSharedDataStoreFactory<?> factory);
}
