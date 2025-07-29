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

import android.app.Application;
import android.content.Context;

import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

/** Base module that is included in both production app and test cases. */
@Module
public abstract class ApplicationBaseModule {

    /** Dagger binding for application context. */
    @Binds
    @Singleton
    abstract Context bindApplicationContext(Application app);
}
