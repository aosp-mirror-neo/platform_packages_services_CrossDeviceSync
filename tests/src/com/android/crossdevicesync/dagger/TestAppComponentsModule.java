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
package com.android.crossdevicesync.dagger;

import android.app.Service;

import com.android.crossdevicesync.services.SyncService;
import com.android.crossdevicesync.services.dagger.TestSyncServiceComponent;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/** Test-only dagger module for injecting app components. */
@Module(subcomponents = {TestSyncServiceComponent.class})
public abstract class TestAppComponentsModule {

    /** Provides a SyncService instance. */
    @Provides
    @IntoMap
    @ClassKey(SyncService.class)
    static Service provideSyncService(TestSyncServiceComponent.Builder builder) {
        return builder.build().getSyncService();
    }
}
