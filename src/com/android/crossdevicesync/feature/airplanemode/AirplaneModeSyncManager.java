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
package com.android.crossdevicesync.feature.airplanemode;

import android.util.IndentingPrintWriter;

import com.android.crossdevicesync.feature.FeatureManager;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import javax.inject.Inject;

/** Airplane mode sync feature manager. */
@SyncServiceSingleton
public class AirplaneModeSyncManager implements FeatureManager {

    @Inject
    AirplaneModeSyncManager() {}

    @Override
    public void init() {}

    @Override
    public void destroy() {}

    @Override
    public void dump(IndentingPrintWriter pw) {}
}
