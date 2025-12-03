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

import com.android.crossdevicesync.data.dagger.TestDataModule;
import com.android.crossdevicesync.data.dagger.TestSharedDataStoreComponent;
import com.android.crossdevicesync.feature.dagger.TestFeatureModule;
import com.android.crossdevicesync.network.dagger.TestNetworkModule;
import com.android.crossdevicesync.services.SyncService;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;
import com.android.crossdevicesync.services.dagger.qualifier.UseRealSharedDataStoreHandleImpl;
import com.android.crossdevicesync.services.dagger.qualifier.UseRealSharedDataStoreImpl;

import dagger.BindsInstance;
import dagger.Subcomponent;

/**
 * Test-only dagger subcomponent for {@link SyncServiceSingleton} scope. This corresponds to the
 * lifecycle of {@link SyncService}.
 */
@SyncServiceSingleton
@Subcomponent(
        modules = {
            TestSyncServiceModule.class,
            TestDataModule.class,
            TestNetworkModule.class,
            TestFeatureModule.class
        })
public interface TestSyncServiceComponent extends SyncServiceComponent {

    /** Builder interface for TestSyncServiceComponent. */
    @Subcomponent.Builder
    interface Builder extends SyncServiceComponent.Builder {

        /** Specify whether to use real shared data store impl. */
        @BindsInstance
        Builder useRealSharedDataStoreImpl(@UseRealSharedDataStoreImpl boolean useRealImpl);

        /** Specify whether to use real shared data store handle impl. */
        @BindsInstance
        Builder useRealSharedDataStoreHandleImpl(
                @UseRealSharedDataStoreHandleImpl boolean useRealImpl);

        /** Build an instance of this component. */
        TestSyncServiceComponent build();
    }

    /** Get a {@link TestSharedDataStoreComponent.Builder} that can be used in tests. */
    TestSharedDataStoreComponent.Builder getTestSharedDataStoreComponentBuilder();

    /** Inject dagger bindings into test. */
    void inject(SyncServiceComponentTestBase test);
}
