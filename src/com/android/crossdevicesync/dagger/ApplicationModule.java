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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.android.crossdevicesync.common.Clock;
import com.android.crossdevicesync.common.ClockImpl;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxyImpl;
import com.android.crossdevicesync.common.ContextualModeManagerProxy;
import com.android.crossdevicesync.common.ContextualModeManagerProxyImpl;
import com.android.crossdevicesync.common.DefaultThreadChecker;
import com.android.crossdevicesync.common.DelayedExecutor;
import com.android.crossdevicesync.common.DeviceUtils;
import com.android.crossdevicesync.common.DeviceUtilsImpl;
import com.android.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.crossdevicesync.common.FrameworkStatsLogProxyImpl;
import com.android.crossdevicesync.common.HandlerDelayedExecutorImpl;
import com.android.crossdevicesync.common.ThreadChecker;
import com.android.crossdevicesync.dagger.qualifier.Background;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.notification.NotificationManagerProxy;
import com.android.crossdevicesync.notification.NotificationManagerProxyImpl;
import com.android.crossdevicesync.user.UserHelper;
import com.android.crossdevicesync.user.UserHelperImpl;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

/** Dagger module for the {@link ApplicationComponent}. */
@Module
public abstract class ApplicationModule {
    private static final String DEFAULT_SHARED_PREFS_FILE_NAME = "cross_device_sync_prefs";

    /** Dagger binding for application context. */
    @Binds
    @Singleton
    abstract Context bindApplicationContext(Application app);

    /** Provides the application level default shared prefs. */
    @Singleton
    @Provides
    static SharedPreferences provideDefaultSharedPrefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(DEFAULT_SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Binds the {@link CompanionDeviceManagerProxy} implementation. */
    @Singleton
    @Binds
    abstract CompanionDeviceManagerProxy bindsCompanionDeviceManagerProxy(
            CompanionDeviceManagerProxyImpl proxy);

    /** Binds the {@link ContextualModeManagerProxy} implementation. */
    @Singleton
    @Binds
    abstract ContextualModeManagerProxy bindsContextualModeManagerProxy(
            ContextualModeManagerProxyImpl proxy);

    @Provides
    @Singleton
    @Main
    static Looper provideMainLooper() {
        return Looper.getMainLooper();
    }

    @Provides
    @Singleton
    @Main
    static Handler provideMainHandler(@Main Looper looper) {
        return new Handler(looper);
    }

    @Provides
    @Singleton
    @Main
    static Executor provideMainExecutor(Context context) {
        return context.getMainExecutor();
    }

    @Provides
    @Singleton
    @Main
    static DelayedExecutor provideMainDelayedExecutor(@Main Handler handler) {
        return new HandlerDelayedExecutorImpl(handler);
    }

    @Provides
    @Singleton
    @Background
    static Executor provideBackgroundExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    @Singleton
    @Binds
    abstract Clock bindsClock(ClockImpl clock);

    @Binds
    @Singleton
    abstract DeviceUtils bindsWatchChecker(DeviceUtilsImpl watchChecker);

    @Binds
    @Singleton
    abstract UserHelper bindUserHelper(UserHelperImpl impl);

    @Binds
    @Singleton
    abstract ThreadChecker bindsThreadChecker(DefaultThreadChecker checker);

    @Binds
    @Singleton
    abstract NotificationManagerProxy bindsNotificationManagerProxy(
            NotificationManagerProxyImpl impl);

    @Binds
    @Singleton
    abstract FrameworkStatsLogProxy bindsFrameworkStatsLogProxy(FrameworkStatsLogProxyImpl impl);
}
