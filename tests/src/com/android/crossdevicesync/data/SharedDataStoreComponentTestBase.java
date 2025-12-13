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
import com.android.crossdevicesync.data.fake.FakeSharedDataStore;
import com.android.crossdevicesync.data.fake.TestSharedDataStoreFactory;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.data.storage.fake.FakeStorage;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import java.util.List;

import javax.inject.Inject;

/** Base class for tests dependent on the shared storage component. */
public abstract class SharedDataStoreComponentTestBase extends SyncServiceComponentTestBase {
    public static final String DOC_ID = "test_doc";

    protected TestSharedDataStoreComponent mSharedDataStoreComponent;
    protected FakeStorage mFakeStorage;
    @Inject @DataStoreName protected String mDataStoreName;
    @Inject protected SharedDataStore<?> mSharedDataStore;
    @Inject protected SubmergeSharedDataStore<?> mSubmergeSharedDataStore;
    @Inject protected FakeSharedDataStore<?> mFakeSharedDataStore;
    @Inject protected TestSharedDataStoreFactory<?> mTestSharedDataStoreFactory;
    @Inject protected SharedDataStoreHandle<?> mSharedDataStoreHandle;
    @Inject protected SchemaProvider<?> mSchemaProvider;

    @Override
    public void setUp() {
        super.setUp();
        mFakeStorage = new FakeStorage();
        mFakeNetworkManager.init();
        mSharedDataStoreComponent =
                newSharedDataStoreComponent(
                        getClass().getSimpleName(), getSchemaProvider(), mFakeStorage);
        mSharedDataStoreComponent.inject(this);
    }

    protected TestSharedDataStoreComponent newSharedDataStoreComponent(
            String dataStoreName, SchemaProvider<String> schemaProvider, IStorage storage) {
        return (TestSharedDataStoreComponent)
                mSyncServiceComponent
                        .getTestSharedDataStoreComponentBuilder()
                        .name(dataStoreName)
                        .converter(mStringConverter)
                        .schemaProvider(schemaProvider)
                        .storage(storage)
                        .build();
    }

    protected SchemaProvider<String> getSchemaProvider() {
        return new SchemaProvider<>() {
            @Override
            public List<DocumentSchemaInfo> getAllDocumentSchema() {
                return List.of();
            }

            @Override
            public void migrateDocument(MutableDocument<String> document) {}
        };
    }

    public SharedDataStore<?> getSharedDataStore() {
        return mSharedDataStore;
    }
}
