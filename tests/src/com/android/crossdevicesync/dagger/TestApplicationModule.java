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
package com.android.crossdevicesync.dagger;

import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.data.fake.SynchronousExecutorService;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

/** Test-only dagger module for the {@link TestApplicationComponent}. */
@Module(includes = {ApplicationBaseModule.class})
public abstract class TestApplicationModule {

    @Binds
    @Singleton
    abstract CompanionDeviceManagerProxy bindCompanionDeviceManagerProxy(
            FakeCompanionDeviceManagerProxy fake);

    @Provides
    @Singleton
    @Main
    static Executor provideMainExecutor() {
        return new SynchronousExecutorService();
    }
}
