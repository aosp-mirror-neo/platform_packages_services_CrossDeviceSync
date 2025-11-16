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

import com.android.crossdevicesync.ApplicationComponentTestBase;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
public class DataStoreInitHelperTest extends ApplicationComponentTestBase {

    private TestSharedDataStoreSupplier mSupplier;

    @Before
    public void setUp() {
        super.setUp();
        mSupplier = new TestSharedDataStoreSupplier();
    }

    @Test
    public void getInitializedDataStore_success() throws Exception {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        TestDataStore dataStore = mSupplier.firstDataStore();
        dataStore.mInitFuture.set(null);

        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(future.get()).isEqualTo(dataStore);
        assertThat(dataStore.isOpen()).isTrue();
    }

    @Test
    public void getInitializedDataStore_recoverFromSchemaError() throws Exception {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        TestDataStore dataStore1 = mSupplier.firstDataStore();
        dataStore1.mInitFuture.setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        dataStore1.mDeleteFuture.set(null);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);

        TestDataStore dataStore2 = mSupplier.secondDataStore();
        dataStore2.mInitFuture.set(null);

        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(future.get()).isEqualTo(dataStore2);
        assertThat(dataStore2.isOpen()).isTrue();
    }

    @Test
    public void getInitializedDataStore_nonSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        TestDataStore dataStore = mSupplier.firstDataStore();
        dataStore.mInitFuture.setException(new IllegalArgumentException());

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(IllegalArgumentException.class);
        assertThat(dataStore.isOpen()).isFalse();
    }

    @Test
    public void getInitializedDataStore_unableToDelete_fail() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        TestDataStore dataStore = mSupplier.firstDataStore();
        dataStore.mInitFuture.setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        dataStore.mDeleteFuture.setException(new NullPointerException());

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(NullPointerException.class);
        assertThat(dataStore.isOpen()).isFalse();
    }

    @Test
    public void getInitializedDataStore_secondInitFailWithSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        TestDataStore dataStore1 = mSupplier.firstDataStore();
        dataStore1.mInitFuture.setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        dataStore1.mDeleteFuture.set(null);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);

        TestDataStore dataStore2 = mSupplier.secondDataStore();
        dataStore2.mInitFuture.setException(new SchemaValidationException("error"));

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(SchemaValidationException.class);
        assertThat(dataStore2.isOpen()).isFalse();
    }

    @Test
    public void getInitializedDataStore_secondInitFailWithNonSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        TestDataStore dataStore1 = mSupplier.firstDataStore();
        dataStore1.mInitFuture.setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        dataStore1.mDeleteFuture.set(null);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);

        TestDataStore dataStore2 = mSupplier.secondDataStore();
        dataStore2.mInitFuture.setException(new NullPointerException());

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(NullPointerException.class);
        assertThat(dataStore2.isOpen()).isFalse();
    }

    private static class TestSharedDataStoreSupplier implements Supplier<SharedDataStore<String>> {
        final List<TestDataStore> mSuppliedDataStores = new ArrayList<>();

        @Override
        public TestDataStore get() {
            TestDataStore dataStore = new TestDataStore();
            mSuppliedDataStores.add(dataStore);
            return dataStore;
        }

        public TestDataStore firstDataStore() {
            return mSuppliedDataStores.get(0);
        }

        public TestDataStore secondDataStore() {
            return mSuppliedDataStores.get(1);
        }
    }

    private static class TestDataStore implements SharedDataStore<String> {
        final SettableFuture<?> mInitFuture = SettableFuture.create();
        final SettableFuture<?> mDeleteFuture = SettableFuture.create();
        private boolean mClosed;

        @Override
        public ListenableFuture<?> init() {
            return mInitFuture;
        }

        @Override
        public String getLocalDeviceNodeId() {
            return "";
        }

        @Override
        public <R> ListenableFuture<R> transact(
                String docId, TransactionApplier<String, R> applier) {
            return null;
        }

        @Override
        public void registerOnRemoteChangeListener(
                Executor executor, OnRemoteChangeListener listener) {}

        @Override
        public void unregisterOnRemoteChangeListener(OnRemoteChangeListener listener) {}

        @Override
        public void close() {
            mClosed = true;
        }

        @Override
        public boolean isOpen() {
            return !mClosed;
        }

        @Override
        public ListenableFuture<?> delete() {
            return mDeleteFuture;
        }
    }
}
