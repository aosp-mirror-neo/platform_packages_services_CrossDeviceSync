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

import static android.provider.Settings.Global.Wearable.TETHERED_CONFIG_RESTRICTED;
import static android.provider.Settings.Global.Wearable.TETHERED_CONFIG_STANDALONE;
import static android.provider.Settings.Global.Wearable.TETHERED_CONFIG_UNKNOWN;
import static android.provider.Settings.Global.Wearable.TETHER_CONFIG_STATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.android.crossdevicesync.common.Clock;
import com.android.crossdevicesync.common.ClockImpl;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxyImpl;
import com.android.crossdevicesync.common.DefaultThreadChecker;
import com.android.crossdevicesync.common.DelayedExecutor;
import com.android.crossdevicesync.common.HandlerDelayedExecutorImpl;
import com.android.crossdevicesync.common.ThreadChecker;
import com.android.crossdevicesync.dagger.qualifier.IsKidsWatch;
import com.android.crossdevicesync.dagger.qualifier.IsWatch;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.notification.dagger.NotificationModule;
import com.android.crossdevicesync.user.UserHelper;
import com.android.crossdevicesync.user.UserHelperImpl;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

/**
 * Dagger module for the {@link ApplicationComponent}. Production-only bindings should live here.
 *
 * <p>Note: if a binding is needed in both production and test code, please put it in {@link
 * ApplicationBaseModule} instead.
 */
@Module(includes = {ApplicationBaseModule.class, NotificationModule.class})
public abstract class ApplicationModule {
    private static final String DEFAULT_SHARED_PREFS_FILE_NAME = "cross_device_sync_prefs";

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

    @Singleton
    @Binds
    abstract Clock bindsClock(ClockImpl clock);

    @Provides
    @IsWatch
    @Singleton
    static boolean providesIsWatch(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    @Binds
    @Singleton
    abstract UserHelper bindUserHelper(UserHelperImpl impl);

    @Binds
    @Singleton
    abstract ThreadChecker bindsThreadChecker(DefaultThreadChecker checker);

    @Provides
    @IsKidsWatch
    @Singleton
    static boolean providesIsKidsWatch(Context context, @IsWatch boolean isWatch) {
        if (!isWatch) {
            return false;
        }
        int config =
                Settings.Global.getInt(
                        context.getContentResolver(), TETHER_CONFIG_STATE, TETHERED_CONFIG_UNKNOWN);
        return config == TETHERED_CONFIG_RESTRICTED || config == TETHERED_CONFIG_STANDALONE;
    }
}
