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

import android.content.Context;
import android.content.SharedPreferences;

import com.android.crossdevicesync.common.Clock;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.ContextualModeManagerProxy;
import com.android.crossdevicesync.common.DelayedExecutor;
import com.android.crossdevicesync.common.DeviceUtils;
import com.android.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.crossdevicesync.common.ThreadChecker;
import com.android.crossdevicesync.common.fake.ClockBasedExecutor;
import com.android.crossdevicesync.common.fake.FakeClock;
import com.android.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.fake.FakeContextWrapper;
import com.android.crossdevicesync.common.fake.FakeContextualModeManager;
import com.android.crossdevicesync.common.fake.FakeDeviceUtils;
import com.android.crossdevicesync.common.fake.FakeFrameworkStatsLogProxy;
import com.android.crossdevicesync.common.fake.SynchronousExecutorService;
import com.android.crossdevicesync.dagger.qualifier.Background;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.notification.NotificationManagerProxy;
import com.android.crossdevicesync.notification.fake.FakeNotificationManagerProxy;
import com.android.crossdevicesync.user.UserHelper;
import com.android.crossdevicesync.user.fake.FakeUserHelper;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

/** Test-only dagger module for the {@link TestApplicationComponent}. */
@Module
public abstract class TestApplicationModule {
    private static final String SHARED_PREFS_FILE_NAME = "test_shared_prefs";

    /** Dagger binding for application context. */
    @Binds
    @Singleton
    abstract Context bindApplicationContext(FakeContextWrapper fakeContextWrapper);

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

    @Binds
    @Singleton
    @Background
    abstract Executor bindsBackgroundExecutor(@Main Executor executor);

    @Provides
    @Singleton
    @Main
    static DelayedExecutor provideMainDelayedExecutor(FakeClock clock) {
        return new ClockBasedExecutor(clock);
    }

    @Binds
    @Singleton
    abstract Clock bindClock(FakeClock fake);

    @Binds
    @Singleton
    abstract DeviceUtils bindWatchChecker(FakeDeviceUtils fake);

    @Singleton
    @Provides
    static SharedPreferences provideSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE);
    }

    @Singleton
    @Binds
    abstract ContextualModeManagerProxy bindsContextualModeManagerProxy(
            FakeContextualModeManager fake);

    @Singleton
    @Binds
    abstract UserHelper bindsUserHelper(FakeUserHelper fake);

    @Singleton
    @Provides
    static ThreadChecker providesThreadChecker() {
        // Ignore.
        return () -> {};
    }

    @Binds
    @Singleton
    abstract NotificationManagerProxy bindNotificationManager(FakeNotificationManagerProxy fake);

    @Binds
    @Singleton
    abstract FrameworkStatsLogProxy bindsFrameworkStatsLogProxy(FakeFrameworkStatsLogProxy fake);
}
