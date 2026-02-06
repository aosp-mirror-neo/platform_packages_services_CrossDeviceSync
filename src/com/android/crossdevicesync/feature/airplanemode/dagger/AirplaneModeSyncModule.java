/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.crossdevicesync.feature.airplanemode.dagger;

import com.android.crossdevicesync.feature.airplanemode.AirplaneModeController;
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeControllerImpl;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import dagger.Binds;
import dagger.Module;

/** Dagger module for airplane mode controller. */
@Module(includes = {AirplaneModeSyncBaseModule.class})
public interface AirplaneModeSyncModule {
    /** Binds {@link AirplaneModeControllerImpl} to {@link AirplaneModeController}. */
    @Binds
    @SyncServiceSingleton
    AirplaneModeController bindsAirplaneModeController(AirplaneModeControllerImpl impl);
}
