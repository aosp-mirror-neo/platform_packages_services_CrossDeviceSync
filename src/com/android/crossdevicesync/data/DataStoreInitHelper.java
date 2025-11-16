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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** A helper class for initializing the {@link SharedDataStore} safely. */
public final class DataStoreInitHelper {
    private static final String TAG = "DataStoreInitHelper";

    private DataStoreInitHelper() {}

    /**
     * Get and initialize a data store. If a schema error occurs, delete and retry initializing the
     * data store.
     *
     * @param dataStoreSupplier a function to create a new data store instance.
     * @param executor an {@link Executor} where the {@code dataStoreSupplier} is executed.
     * @return a future containing the initialized data store, or an exception.
     */
    public static <T> ListenableFuture<SharedDataStore<T>> getInitializedDataStore(
            Supplier<SharedDataStore<T>> dataStoreSupplier, Executor executor) {
        return Futures.transformAsync(
                createSharedDataStoreAsync(dataStoreSupplier, executor),
                dataStore ->
                        initializeDataStore(
                                dataStore, dataStoreSupplier, executor, /* shouldRetry= */ true),
                directExecutor());
    }

    private static <T> ListenableFuture<SharedDataStore<T>> initializeDataStore(
            SharedDataStore<T> dataStoreToInit,
            Supplier<SharedDataStore<T>> dataStoreSupplier,
            Executor executor,
            boolean shouldRetry) {
        return Futures.catchingAsync(
                doInitDataStore(dataStoreToInit),
                Exception.class,
                e -> {
                    if (shouldRetry
                            && (e instanceof IllegalSchemaChangeException
                                    || e instanceof SchemaValidationException)) {
                        Log.w(
                                TAG,
                                "Schema error occurred, deleting the data store " + dataStoreToInit,
                                e);
                        return deleteAndReInitDataStore(
                                dataStoreToInit, dataStoreSupplier, executor);
                    } else {
                        dataStoreToInit.close();
                        return Futures.immediateFailedFuture(e);
                    }
                },
                directExecutor());
    }

    private static <T> ListenableFuture<SharedDataStore<T>> doInitDataStore(
            SharedDataStore<T> dataStore) {
        return Futures.transform(dataStore.init(), unused -> dataStore, directExecutor());
    }

    private static <T> ListenableFuture<SharedDataStore<T>> deleteAndReInitDataStore(
            SharedDataStore<T> dataStoreToDelete,
            Supplier<SharedDataStore<T>> dataStoreSupplier,
            Executor executor) {
        ListenableFuture<?> deleteFuture = dataStoreToDelete.delete();
        Futures.addCallback(
                deleteFuture,
                new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(Object result) {
                        Log.i(TAG, "Successfully deleted data store " + dataStoreToDelete);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Unable to delete data store.", t);
                        dataStoreToDelete.close();
                    }
                },
                directExecutor());
        return Futures.transformAsync(
                deleteFuture,
                unused ->
                        initializeDataStore(
                                dataStoreSupplier.get(),
                                dataStoreSupplier,
                                executor,
                                /* shouldRetry= */ false),
                executor);
    }

    private static <T> ListenableFuture<SharedDataStore<T>> createSharedDataStoreAsync(
            Supplier<SharedDataStore<T>> dataStoreSupplier, Executor executor) {
        if (executor instanceof ListeningExecutorService executorService) {
            return executorService.submit(dataStoreSupplier::get);
        }
        ListenableFutureTask<SharedDataStore<T>> task =
                ListenableFutureTask.create(dataStoreSupplier::get);
        executor.execute(task);
        return task;
    }
}
