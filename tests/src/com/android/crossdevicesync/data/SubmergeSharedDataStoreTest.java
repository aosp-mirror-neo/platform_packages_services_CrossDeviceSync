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
import com.android.crossdevicesync.data.SchemaProvider.PathSchemaInfo;
import com.android.crossdevicesync.data.SharedDataStore.Document;
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.crossdevicesync.data.SharedDataStore.Record;
import com.android.crossdevicesync.data.SharedDataStore.SetRecord;
import com.android.crossdevicesync.data.SharedDataStore.UnmergedRecord;
import com.android.crossdevicesync.data.fake.FakeStorage;
import com.android.crossdevicesync.data.fake.FakeTimestampProvider;
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
    private RemoteDeviceContext mRemoteDevice3;

    @Before
    public void setUp() {
        super.setUp();
        mTimestampProvider.setTimeStamp(100);
        mDataStore = (SubmergeSharedDataStore<String>) mSubmergeSharedDataStore;
        mDataStore.init();
        mRemoteDevice1 = new RemoteDeviceContext();
        mRemoteDevice2 = new RemoteDeviceContext();
        mRemoteDevice3 = new RemoteDeviceContext();
    }

    @After
    public void tearDown() {
        mDataStore.close();
        mRemoteDevice1.close();
        mRemoteDevice2.close();
        mRemoteDevice3.close();
    }

    @Override
    protected SchemaProvider<String> getSchemaProvider() {
        return new TestSchemaProvider(
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/register", SchemaProvider.TYPE_REGISTER, 1))
                        .putPathSchema(new PathSchemaInfo("/root/set", SchemaProvider.TYPE_SET, 1))
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/unmerged", SchemaProvider.TYPE_UNMERGED, 1))
                        .build());
    }

    @Test
    public void testInit_schemaInitialized() throws Exception {
        transactDataStore(
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
                    assertThat(unmerged.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });

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
                        .putPathSchema(
                                new PathSchemaInfo("/root/v2/set", SchemaProvider.TYPE_SET, 2))
                        .build());

        transactDataStore(
                doc -> {
                    assertThat(doc.getSchemaVersion()).isEqualTo(2);
                    assertThat(doc.getRecord("/root/register")).isNotNull();
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(set).isNotNull();
                    assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testInit_schemaUpgradeNotOverrideExistingData() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo("/root/v2/set", SchemaProvider.TYPE_SET, 2))
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        transactDataStore(mRemoteDevice1, doc -> doc.addDataToSet("/root/v2/set", "abc"));

        // Sync to local device.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify sync was successful.
        transactDataStore(
                doc -> {
                    SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(r.entries().contains("abc")).isTrue();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Local device also upgrades the schema.
        upgradeSchema(schema);

        // Verify that the data is still present.
        transactDataStore(
                doc -> {
                    SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(r.entries().contains("abc")).isTrue();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void testInit_migrationOverridesPastChanges() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/v2/register", SchemaProvider.TYPE_REGISTER, 2))
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/v2/register", "abc"));

        // Sync to local device.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify sync was successful.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Local device upgrades schema, but overrides the new path.
        upgradeSchema(
                /* migrator= */ doc -> {
                    doc.putData("/root/v2/register", null);
                },
                /* validator= */ null,
                schema);

        // Verify that the local change wins.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isNull();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });

        // Verify the remote device 1 data is overridden.
        flushNetworks(this, mRemoteDevice1);
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isNull();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void testInit_migrationNotOverridingConcurrentChanges() throws Exception {
        // Remote device 1 upgrades the schema.
        DocumentSchemaInfo schema =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/v2/register", SchemaProvider.TYPE_REGISTER, 2))
                        .build();
        upgradeSchema(mRemoteDevice1, schema);

        // Remote device 1 changes the data in the new path.
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/v2/register", "abc"));

        // Local device upgrades schema, but overrides the new path.
        upgradeSchema(
                /* migrator= */ doc -> {
                    doc.putData("/root/v2/register", null);
                },
                /* validator= */ null,
                schema);

        // Sync happens.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify that the local change is overridden.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Verify the remote device 1 data is NOT overridden.
        flushNetworks(this, mRemoteDevice1);
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testInit_upgradePathIsBad_fails() throws Exception {
        ListenableFuture<Boolean> future =
                upgradeSchema(
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(2)
                                .putPathSchema(
                                        new PathSchemaInfo(
                                                "/root/register", SchemaProvider.TYPE_SET, 2))
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(IllegalSchemaChangeException.class);
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
                                .putPathSchema(
                                        new PathSchemaInfo(
                                                "/root/v2/set", SchemaProvider.TYPE_SET, 2))
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
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
                                .putPathSchema(
                                        new PathSchemaInfo(
                                                "/root/v2/register",
                                                SchemaProvider.TYPE_REGISTER,
                                                1))
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        assertThat(mDataStore.isOpen()).isFalse();
    }

    @Test
    public void testInit_upgradeIncompatibleWithExistingData_fail() throws Exception {
        // Remote device 1 upgrades the schema.
        upgradeSchema(
                mRemoteDevice1,
                /* migrator= */ doc -> {
                    doc.putData("/root/v2/new", "abc");
                },
                /* validator= */ null,
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo("/root/v2/new", SchemaProvider.TYPE_REGISTER, 2))
                        .build());

        // Connect with remote device 1 and sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify data is synced.
        transactDataStore(doc -> assertThat(doc.getRecord("/root/v2/new").get()).isEqualTo("abc"));

        // Upgrade local device with incompatible schema.
        ListenableFuture<Boolean> future =
                upgradeSchema(
                        DocumentSchemaInfo.builder()
                                .setDocId(DOC_ID)
                                .setVersion(2)
                                .putPathSchema(
                                        new PathSchemaInfo(
                                                "/root/v2/new", SchemaProvider.TYPE_SET, 2))
                                .build());

        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        assertThat(mDataStore.isOpen()).isFalse();
    }

    @Test
    public void testInit_failureReceoveredByDeletingDB() throws Exception {
        // Illegal upgrade.
        DocumentSchemaInfo info =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo("/root/register", SchemaProvider.TYPE_SET, 2))
                        .build();
        ListenableFuture<Boolean> future = upgradeSchema(info);
        assertThrows(ExecutionException.class, future::get);
        assertThat(future.exceptionNow()).isInstanceOf(IllegalSchemaChangeException.class);

        // Delete the DB
        assertThat(mDataStore.delete().get()).isTrue();

        // Re-init after deletion should succeed.
        assertThat(upgradeSchema(info).get()).isTrue();
        transactDataStore(
                doc -> {
                    assertThat(doc.getSchemaVersion()).isEqualTo(2);
                    assertThat(doc.getRecord("/root/register")).isInstanceOf(SetRecord.class);
                });
    }

    @Test
    public void testTransaction_success() throws Exception {
        // Write data.
        transactDataStore(doc -> doc.putData("/root/register", "abc"));

        // Read data and assert it's correct.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r).isNotNull();
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testTransaction_applierFail() throws Exception {
        // Write illegal data
        assertThrows(
                ExecutionException.class,
                () -> transactDataStore(doc -> doc.putUnmergedData("/", "abc")));

        // Read data and assert no data was written.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/");
                    assertThat(r).isNull();
                });
    }

    @Test
    public void testTransaction_storageFail() throws Exception {
        // Write data.
        assertThrows(
                ExecutionException.class,
                () ->
                        transactDataStore(
                                doc -> {
                                    doc.putData("/root/register", "abc");
                                    // Close storage so that accessing it will trigger an exception.
                                    mFakeStorage.close();
                                }));

        // Recover storage and verify nothing is written.
        mFakeStorage.init();
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isNull();
                });
    }

    @Test
    public void testTransaction_documentNotAccessibleOutOfTransaction() throws Exception {
        // Open and cache the doc.
        MutableDocument<String>[] document = new MutableDocument[1];
        transactDataStore(doc -> document[0] = doc);

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
                        .putPathSchema(new PathSchemaInfo("/root", SchemaProvider.TYPE_REGISTER, 1))
                        .build(),
                DocumentSchemaInfo.builder()
                        .setDocId("doc_2")
                        .setVersion(1)
                        .putPathSchema(new PathSchemaInfo("/root", SchemaProvider.TYPE_SET, 1))
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
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "abc"));
        flushNetworks(this, mRemoteDevice1);

        // Verify it's visible to local device.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void testConcurrentNetworkUpdate_differentPath_merged() throws Exception {
        // Concurrent update from different devices.
        transactDataStore(doc -> doc.putData("/root/register", "abc"));
        transactDataStore(mRemoteDevice1, doc -> doc.addDataToSet("/root/set", "def"));
        transactDataStore(mRemoteDevice2, doc -> doc.putUnmergedData("/root/unmerged", "bla"));

        // Connect with device 1 and trigger a sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify the data is merged.
        transactDataStore(
                doc -> {
                    SetRecord<String> r = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(r.entries().contains("def")).isTrue();
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });

        // Connect with device 2 and trigger a sync.
        connectNetworks(this, mRemoteDevice1, mRemoteDevice2);
        flushNetworks(this, mRemoteDevice1, mRemoteDevice2);

        // Verify the data is merged.
        transactDataStore(
                doc -> {
                    UnmergedRecord<String> r =
                            (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                    assertThat(r.get(mRemoteDevice2.nodeId())).isEqualTo("bla");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    UnmergedRecord<String> r =
                            (UnmergedRecord<String>) doc.getRecord("/root/unmerged");
                    assertThat(r.get(mRemoteDevice2.nodeId())).isEqualTo("bla");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice2,
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("abc");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(set.entries().contains("def")).isTrue();
                    assertThat(set.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
    }

    @Test
    public void testConcurrentNetworkUpdate_remoteSchemaIncompatible_fail() throws Exception {
        // Remote device 1 upgrades the schema.
        upgradeSchema(
                mRemoteDevice1,
                /* migrator= */ doc -> {
                    doc.putData("/root/v2/new", "abc");
                },
                /* validator= */ null,
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo("/root/v2/new", SchemaProvider.TYPE_REGISTER, 2))
                        .build());

        // Local device upgrades with incompatible schema.
        upgradeSchema(
                /* migrator= */ doc -> {
                    doc.addDataToSet("/root/v2/new", "abc");
                },
                /* validator= */ null,
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo("/root/v2/new", SchemaProvider.TYPE_SET, 2))
                        .build());

        // Connect and sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify devices stay out of sync.
        transactDataStore(
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/v2/new");
                    assertThat(set.entries().contains("abc")).isTrue();
                });

        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/v2/new");
                    assertThat(r).isNotInstanceOf(SetRecord.class);
                    assertThat(r.get()).isEqualTo("abc");
                });
    }

    @Test
    public void testConcurrentNetworkUpdate_samePath_latestTimestampWins() throws Exception {
        // Concurrent update from different devices.
        mTimestampProvider.setTimeStamp(200);
        transactDataStore(doc -> doc.putData("/root/register", "local"));
        mRemoteDevice1.getTimestampProvider().setTimeStamp(mTimestampProvider.peekNow() + 100);
        transactDataStore(mRemoteDevice1, doc -> doc.putData("/root/register", "remote"));

        // Connect with device 1 and trigger a sync.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // Verify the remote data wins.
        transactDataStore(
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("remote");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
                });
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("remote");
                    assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isTrue();
                });
    }

    @Test
    public void testConcurrentNetworkUpdate_threeDevices_eventOrderWins() throws Exception {
        // This test verifies that event order is prioritized over timestamp in a 3-device scenario.
        // Let's denote mRemoteDevice1 as A, local device as B, and mRemoteDevice2 as C.
        RemoteDeviceContext a = mRemoteDevice1;
        SubmergeSharedDataStoreTest b = this;
        RemoteDeviceContext c = mRemoteDevice2;

        // 1. A with a larger timestamp writes data.
        a.getTimestampProvider().setTimeStamp(300);
        transactDataStore(a, doc -> doc.putData("/root/register", "A"));

        // 2. A syncs with B.
        connectNetworks(b, a);
        flushNetworks(b, a);
        transactDataStore(
                b, doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("A"));

        // 3. B writes the data without syncing to A.
        b.mTimestampProvider.setTimeStamp(200); // B has a smaller timestamp.
        transactDataStore(b, doc -> doc.putData("/root/register", "B"));

        // 4. A syncs with C.
        connectNetworks(a, c);
        flushNetworks(a, c);
        transactDataStore(
                c, doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("A"));

        // 5. B syncs with C.
        connectNetworks(b, c);
        flushNetworks(b, c);
        transactDataStore(
                c, doc -> assertThat(doc.getRecord("/root/register").get()).isEqualTo("B"));

        // 6. Flush all pending messages.
        flushNetworks(b, a, c);

        // 7. Verify A and B's state are right. All devices should have B's data.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> {
                    Record<String> r = doc.getRecord("/root/register");
                    assertThat(r.get()).isEqualTo("B");
                };

        transactDataStore(b, verifier);
        transactDataStore(a, verifier);
        transactDataStore(c, verifier);
    }

    @Test
    public void testConcurrentUpdate_upgradedFromDifferentBaseSchemaVersion_merged()
            throws Exception {
        // 1. Define schema v2 with a new set.
        DocumentSchemaInfo schemaV2 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/register", SchemaProvider.TYPE_REGISTER, 1))
                        .putPathSchema(new PathSchemaInfo("/root/set", SchemaProvider.TYPE_SET, 1))
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/unmerged", SchemaProvider.TYPE_UNMERGED, 1))
                        .putPathSchema(
                                new PathSchemaInfo("/root/v2/set", SchemaProvider.TYPE_SET, 2))
                        .build();

        // 2. Setup Device 1 (local device), upgrading from v1 to v2.
        upgradeSchema(schemaV2).get();
        mTimestampProvider.setTimeStamp(200);
        transactDataStore(doc -> doc.addDataToSet("/root/v2/set", "local"));

        // 3. Setup Device 2 (remote device) to initialize with schema v2 directly.
        reInitWithNewSchema(mRemoteDevice1, schemaV2).get();
        mRemoteDevice1.getTimestampProvider().setTimeStamp(300);
        transactDataStore(mRemoteDevice1, doc -> doc.addDataToSet("/root/v2/set", "remote"));

        // 4. Sync devices.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // 5. Assert that both devices have the merged data.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/v2/set");
                    assertThat(set.entries()).containsExactly("local", "remote");
                };
        transactDataStore(verifier);
        transactDataStore(mRemoteDevice1, verifier);
    }

    @Test
    public void testEmptySchemaVersionBump_concurrentUpdate_merged() throws Exception {
        // 1. Define schemas.
        // Schema v2 is an "empty" bump, just incrementing the version number.
        DocumentSchemaInfo schemaV2 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/register", SchemaProvider.TYPE_REGISTER, 1))
                        .putPathSchema(new PathSchemaInfo("/root/set", SchemaProvider.TYPE_SET, 1))
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/unmerged", SchemaProvider.TYPE_UNMERGED, 1))
                        .build();
        // Schema v3 adds a new path.
        DocumentSchemaInfo schemaV3 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(3)
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/register", SchemaProvider.TYPE_REGISTER, 1))
                        .putPathSchema(new PathSchemaInfo("/root/set", SchemaProvider.TYPE_SET, 1))
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/unmerged", SchemaProvider.TYPE_UNMERGED, 1))
                        .putPathSchema(
                                new PathSchemaInfo("/root/v3/set", SchemaProvider.TYPE_SET, 3))
                        .build();

        // 2. Setup Device A (local device), starting from the default v1 schema.
        upgradeSchema(schemaV2).get();
        upgradeSchema(schemaV3).get();
        mTimestampProvider.setTimeStamp(200);
        transactDataStore(
                doc -> {
                    doc.addDataToSet("/root/set", "local_v1");
                    doc.addDataToSet("/root/v3/set", "local_v3");
                });

        // 3. Setup Device B (remote device) to initialize with schema v3 directly.
        reInitWithNewSchema(mRemoteDevice1, schemaV3).get();
        mRemoteDevice1.getTimestampProvider().setTimeStamp(300);
        transactDataStore(
                mRemoteDevice1,
                doc -> {
                    doc.addDataToSet("/root/set", "remote_v1");
                    doc.addDataToSet("/root/v3/set", "remote_v3");
                });

        // 4. Sync devices.
        connectNetworks(this, mRemoteDevice1);
        flushNetworks(this, mRemoteDevice1);

        // 5. Assert that both devices have the merged data.
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc -> {
                    SetRecord<String> setV1 = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(setV1.entries()).containsExactly("local_v1", "remote_v1");
                    SetRecord<String> setV3 = (SetRecord<String>) doc.getRecord("/root/v3/set");
                    assertThat(setV3.entries()).containsExactly("local_v3", "remote_v3");
                };
        transactDataStore(verifier);
        transactDataStore(mRemoteDevice1, verifier);
    }

    @Test
    public void testIncompatibleSchema_concurrentUpdate_notSynced() throws Exception {
        // 1. Define schemas.
        DocumentSchemaInfo schemaAB =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema(new PathSchemaInfo("/root/set", SchemaProvider.TYPE_SET, 1))
                        .build();
        DocumentSchemaInfo schemaCD =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema(
                                new PathSchemaInfo("/root/set", SchemaProvider.TYPE_REGISTER, 1))
                        .build();

        // 2. Setup devices.
        SubmergeSharedDataStoreTest a = this;
        reInitWithNewSchema(a, schemaAB).get();
        RemoteDeviceContext b = mRemoteDevice1;
        reInitWithNewSchema(b, schemaAB).get();
        RemoteDeviceContext c = mRemoteDevice2;
        reInitWithNewSchema(c, schemaCD).get();
        RemoteDeviceContext d = mRemoteDevice3;
        reInitWithNewSchema(d, schemaCD).get();
        connectNetworks(a, b, c, d);

        // 3. Perform multiple rounds of concurrent updates and syncs.
        for (int i = 0; i < 3; i++) {
            final int round = i;
            transactDataStore(a, doc -> doc.addDataToSet("/root/set", "A" + round));
            transactDataStore(b, doc -> doc.addDataToSet("/root/set", "B" + round));
            transactDataStore(c, doc -> doc.putData("/root/set", "C" + round));
            transactDataStore(d, doc -> doc.putData("/root/set", "D" + round));
            flushNetworks(a, b, c, d);
        }

        // 4. Verification.
        ThrowingConsumer<MutableDocument<String>> verifierAB =
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/set");
                    assertThat(set.entries()).containsExactly("A0", "B0", "A1", "B1", "A2", "B2");
                };
        transactDataStore(a, verifierAB);
        transactDataStore(b, verifierAB);

        ThrowingConsumer<MutableDocument<String>> verifierCD =
                doc -> {
                    Record<String> register = doc.getRecord("/root/set");
                    assertThat(register.get()).isAnyOf("C2", "D2");
                };
        transactDataStore(c, verifierCD);
        transactDataStore(d, verifierCD);
    }

    @Test
    public void testSequentialIncompatibleSchemaUpgrade_formsPartitions() throws Exception {
        // 1. Define schemas.
        DocumentSchemaInfo schemaV1 =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema(
                                new PathSchemaInfo("/root/common", SchemaProvider.TYPE_SET, 1))
                        .build();
        DocumentSchemaInfo schemaV2_AB =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo("/root/common", SchemaProvider.TYPE_SET, 1))
                        .putPathSchema(
                                new PathSchemaInfo("/root/v2/data", SchemaProvider.TYPE_SET, 2))
                        .build();
        DocumentSchemaInfo schemaV2_CD =
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(2)
                        .putPathSchema(
                                new PathSchemaInfo("/root/common", SchemaProvider.TYPE_SET, 1))
                        .putPathSchema(
                                new PathSchemaInfo(
                                        "/root/v2/data", SchemaProvider.TYPE_REGISTER, 2))
                        .build();

        // 2. Setup devices.
        SubmergeSharedDataStoreTest a = this;
        reInitWithNewSchema(a, schemaV1).get();
        RemoteDeviceContext b = mRemoteDevice1;
        reInitWithNewSchema(b, schemaV1).get();
        RemoteDeviceContext c = mRemoteDevice2;
        reInitWithNewSchema(c, schemaV1).get();
        RemoteDeviceContext d = mRemoteDevice3;
        reInitWithNewSchema(d, schemaV1).get();
        connectNetworks(a, b, c, d);

        // 3. Initial sync on compatible schema.
        transactDataStore(a, doc -> doc.addDataToSet("/root/common", "A"));
        transactDataStore(b, doc -> doc.addDataToSet("/root/common", "B"));
        transactDataStore(c, doc -> doc.addDataToSet("/root/common", "C"));
        transactDataStore(d, doc -> doc.addDataToSet("/root/common", "D"));
        flushNetworks(a, b, c, d);
        ThrowingConsumer<MutableDocument<String>> v1Verifier =
                doc -> {
                    SetRecord<String> set = (SetRecord<String>) doc.getRecord("/root/common");
                    assertThat(set.entries()).containsExactly("A", "B", "C", "D");
                };
        transactDataStore(a, v1Verifier);
        transactDataStore(b, v1Verifier);
        transactDataStore(c, v1Verifier);
        transactDataStore(d, v1Verifier);

        // 4. Sequential, interleaved upgrade.
        upgradeSchema(a, schemaV2_AB).get();
        transactDataStore(a, doc -> doc.addDataToSet("/root/v2/data", "A_v2"));
        flushNetworks(a, b, c, d);
        ThrowingConsumer<MutableDocument<String>> verifier =
                doc ->
                        assertThat(((SetRecord<String>) doc.getRecord("/root/v2/data")).entries())
                                .containsExactly("A_v2");
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        transactDataStore(c, verifier);
        transactDataStore(d, verifier);

        upgradeSchema(b, schemaV2_AB).get();
        transactDataStore(b, doc -> doc.addDataToSet("/root/v2/data", "B_v2"));
        flushNetworks(a, b, c, d);
        verifier =
                doc ->
                        assertThat(((SetRecord<String>) doc.getRecord("/root/v2/data")).entries())
                                .containsExactly("A_v2", "B_v2");
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        transactDataStore(c, verifier);
        transactDataStore(d, verifier);

        // C upgrade will fail due to incompatible schema.
        ListenableFuture<Boolean> future = upgradeSchema(c, schemaV2_CD);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        // Reinit C from fresh state.
        reInitWithNewSchema(c, schemaV2_CD).get();
        connectNetworks(a, b, c, d);
        transactDataStore(c, doc -> doc.putData("/root/v2/data", "C_v2"));
        flushNetworks(a, b, c, d);
        // C now stays out of sync.
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        transactDataStore(d, verifier);
        transactDataStore(
                c,
                doc -> {
                    assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                            .isEmpty();
                    assertThat(doc.getRecord("/root/v2/data")).isNotInstanceOf(SetRecord.class);
                    assertThat(doc.getRecord("/root/v2/data").get()).isEqualTo("C_v2");
                });

        // D's upgrade will fail due to incompatible schema.
        future = upgradeSchema(d, schemaV2_CD);
        assertThat(future.exceptionNow()).isInstanceOf(SchemaValidationException.class);
        // Reinit D from fresh state.
        reInitWithNewSchema(d, schemaV2_CD).get();
        connectNetworks(a, b, c, d);
        transactDataStore(d, doc -> doc.putData("/root/v2/data", "D_v2"));
        flushNetworks(a, b, c, d);
        // D now stays out of sync from A and B but is in sync with C.
        transactDataStore(a, verifier);
        transactDataStore(b, verifier);
        ThrowingConsumer<MutableDocument<String>> verifierCD =
                doc -> {
                    assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                            .isEmpty();
                    assertThat(doc.getRecord("/root/v2/data")).isNotInstanceOf(SetRecord.class);
                    assertThat(doc.getRecord("/root/v2/data").get()).isEqualTo("D_v2");
                };
        transactDataStore(c, verifierCD);
        transactDataStore(d, verifierCD);

        // From this point, A/B/C/D forms partition.
        transactDataStore(a, doc -> doc.addDataToSet("/root/common", "A_v2"));
        transactDataStore(b, doc -> doc.addDataToSet("/root/common", "B_v2"));
        transactDataStore(c, doc -> doc.addDataToSet("/root/common", "C_v2"));
        transactDataStore(d, doc -> doc.addDataToSet("/root/common", "D_v2"));
        flushNetworks(a, b, c, d);
        ThrowingConsumer<MutableDocument<String>> verifierAB =
                doc -> {
                    assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                            .containsExactly("A", "B", "C", "D", "A_v2", "B_v2");
                };
        verifierCD =
                doc -> {
                    assertThat(((SetRecord<String>) doc.getRecord("/root/common")).entries())
                            .containsExactly("C_v2", "D_v2");
                };
        transactDataStore(a, verifierAB);
        transactDataStore(b, verifierAB);
        transactDataStore(c, verifierCD);
        transactDataStore(d, verifierCD);
    }

    @Test
    public void testClose() throws Exception {
        transactDataStore(doc -> doc.putData("/root/register", "abc"));

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

    private ListenableFuture<Boolean> reInitWithNewSchema(DocumentSchemaInfo... schema)
            throws Exception {
        return reInitWithNewSchema(this, schema);
    }

    private ListenableFuture<Boolean> reInitWithNewSchema(
            SharedDataStoreComponentTestBase testContext, DocumentSchemaInfo... schema)
            throws Exception {
        testContext.mSubmergeSharedDataStore.delete().get();
        return upgradeSchema(testContext, schema);
    }

    private void transactDataStore(
            SharedDataStoreComponentTestBase testContext,
            ThrowingConsumer<MutableDocument<String>> consumer)
            throws Exception {
        ((SubmergeSharedDataStore<String>) testContext.mSubmergeSharedDataStore)
                .transact(
                        DOC_ID,
                        doc -> {
                            consumer.acceptOrThrow(doc);
                            return true;
                        })
                .get();
    }

    private void transactDataStore(ThrowingConsumer<MutableDocument<String>> consumer)
            throws Exception {
        transactDataStore(this, consumer);
    }

    private class RemoteDeviceContext extends SharedDataStoreComponentTestBase {
        RemoteDeviceContext() {
            super.setUp();
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

        public FakeTimestampProvider getTimestampProvider() {
            return mTimestampProvider;
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
