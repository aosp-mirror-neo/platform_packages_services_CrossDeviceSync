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

import com.android.crossdevicesync.data.DefaultSharedDataStoreHandle;
import com.android.crossdevicesync.data.SharedDataStore;
import com.android.crossdevicesync.data.SharedDataStoreFactory;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.SubmergeSharedDataStore;
import com.android.crossdevicesync.data.dagger.qualifier.IOThread;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.data.storage.RoomStorage;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.concurrent.Executors;

import javax.inject.Provider;

/**
 * Dagger module for injecting objects in the {@link SharedDataStoreSingleton} scope.Production-only
 * bindings should live here.
 *
 * <p>Note: if a binding is needed in both production and test code, please put it in {@link
 * SharedDataStoreBaseModule} instead.
 */
@Module(includes = {SharedDataStoreBaseModule.class})
interface SharedDataStoreModule {

    @Binds
    @SharedDataStoreSingleton
    IStorage bindsIStorage(RoomStorage storage);

    @Provides
    @IOThread
    @SharedDataStoreSingleton
    static ListeningExecutorService provideIOThreadListeningExecutorService() {
        return MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    }

    @Binds
    @SharedDataStoreSingleton
    SharedDataStoreHandle<?> bindsSharedDataStoreHandle(DefaultSharedDataStoreHandle<?> handle);

    @Binds
    SharedDataStore<?> bindsSharedDataStore(SubmergeSharedDataStore<?> submergeSharedDataStore);

    @Provides
    @SharedDataStoreSingleton
    static SharedDataStoreFactory<?> providesSharedDataStoreFactory(
            Provider<SharedDataStore<?>> provider) {
        return (SharedDataStoreFactory) provider::get;
    }
}
