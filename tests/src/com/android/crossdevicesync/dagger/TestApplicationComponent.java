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

import com.android.crossdevicesync.services.dagger.TestSyncServiceComponent;

import dagger.Component;

import javax.inject.Singleton;

/** Test-only dagger component for the {@link Singleton} scope. */
@Singleton
@Component(
        modules = {
            TestApplicationModule.class,
            TestAppComponentsModule.class,
        })
public interface TestApplicationComponent extends ApplicationComponent {

    /** Builder of this component. */
    @Component.Builder
    interface Builder extends ApplicationComponent.Builder {
        /** Build an instance of the component. */
        TestApplicationComponent build();
    }

    /** Get a {@link TestSyncServiceComponent.Builder} that can be used in tests. */
    TestSyncServiceComponent.Builder getTestSyncServiceComponentBuilder();
}
