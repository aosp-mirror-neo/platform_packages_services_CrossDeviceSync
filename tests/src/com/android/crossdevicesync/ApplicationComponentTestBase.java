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
package com.android.crossdevicesync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.android.crossdevicesync.common.DelayedExecutor;
import com.android.crossdevicesync.common.fake.FakeClock;
import com.android.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.fake.FakeContextualModeManager;
import com.android.crossdevicesync.common.fake.FakeDeviceUtils;
import com.android.crossdevicesync.common.fake.FakeFrameworkStatsLogProxy;
import com.android.crossdevicesync.common.fake.FakeResources;
import com.android.crossdevicesync.dagger.TestApplicationComponent;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.notification.fake.FakeNotificationManagerProxy;
import com.android.crossdevicesync.user.fake.FakeUserHelper;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/** Base class for tests that depend on the {@link TestApplicationComponent} */
public abstract class ApplicationComponentTestBase {

    protected Context mContext;
    protected TestApplication mApplication;
    protected TestApplicationComponent mApplicationComponent;
    @Inject protected BootReceiver mBootReceiver;
    @Inject protected FakeCompanionDeviceManagerProxy mFakeCompanionDeviceManagerProxy;
    @Inject protected FakeNotificationManagerProxy mFakeNotificationManagerProxy;
    @Inject @Main protected Executor mMainExecutor;
    @Inject protected FakeClock mFakeClock;
    @Inject @Main protected DelayedExecutor mDelayedExecutor;
    @Inject protected FakeDeviceUtils mFakeDeviceUtil;
    @Inject protected SharedPreferences mSharedPreferences;
    @Inject protected FakeUserHelper mFakeUserHelper;
    @Inject protected FakeContextualModeManager mFakeContextualModeManager;
    @Inject protected FakeResources mFakeResources;
    @Inject protected FakeFrameworkStatsLogProxy mFakeFrameworkStatsLogProxy;

    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mApplication = (TestApplication) mContext;
        mApplication.reCreateDaggerComponent();
        mApplicationComponent = mApplication.getApplicationComponent();
        mApplicationComponent.inject(this);
        // Clear shared preferences
        mSharedPreferences.edit().clear().commit();
        mFakeResources.reset();
    }
}
