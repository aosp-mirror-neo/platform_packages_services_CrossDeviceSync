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

import com.android.crossdevicesync.CrossDeviceSyncAppComponentFactory;

import dagger.BindsInstance;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Dagger component for the {@link Singleton} scope. This corresponds to the application life cycle.
 */
@Singleton
@Component(modules = {ApplicationModule.class})
public interface ApplicationComponent {

    /** Builder for {@link ApplicationComponent}. */
    @Component.Builder
    interface Builder {
        /** Binds an Application instance. */
        @BindsInstance
        Builder application(Application app);

        /** Build an ApplicationComponent instance. */
        ApplicationComponent build();
    }

    /** Inject bindings into app component factory. */
    void inject(CrossDeviceSyncAppComponentFactory appComponentFactory);
}
