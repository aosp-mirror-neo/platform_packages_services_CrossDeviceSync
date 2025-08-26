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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.data.SchemaProvider.DocumentSchemaInfo;
import com.android.crossdevicesync.data.SharedDataStore.Document;
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.crossdevicesync.data.SharedDataStore.Record;
import com.android.crossdevicesync.data.SharedDataStore.SetRecord;
import com.android.crossdevicesync.data.SharedDataStore.UnmergedRecord;
import com.android.crossdevicesync.data.fake.FakeStorage;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class SubmergeSharedDataStoreTest extends SharedDataStoreComponentTestBase {
    private SubmergeSharedDataStore<String> mDataStore;
    private RemoteDeviceContext mRemoteDevice1;
    private RemoteDeviceContext mRemoteDevice2;

    @Before
    public void setUp() {
        super.setUp();
        mTimestampProvider.setTimeStamp(100);
        mDataStore = (SubmergeSharedDataStore<String>) mSubmergeSharedDataStore;
        mDataStore.init();
        mRemoteDevice1 = new RemoteDeviceContext("device_1");
        mRemoteDevice2 = new RemoteDeviceContext("device_2");
    }

    @After
    public void tearDown() {
        mDataStore.close();
        mRemoteDevice1.close();
        mRemoteDevice2.close();
    }

    @Override
    protected SchemaProvider<String> getSchemaProvider() {
        return new TestSchemaProvider(
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema("/root/register", SchemaProvider.TYPE_REGISTER)
                        .putPathSchema("/root/set", SchemaProvider.TYPE_SET)
                        .putPathSchema("/root/unmerged", SchemaProvider.TYPE_UNMERGED)
                        .build());
    }

    @Test
    public void testInit_schemaInitialized() throws Exception {
        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            assertThat(doc.getSchemaVersion()).isEqualTo(1);
                            Record<String> r = doc.getRecord("/root/register");
                            assertThat(r.get()).isNull();
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                            SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/set");
                            assertThat(set.entries()).isEmpty();
                            assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                            UnmergedRecord<String> unmerged =
                                    (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                            assertThat(unmerged.entries()).isEmpty();
                            assertThat(unmerged.getMetadata().isLastModifiedByLocalDevice())
                                    .isTrue();
                            return true;
                        });

        assertThat(future.get()).isTrue();
        assertThat(mFakeStorage.getDocument(DOC_ID)).isNotNull();
        assertThat(mFakeStorage.getDocumentSchema(DOC_ID)).isNotNull();
        assertThat(mFakeStorage.getMetadata(DOC_ID)).isNotNull();
    }

    @Test
    public void testInit_schemaVersionUpgraded() throws Exception {
        upgradeSchema(
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/set", SchemaProvider.TYPE_SET)
                        .build());

        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            assertThat(doc.getSchemaVersion()).isEqualTo(2);
                            assertThat(doc.getRecord("/root/register")).isNotNull();
                            SetRecord<String> set =
                                    (SetRecord<String>) doc.getRecord("/root/v2/set");
                            assertThat(set).isNotNull();
                            assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                            return true;
                        });

        assertThat(future.get()).isTrue();
    }

    @Test
    public void testInit_schemaUpgradeNotOverrideExistingData() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/set", SchemaProvider.TYPE_SET)
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            doc.addDataToSet("/root/v2/set", "abc");
                            return true;
                        })
                .get();

        // Sync to local device.
        connectNetworks(this, mRemoteDevice1);
        mDataStore.requestSyncWithRemoteDevice(mRemoteDevice1.nodeId());
        flushNetworks(this, mRemoteDevice1);

        // Verify sync was successful.
        mDataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/v2/set");
                            assertThat(r.entries().contains("abc")).isTrue();
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();

        // Local device also upgrades the schema.
        upgradeSchema(schema);

        // Verify that the data is still present.
        mDataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/v2/set");
                            assertThat(r.entries().contains("abc")).isTrue();
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();
    }

    @Test
    public void testInit_migrationOverridesPastChanges() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/register", SchemaProvider.TYPE_REGISTER)
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            doc.putData("/root/v2/register", "abc");
                            return true;
                        })
                .get();

        // Sync to local device.
        connectNetworks(this, mRemoteDevice1);
        mDataStore.requestSyncWithRemoteDevice(mRemoteDevice1.nodeId());
        flushNetworks(this, mRemoteDevice1);

        // Verify sync was successful.
        mDataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/v2/register");
                            assertThat(r.get()).isEqualTo("abc");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();

        // Local device upgrades schema, but overrides the new path.
        upgradeSchema(
                /* migrator= */ doc -> {
                    doc.putData("/root/v2/register", null);
                },
                /* validator= */ null,
                schema);

        // Verify that the local change wins.
        mDataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/v2/register");
                            assertThat(r.get()).isNull();
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                            return true;
                        })
                .get();

        // Verify the remote device 1 data is overridden.
        flushNetworks(this, mRemoteDevice1);
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/v2/register");
                            assertThat(r.get()).isNull();
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();
    }

    @Test
    public void testInit_migrationNotOverridingConcurrentChanges() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema("/root/v2/register", SchemaProvider.TYPE_REGISTER)
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            doc.putData("/root/v2/register", "abc");
                            return true;
                        })
                .get();

        // Local device upgrades schema, but overrides the new path.
        upgradeSchema(
                /* migrator= */ doc -> {
                    doc.putData("/root/v2/register", null);
                },
                /* validator= */ null,
                schema);

        // Sync happens.
        connectNetworks(this, mRemoteDevice1);
        mDataStore.requestSyncWithRemoteDevice(mRemoteDevice1.nodeId());
        flushNetworks(this, mRemoteDevice1);

        // Verify that the local change is overridden.
        mDataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/v2/register");
                            assertThat(r.get()).isEqualTo("abc");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();

        // Verify the remote device 1 data is NOT overridden.
        flushNetworks(this, mRemoteDevice1);
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/v2/register");
                            assertThat(r.get()).isEqualTo("abc");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                            return true;
                        })
                .get();
    }

    @Test
    public void testInit_upgradePathIsBad_fails() throws Exception {
        ListenableFuture<Boolean> future =
                upgradeSchema(
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(2)
                                .putPathSchema("/root/register", SchemaProvider.TYPE_SET)
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(mDataStore.isOpen()).isFalse();
    }

    @Test
    public void testInit_upgradeValidationFails() throws Exception {
        ListenableFuture<Boolean> future =
                upgradeSchema(
                        /* migrator= */ null,
                        /* validator= */ doc -> {
                            throw new Exception();
                        },
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(2)
                                .putPathSchema("/root/v2/set", SchemaProvider.TYPE_SET)
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(mDataStore.isOpen()).isFalse();
    }

    @Test
    public void testInit_upgradeIgnoredWithSameSchemaVersion() throws Exception {
        ListenableFuture<Boolean> future =
                upgradeSchema(getSchemaProvider().getAllDocumentSchema().get(0));

        assertThat(future.get()).isTrue();
        assertThat(mDataStore.isOpen()).isTrue();
    }

    @Test
    public void testInit_upgradeSchemaWithSameVersion_failedValidation() throws Exception {
        // Changes schema without changing version.
        ListenableFuture<Boolean> future =
                upgradeSchema(
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(1)
                                .putPathSchema("/root/v2/register", SchemaProvider.TYPE_REGISTER)
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(mDataStore.isOpen()).isFalse();
    }

    @Test
    public void testTransaction_success() throws Exception {
        // Write data.
        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            doc.putData("/root/register", "abc");
                            return true;
                        });

        assertThat(future.get()).isTrue();

        // Read data and assert it's correct.
        future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/register");
                            assertThat(r).isNotNull();
                            assertThat(r.get()).isEqualTo("abc");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                            return true;
                        });
        assertThat(future.get()).isTrue();
    }

    @Test
    public void testTransaction_applierFail() throws Exception {
        // Write illegal data
        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            doc.putUnmergedData("/", "abc");
                            return true;
                        });

        assertThrows(ExecutionException.class, future::get);

        // Read data and assert no data was written.
        future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/");
                            assertThat(r).isNull();
                            return true;
                        });
        assertThat(future.get()).isTrue();
    }

    @Test
    public void testTransaction_storageFail() throws Exception {
        // Write data.
        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            doc.putData("/root/register", "abc");
                            // Close storage so that accessing it will trigger an exception.
                            mFakeStorage.close();
                            return true;
                        });

        assertThrows(ExecutionException.class, future::get);

        // Recover storage and verify nothing is written.
        mFakeStorage.init();
        future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/register");
                            assertThat(r.get()).isNull();
                            return true;
                        });
        assertThat(future.get()).isTrue();
    }

    @Test
    public void testTransaction_documentNotAccessibleOutOfTransaction() throws Exception {
        // Open and cache the doc.
        MutableDocument<String>[] document = new MutableDocument[1];
        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            document[0] = doc;
                            return true;
                        });

        assertThat(future.get()).isTrue();
        assertThrows(
                IllegalStateException.class, () -> document[0].putData("/root/register", "abc"));
        assertThrows(IllegalStateException.class, () -> document[0].getRecord("/root/register"));
    }

    @Test
    public void testTransaction_multiDoc_independentlyUpdate() throws Exception {
        // Create 2 docs.
        upgradeSchema(
                DocumentSchemaInfo.builder()
                        .setDocId("doc_1")
                        .setVersion(1)
                        .putPathSchema("/root", SchemaProvider.TYPE_REGISTER)
                        .build(),
                DocumentSchemaInfo.builder()
                        .setDocId("doc_2")
                        .setVersion(1)
                        .putPathSchema("/root", SchemaProvider.TYPE_SET)
                        .build());

        mDataStore.transact(
                "doc_1",
                doc -> {
                    doc.putData("/root", "abc");
                    return true;
                });
        mDataStore.transact(
                "doc_2",
                doc -> {
                    doc.addDataToSet("/root", "def");
                    return true;
                });

        // Verify their value are independent.
        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        "doc_1",
                        doc -> {
                            Record<String> r = doc.getRecord("/root");
                            assertThat(r).isNotNull();
                            assertThat(r.get()).isEqualTo("abc");
                            return true;
                        });
        assertThat(future.get()).isTrue();
        future =
                mDataStore.transact(
                        "doc_2",
                        doc -> {
                            SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root");
                            assertThat(r).isNotNull();
                            assertThat(r.entries().contains("def")).isTrue();
                            return true;
                        });
        assertThat(future.get()).isTrue();
    }

    @Test
    public void testNetworkUpdate_remoteDataVisible() throws Exception {
        // Update initiated from remote device.
        connectNetworks(this, mRemoteDevice1);
        ListenableFuture<Boolean> future =
                mRemoteDevice1
                        .getDataStore()
                        .transact(
                                DOC_ID,
                                doc -> {
                                    doc.putData("/root/register", "abc");
                                    return true;
                                });
        assertThat(future.get()).isTrue();
        flushNetworks(this, mRemoteDevice1);

        // Verify it's visible to local device.
        future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/register");
                            assertThat(r.get()).isEqualTo("abc");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        });
        assertThat(future.get()).isTrue();
    }

    @Test
    public void testConcurrentNetworkUpdate_differentPath_merged() throws Exception {
        // Concurrent update from different devices.
        mDataStore.transact(
                DOC_ID,
                doc -> {
                    doc.putData("/root/register", "abc");
                    return true;
                });
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            doc.addDataToSet("/root/set", "def");
                            return true;
                        });
        mRemoteDevice2
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            doc.putUnmergedData("/root/unmerged", "bla");
                            return true;
                        });

        // Connect with device 1 and trigger a sync.
        connectNetworks(this, mRemoteDevice1);
        mDataStore.requestSyncWithRemoteDevice(mRemoteDevice1.nodeId());
        flushNetworks(this, mRemoteDevice1);

        // Verify the data is merged.
        mDataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/set");
                            assertThat(r.entries().contains("def")).isTrue();
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/register");
                            assertThat(r.get()).isEqualTo("abc");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();

        // Connect with device 2 and trigger a sync.
        connectNetworks(this, mRemoteDevice1, mRemoteDevice2);
        mDataStore.requestSyncWithRemoteDevice(mRemoteDevice2.nodeId());
        mRemoteDevice2.getDataStore().requestSyncWithRemoteDevice(mRemoteDevice1.nodeId());
        flushNetworks(this, mRemoteDevice1, mRemoteDevice2);

        // Verify the data is merged.
        mDataStore
                .transact(
                        DOC_ID,
                        doc -> {
                            UnmergedRecord<String> r =
                                    (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                            assertThat(r.get(mRemoteDevice2.nodeId())).isEqualTo("bla");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();
        mRemoteDevice1
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            UnmergedRecord<String> r =
                                    (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                            assertThat(r.get(mRemoteDevice2.nodeId())).isEqualTo("bla");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();
        mRemoteDevice2
                .getDataStore()
                .transact(
                        DOC_ID,
                        doc -> {
                            Record<String> r = doc.getRecord("/root/register");
                            assertThat(r.get()).isEqualTo("abc");
                            assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/set");
                            assertThat(set.entries().contains("def")).isTrue();
                            assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                            return true;
                        })
                .get();
    }

    @Test
    public void testClose() throws Exception {
        ListenableFuture<Boolean> future =
                mDataStore.transact(
                        DOC_ID,
                        doc -> {
                            doc.putData("/root/register", "abc");
                            return true;
                        });
        assertThat(future.get()).isTrue();

        mDataStore.close();

        assertThrows(
                ExecutionException.class, () -> mDataStore.transact(DOC_ID, doc -> null).get());
        assertThrows(IllegalStateException.class, () -> mDataStore.init());
        assertThat(mFakeStorage.isOpen()).isFalse();
        assertThat(mExecutorService.isShutdown()).isTrue();
    }

    private static void connectNetworks(SharedDataStoreComponentTestBase... testContexts) {
        for (SharedDataStoreComponentTestBase a : testContexts) {
            for (SharedDataStoreComponentTestBase b : testContexts) {
                if (a == b) {
                    continue;
                }
                a.getNetwork().connect(b.getNetwork());
                b.getNetwork().connect(a.getNetwork());
            }
        }
    }

    private static void flushNetworks(SharedDataStoreComponentTestBase... testContexts) {
        while (true) {
            boolean allFlushed = true;
            for (SharedDataStoreComponentTestBase t : testContexts) {
                allFlushed &= !t.getNetwork().flushMessages();
            }
            if (allFlushed) {
                break;
            }
        }
    }

    private ListenableFuture<Boolean> upgradeSchema(DocumentSchemaInfo... schema) {
        return upgradeSchema(null, null, schema);
    }

    private ListenableFuture<Boolean> upgradeSchema(
            @Nullable Consumer<MutableDocument<String>> migrator,
            @Nullable ThrowingConsumer<Document<String>> validator,
            DocumentSchemaInfo... schema) {
        ListenableFuture<Boolean> future = upgradeSchema(this, migrator, validator, schema);
        mDataStore = (SubmergeSharedDataStore<String>) mSubmergeSharedDataStore;
        return future;
    }

    private ListenableFuture<Boolean> upgradeSchema(
            SharedDataStoreComponentTestBase testContext, DocumentSchemaInfo... schema) {
        return upgradeSchema(testContext, null, null, schema);
    }

    private ListenableFuture<Boolean> upgradeSchema(
            SharedDataStoreComponentTestBase testContext,
            @Nullable Consumer<MutableDocument<String>> migrator,
            @Nullable ThrowingConsumer<Document<String>> validator,
            DocumentSchemaInfo... schema) {
        // Close the current data store. And build a new one with a higher schema version.
        testContext.mSubmergeSharedDataStore.close();
        FakeStorage prevStorage = testContext.mFakeStorage;
        testContext
                .newSharedDataStoreComponent(
                        testContext.mDataStoreName,
                        new TestSchemaProvider(schema)
                                .setMigrator(migrator)
                                .setValidator(validator))
                .inject(testContext);
        testContext.mFakeStorage.copyAll(prevStorage);
        return testContext.mSubmergeSharedDataStore.init();
    }

    private class RemoteDeviceContext extends SharedDataStoreComponentTestBase {
        RemoteDeviceContext(String nodeId) {
            super.setUp();
            mDeviceNodeIdProvider.setNodeIdForDataStore(mDataStoreName, nodeId);
            mTimestampProvider.setTimeStamp(
                    SubmergeSharedDataStoreTest.this.mTimestampProvider.peekNow());
            mSubmergeSharedDataStore.init();
        }

        @Override
        protected SchemaProvider<String> getSchemaProvider() {
            return SubmergeSharedDataStoreTest.this.getSchemaProvider();
        }

        public SubmergeSharedDataStore<String> getDataStore() {
            return (SubmergeSharedDataStore<String>) mSubmergeSharedDataStore;
        }

        public void close() {
            mSubmergeSharedDataStore.close();
        }

        public String nodeId() {
            return mSubmergeSharedDataStore.getLocalDeviceNodeId();
        }
    }

    private static class TestSchemaProvider implements SchemaProvider<String> {
        private final List<DocumentSchemaInfo> mSchemaList = new ArrayList<>();
        @Nullable private Consumer<MutableDocument<String>> mMigrator;
        @Nullable private ThrowingConsumer<Document<String>> mValidator;

        TestSchemaProvider(DocumentSchemaInfo schema) {
            this(new DocumentSchemaInfo[] {schema});
        }

        TestSchemaProvider(DocumentSchemaInfo... schemas) {
            Collections.addAll(mSchemaList, schemas);
        }

        @Override
        public List<DocumentSchemaInfo> getAllDocumentSchema() {
            return mSchemaList;
        }

        public TestSchemaProvider setMigrator(
                @Nullable Consumer<MutableDocument<String>> migrator) {
            mMigrator = migrator;
            return this;
        }

        @Override
        public void migrateDocument(MutableDocument<String> document) {
            if (mMigrator != null) {
                mMigrator.accept(document);
            }
        }

        public TestSchemaProvider setValidator(
                @Nullable ThrowingConsumer<Document<String>> validator) {
            mValidator = validator;
            return this;
        }

        @Override
        public void validateDocument(Document<String> document) throws SchemaValidationException {
            SchemaProvider.super.validateDocument(document);
            if (mValidator != null) {
                try {
                    mValidator.acceptOrThrow(document);
                } catch (Exception e) {
                    throw new SchemaValidationException(e);
                }
            }
        }
    }
}
