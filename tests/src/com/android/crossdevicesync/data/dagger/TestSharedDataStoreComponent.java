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

import com.android.crossdevicesync.data.SharedDataStoreComponentTestBase;

import dagger.Subcomponent;

/**
 * Test-only dagger subcomponent for {@link SharedDataStoreSingleton} scope. This corresponds to an
 * individual data store.
 */
@SharedDataStoreSingleton
@Subcomponent(modules = {TestSharedDataStoreModule.class})
public interface TestSharedDataStoreComponent extends SharedDataStoreComponent {

    /** Builder interface for TestSharedDataStoreComponent. */
    @Subcomponent.Builder
    interface Builder extends SharedDataStoreComponent.Builder {

        /** Build an instance of this component. */
        TestSharedDataStoreComponent build();
    }

    /** Inject dagger bindings into the test case. */
    void inject(SharedDataStoreComponentTestBase test);
}
