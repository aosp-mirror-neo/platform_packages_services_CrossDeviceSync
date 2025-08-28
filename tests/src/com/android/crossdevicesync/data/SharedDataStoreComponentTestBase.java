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
package com.android.crossdevicesync.data;

import com.android.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.crossdevicesync.data.dagger.TestSharedDataStoreComponent;
import com.android.crossdevicesync.data.dagger.qualifier.DataStoreName;
import com.android.crossdevicesync.data.dagger.qualifier.IOThread;
import com.android.crossdevicesync.data.fake.FakeNetwork;
import com.android.crossdevicesync.data.fake.FakeStorage;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

/** Base class for tests dependent on the shared storage component. */
public abstract class SharedDataStoreComponentTestBase extends SyncServiceComponentTestBase {
    public static final String DOC_ID = "test_doc";

    protected TestSharedDataStoreComponent mSharedDataStoreComponent;
    @Inject protected RoomStorage mRoomStorage;
    @Inject protected FakeStorage mFakeStorage;
    @Inject @DataStoreName protected String mDataStoreName;
    @Inject protected SubmergeSharedDataStore<?> mSubmergeSharedDataStore;
    @Inject @IOThread protected ExecutorService mExecutorService;
    protected FakeNetwork.Factory mNetworkFactory;

    @Override
    public void setUp() {
        super.setUp();
        mNetworkFactory = new FakeNetwork.Factory();
        mSharedDataStoreComponent =
                newSharedDataStoreComponent(getClass().getSimpleName(), getSchemaProvider());
        mSharedDataStoreComponent.inject(this);
    }

    protected TestSharedDataStoreComponent newSharedDataStoreComponent(
            String dataStoreName, SchemaProvider<String> schemaProvider) {
        return (TestSharedDataStoreComponent)
                mSyncServiceComponent
                        .getTestSharedDataStoreComponentBuilder()
                        .name(dataStoreName)
                        .converter(mStringConverter)
                        .schemaProvider(schemaProvider)
                        .networkFactory(mNetworkFactory)
                        .build();
    }

    protected SchemaProvider<String> getSchemaProvider() {
        return new SchemaProvider<String>() {
            @Override
            public List<DocumentSchemaInfo> getAllDocumentSchema() {
                return List.of();
            }

            @Override
            public void migrateDocument(MutableDocument<String> document) {}
        };
    }

    protected FakeNetwork getNetwork() {
        // This will create a new network or get the existing one if available.
        return mNetworkFactory.create(mSubmergeSharedDataStore.getLocalDeviceNodeId());
    }
}
