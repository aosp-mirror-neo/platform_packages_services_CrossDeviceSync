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

import static com.android.crossdevicesync.common.Utils.getException;
import static com.android.crossdevicesync.common.Utils.isFutureFailed;
import static com.android.crossdevicesync.common.Utils.isFutureSucceeded;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.crossdevicesync.data.fake.FakeSharedDataStore;
import com.android.crossdevicesync.data.fake.TestSharedDataStoreFactory;
import com.android.crossdevicesync.network.NetworkManager.Network;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DefaultSharedDataStoreHandleTest extends SharedDataStoreComponentTestBase {
    private DefaultSharedDataStoreHandle<String> mHandle;
    private TestSharedDataStoreFactory<String> mDataStoreFactory;
    private List<DocumentSchemaInfo> mSchema;
    private SharedDataStore<String> mDataStore;
    private Network mNetwork;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        super.setUp();
        mFakeStorage.init();
        mHandle = (DefaultSharedDataStoreHandle<String>) mSharedDataStoreHandle;
        mDataStoreFactory =
                (TestSharedDataStoreFactory<String>)
                        mTestSharedDataStoreFactory.setDataStoresAsync(true);
        mDataStore = (FakeSharedDataStore<String>) mFakeSharedDataStore;
        mSchema = new ArrayList<>();
        mNetwork = getNetworkManager().createNetwork("test_network", device -> true);
    }

    @Override
    protected SchemaProvider<String> getSchemaProvider() {
        return new SchemaProvider<>() {
            @Override
            public List<DocumentSchemaInfo> getAllDocumentSchema() {
                return mSchema;
            }

            @Override
            public void migrateDocument(MutableDocument<String> document) {}
        };
    }

    @Override
    protected boolean useRealSharedDataStoreHandleImpl() {
        // Need to force the FakeSharedDataStore to use the real handle impl so that we can test it.
        return true;
    }

    @Test
    public void testInitiallyUnlocked() {
        assertThat(mHandle.isLocked()).isFalse();
        assertThat(mHandle.getLockedDataStore()).isNull();
    }

    @Test
    public void testLock_success() {
        assertThat(mHandle.lock(mDataStore)).isTrue();
        assertThat(mHandle.isLocked()).isTrue();
        assertThat(mHandle.getLockedDataStore()).isSameInstanceAs(mDataStore);
    }

    @Test
    public void testLock_failed() {
        mHandle.lock(mDataStore);

        // Lock again should fail.
        assertThat(mHandle.lock(mDataStore)).isFalse();
    }

    @Test
    public void testUnlock_success() {
        mHandle.lock(mDataStore);

        mHandle.unlock(mDataStore);

        assertThat(mHandle.isLocked()).isFalse();
        assertThat(mHandle.getLockedDataStore()).isNull();
    }

    @Test
    public void testUnlock_failed() {
        mHandle.lock(mDataStore);

        mHandle.unlock(mDataStoreFactory.create());

        assertThat(mHandle.isLocked()).isTrue();
        assertThat(mHandle.getLockedDataStore()).isSameInstanceAs(mDataStore);
    }

    @Test
    public void testDelete_locked_throwsException() {
        mHandle.lock(mDataStore);

        assertThat(getException(mHandle.delete())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testDelete_success() throws Exception {
        mFakeStorage.persistObject(DOC_ID, new Object());

        assertThat(mHandle.delete().get()).isTrue();
        assertThat(mFakeStorage.getAllObjectsUnchecked().containsKey(DOC_ID)).isFalse();
    }

    @Test
    public void testDelete_failed() throws Exception {
        assertThat(mHandle.delete().get()).isFalse();
    }

    @Test
    public void openDataStore_success() throws Exception {
        ListenableFuture<SharedDataStore<String>> future = mHandle.openDataStore(mNetwork);

        assertThat(future.isDone()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);

        FakeSharedDataStore<String> dataStore =
                (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        dataStore.getInitFuture().set(null);

        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(future.get()).isEqualTo(dataStore);
        assertThat(dataStore.isOpen()).isTrue();
        assertThat(mHandle.getLockedDataStore()).isSameInstanceAs(future.get());
    }

    @Test
    public void openDataStore_recoverFromSchemaError() throws Exception {
        ListenableFuture<SharedDataStore<String>> future = mHandle.openDataStore(mNetwork);

        assertThat(future.isDone()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);

        FakeSharedDataStore<String> dataStore1 =
                (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        failInit(dataStore1, new SchemaValidationException("error"));

        assertThat(dataStore1.isOpen()).isFalse();
        assertThat(future.isDone()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(2);

        FakeSharedDataStore<String> dataStore2 =
                (FakeSharedDataStore<String>) mDataStoreFactory.secondDataStore();
        dataStore2.getInitFuture().set(null);

        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(future.get()).isEqualTo(dataStore2);
        assertThat(dataStore2.isOpen()).isTrue();
        assertThat(mHandle.getLockedDataStore()).isSameInstanceAs(future.get());
    }

    @Test
    public void openDataStore_nonSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future = mHandle.openDataStore(mNetwork);

        assertThat(future.isDone()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);

        FakeSharedDataStore<String> dataStore =
                (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        failInit(dataStore, new IllegalArgumentException());

        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);
        assertThat(dataStore.isOpen()).isFalse();
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mHandle.getLockedDataStore()).isNull();
    }

    @Test
    public void openDataStore_secondInitFailWithSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future = mHandle.openDataStore(mNetwork);

        assertThat(future.isDone()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);

        FakeSharedDataStore<String> dataStore1 =
                (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        failInit(dataStore1, new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(dataStore1.isOpen()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(2);

        FakeSharedDataStore<String> dataStore2 =
                (FakeSharedDataStore<String>) mDataStoreFactory.secondDataStore();
        failInit(dataStore2, new SchemaValidationException("error"));

        assertThat(mDataStoreFactory.getDataStores()).hasSize(2);
        assertThat(dataStore2.isOpen()).isFalse();
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(SchemaValidationException.class);
        assertThat(mHandle.getLockedDataStore()).isNull();
    }

    @Test
    public void openDataStore_secondInitFailWithNonSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future = mHandle.openDataStore(mNetwork);

        assertThat(future.isDone()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);

        FakeSharedDataStore<String> dataStore1 =
                (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        failInit(dataStore1, new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(dataStore1.isOpen()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(2);

        FakeSharedDataStore<String> dataStore2 =
                (FakeSharedDataStore<String>) mDataStoreFactory.secondDataStore();
        failInit(dataStore2, new NullPointerException());

        assertThat(mDataStoreFactory.getDataStores()).hasSize(2);
        assertThat(dataStore2.isOpen()).isFalse();
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(NullPointerException.class);
        assertThat(mHandle.getLockedDataStore()).isNull();
    }

    @Test
    public void openDataStore_deletionInterruptedByDestroy_fail() {
        ListenableFuture<SharedDataStore<String>> future = mHandle.openDataStore(mNetwork);

        assertThat(future.isDone()).isFalse();
        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);
        FakeSharedDataStore<String> dataStore =
                (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();

        mHandle.destroy();
        failInit(dataStore, new SchemaValidationException("error"));

        assertThat(mDataStoreFactory.getDataStores()).hasSize(1);
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(IllegalStateException.class);
        assertThat(mHandle.getLockedDataStore()).isNull();
    }

    @Test
    public void destroy_noDataStore_shutDownIoThread() {
        mHandle.destroy();

        assertThat(mIOThreadExecutorService.isShutdown()).isTrue();
    }

    @Test
    public void destroy_noDataStore_deleteDataStoreAndShutDownIoThread() throws Exception {
        addSchema();
        mDataStoreFactory.setDataStoresAsync(false);
        // Open and close.
        mHandle.openDataStore(mNetwork).get().close().get();
        assertThat(mFakeStorage.getAllObjectsUnchecked().containsKey(DOC_ID)).isTrue();
        assertThat(mHandle.getLockedDataStore()).isNull();

        mHandle.destroy(/* deleteDataStore= */ true);

        assertThat(mFakeStorage.getAllObjectsUnchecked().containsKey(DOC_ID)).isFalse();
        assertThat(mIOThreadExecutorService.isShutdown()).isTrue();
    }

    @Test
    public void destroy_dataStoreClosed() throws Exception {
        addSchema();
        mDataStoreFactory.setDataStoresAsync(false);
        SharedDataStore<?> dataStore = mHandle.openDataStore(mNetwork).get();
        assertThat(dataStore.isOpen()).isTrue();
        assertThat(mFakeStorage.getAllObjectsUnchecked().containsKey(DOC_ID)).isTrue();
        assertThat(mHandle.getLockedDataStore()).isSameInstanceAs(dataStore);

        mHandle.destroy();

        assertThat(dataStore.isOpen()).isFalse();
        assertThat(mFakeStorage.getAllObjectsUnchecked().containsKey(DOC_ID)).isTrue();
        assertThat(mIOThreadExecutorService.isShutdown()).isTrue();
    }

    @Test
    public void destroy_deleteDataStoreAfterClosed() throws Exception {
        addSchema();
        mDataStoreFactory.setDataStoresAsync(false);
        SharedDataStore<String> dataStore = mHandle.openDataStore(mNetwork).get();
        assertThat(dataStore.isOpen()).isTrue();
        assertThat(mFakeStorage.getAllObjectsUnchecked().containsKey(DOC_ID)).isTrue();
        assertThat(mHandle.getLockedDataStore()).isSameInstanceAs(dataStore);

        mHandle.destroy(/* deleteDataStore= */ true);

        assertThat(dataStore.isOpen()).isFalse();
        assertThat(mFakeStorage.getAllObjectsUnchecked().containsKey(DOC_ID)).isFalse();
        assertThat(mIOThreadExecutorService.isShutdown()).isTrue();
    }

    private void addSchema() {
        mSchema.add(
                DocumentSchemaInfo.builder()
                        .setDocId(DOC_ID)
                        .setVersion(1)
                        .putPathSchema("/register", RecordType.TYPE_REGISTER, 1)
                        .build());
    }

    private void failInit(FakeSharedDataStore<String> dataStore, Exception e) {
        dataStore.getInitFuture().setException(e);
        dataStore.setAsync(false);
    }
}
