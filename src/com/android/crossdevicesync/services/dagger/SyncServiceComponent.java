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
package com.android.crossdevicesync.services.dagger;

import com.android.crossdevicesync.data.dagger.DataModule;
import com.android.crossdevicesync.services.SyncService;

import dagger.Subcomponent;

/**
 * Dagger subcomponent for {@link SyncServiceSingleton} scope. This corresponds to the lifecycle of
 * {@link SyncService}.
 */
@SyncServiceSingleton
@Subcomponent(modules = {DataModule.class})
public interface SyncServiceComponent {

    /** Builder interface for SyncServiceComponent. */
    @Subcomponent.Builder
    interface Builder {
        /** Build a SyncServiceComponent. */
        SyncServiceComponent build();
    }

    /** Get an instance of {@link SyncService}. */
    SyncService getSyncService();
}
