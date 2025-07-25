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
package com.android.crossdevicesync;

import android.annotation.Nullable;
import android.app.Application;

import com.android.crossdevicesync.dagger.ApplicationComponent;
import com.android.crossdevicesync.dagger.DaggerApplicationComponent;

/** Application class of the cross device sync app. */
public class CrossDeviceSyncApplication extends Application {
    private static final String TAG = "XDeviceSyncApplication";

    @Nullable private ApplicationComponent mAppComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        mAppComponent = DaggerApplicationComponent.builder().application(this).build();
    }

    @Nullable
    public ApplicationComponent getApplicationComponent() {
        return mAppComponent;
    }
}
