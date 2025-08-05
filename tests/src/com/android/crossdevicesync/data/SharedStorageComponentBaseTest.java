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
package com.android.crossdevicesync.data;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.crossdevicesync.TestApplication;
import com.android.crossdevicesync.dagger.TestApplicationComponent;
import com.android.crossdevicesync.data.dagger.TestSharedDataStoreComponent;
import com.android.crossdevicesync.services.dagger.TestSyncServiceComponent;

import javax.inject.Inject;

/** Base class for test cases against objects in shared storage component. */
public abstract class SharedStorageComponentBaseTest {

    protected Context mContext;
    @Inject protected RoomStorage mRoomStorage;

    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestApplicationComponent applicationComponent =
                ((TestApplication) mContext).getApplicationComponent();
        TestSyncServiceComponent syncServiceComponent =
                applicationComponent.getTestSyncServiceComponentBuilder().build();
        TestSharedDataStoreComponent sharedDataStoreComponent =
                (TestSharedDataStoreComponent)
                        syncServiceComponent
                                .getTestSharedDataStoreComponentBuilder()
                                .name(getDataStoreName())
                                .build();
        sharedDataStoreComponent.inject(this);
    }

    protected String getDataStoreName() {
        return getClass().getSimpleName();
    }
}
