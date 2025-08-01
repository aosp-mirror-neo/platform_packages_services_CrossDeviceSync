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

import com.android.crossdevicesync.data.dagger.TestSharedDataStoreComponent;
import com.android.crossdevicesync.data.fake.FakeNetwork;
import com.android.crossdevicesync.data.fake.FakeStorage;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import javax.inject.Inject;

/** Base class for tests dependent on the shared storage component. */
public abstract class SharedStorageComponentTestBase extends SyncServiceComponentTestBase {

    protected TestSharedDataStoreComponent mSharedDataStoreComponent;
    @Inject protected RoomStorage mRoomStorage;
    @Inject protected FakeStorage mFakeStorage;
    @Inject protected FakeNetwork mFakeNetwork;

    @Override
    public void setUp() {
        super.setUp();
        mSharedDataStoreComponent = newSharedDataStoreComponent(getClass().getSimpleName());
        mSharedDataStoreComponent.inject(this);
    }

    protected TestSharedDataStoreComponent newSharedDataStoreComponent(String dataStoreName) {
        return (TestSharedDataStoreComponent)
                mSyncServiceComponent
                        .getTestSharedDataStoreComponentBuilder()
                        .name(dataStoreName)
                        .converter(mStringConverter)
                        .build();
    }
}
