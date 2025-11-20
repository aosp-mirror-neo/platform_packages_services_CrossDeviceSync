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
import com.android.crossdevicesync.data.fake.FakeSharedDataStore;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
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

        FakeSharedDataStore<String> dataStore = mSupplier.firstDataStore();
        dataStore.getInitFuture().set(null);

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

        FakeSharedDataStore<String> dataStore1 = mSupplier.firstDataStore();
        dataStore1.getInitFuture().setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);
        assertThat(dataStore1.isOpen()).isFalse();

        dataStore1.getDeleteFuture().set(null);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);

        FakeSharedDataStore<String> dataStore2 = mSupplier.secondDataStore();
        dataStore2.getInitFuture().set(null);

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

        FakeSharedDataStore<String> dataStore = mSupplier.firstDataStore();
        dataStore.getInitFuture().setException(new IllegalArgumentException());

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);
        assertThat(dataStore.isOpen()).isFalse();

        dataStore.getCloseFuture().set(null);

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getInitializedDataStore_closeError_doesNotOverrideOriginalErrorType() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        FakeSharedDataStore<String> dataStore = mSupplier.firstDataStore();
        dataStore.getInitFuture().setException(new IllegalArgumentException());

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);
        assertThat(dataStore.isOpen()).isFalse();

        // Close throws a different error type.
        dataStore.getCloseFuture().setException(new NullPointerException());

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getInitializedDataStore_unableToDelete_fail() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        FakeSharedDataStore<String> dataStore = mSupplier.firstDataStore();
        dataStore.getInitFuture().setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);
        assertThat(dataStore.isOpen()).isFalse();

        dataStore.getDeleteFuture().setException(new NullPointerException());

        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void getInitializedDataStore_secondInitFailWithSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        FakeSharedDataStore<String> dataStore1 = mSupplier.firstDataStore();
        dataStore1.getInitFuture().setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);
        assertThat(dataStore1.isOpen()).isFalse();

        dataStore1.getDeleteFuture().set(null);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);

        FakeSharedDataStore<String> dataStore2 = mSupplier.secondDataStore();
        dataStore2.getInitFuture().setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);
        assertThat(dataStore2.isOpen()).isFalse();

        dataStore2.getCloseFuture().set(null);

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(SchemaValidationException.class);
    }

    @Test
    public void getInitializedDataStore_secondInitFailWithNonSchemaError_fail() {
        ListenableFuture<SharedDataStore<String>> future =
                DataStoreInitHelper.getInitializedDataStore(mSupplier, mMainExecutor);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);

        FakeSharedDataStore<String> dataStore1 = mSupplier.firstDataStore();
        dataStore1.getInitFuture().setException(new SchemaValidationException("error"));

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(1);
        assertThat(dataStore1.isOpen()).isFalse();

        dataStore1.getDeleteFuture().set(null);

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);

        FakeSharedDataStore<String> dataStore2 = mSupplier.secondDataStore();
        dataStore2.getInitFuture().setException(new NullPointerException());

        assertThat(future.isDone()).isFalse();
        assertThat(mSupplier.mSuppliedDataStores).hasSize(2);
        assertThat(dataStore2.isOpen()).isFalse();

        dataStore2.getCloseFuture().set(null);

        assertThat(isFutureFailed(future)).isTrue();
        assertThat(getException(future)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("EffectivelyPrivate")
    private static class TestSharedDataStoreSupplier implements Supplier<SharedDataStore<String>> {
        final List<FakeSharedDataStore<String>> mSuppliedDataStores = new ArrayList<>();

        @Override
        public FakeSharedDataStore<String> get() {
            FakeSharedDataStore<String> dataStore = new FakeSharedDataStore<>();
            dataStore.setAsync(true);
            mSuppliedDataStores.add(dataStore);
            return dataStore;
        }

        public FakeSharedDataStore<String> firstDataStore() {
            return mSuppliedDataStores.get(0);
        }

        public FakeSharedDataStore<String> secondDataStore() {
            return mSuppliedDataStores.get(1);
        }
    }
}
