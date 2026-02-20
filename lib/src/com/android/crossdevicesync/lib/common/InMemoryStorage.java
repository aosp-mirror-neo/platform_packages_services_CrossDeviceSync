/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.crossdevicesync.lib.common;

import android.annotation.Nullable;

import com.android.crossdevicesync.data.storage.IStorage;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;

import com.google.android.submerge.StorageInterface.StorageException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** In-memory implementation of {@link IStorage} for testing. */
public class InMemoryStorage implements IStorage {

    private final Map<String, byte[]> mDocuments = new HashMap<>();
    private final Map<String, byte[]> mMetadata = new HashMap<>();
    private final Map<String, byte[]> mSchema = new HashMap<>();
    private final ListeningExecutorService mExecutorService =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    private boolean mOpen;

    @Override
    public void open() {
        mOpen = true;
    }

    @Nullable
    @Override
    public byte[] getDocument(String docId) throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        return mDocuments.get(docId);
    }

    @Override
    public void persistDocument(String docId, byte[] document) throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        mDocuments.put(docId, document);
    }

    @Override
    public byte[] getDocumentSchema(String docId) throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        return mSchema.get(docId);
    }

    @Override
    public void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        mSchema.put(docId, documentSchema);
    }

    @Override
    @Nullable
    public byte[] getMetadata(String docId) throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        return mMetadata.get(docId);
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        mMetadata.put(docId, metadata);
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception {
        if (!mOpen) {
            throw new StorageException();
        }
        transactionApplier.acceptOrThrow(this);
    }

    @Override
    public List<String> getAllDocumentIds() throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        Set<String> ids = new HashSet<>();
        ids.addAll(mDocuments.keySet());
        ids.addAll(mMetadata.keySet());
        ids.addAll(mSchema.keySet());
        return ids.stream().toList();
    }

    @Override
    public void deleteDocuments(List<String> docIds) throws StorageException {
        if (!mOpen) {
            throw new StorageException();
        }
        for (String docId : docIds) {
            mDocuments.remove(docId);
            mMetadata.remove(docId);
            mSchema.remove(docId);
        }
    }

    @Override
    public void close() {
        mOpen = false;
    }

    @Override
    public <R> ListenableFuture<R> submitToIoThread(Callable<R> task) {
        if (mExecutorService.isShutdown()) {
            return Futures.immediateFailedFuture(new IllegalStateException("Shutdown"));
        }
        return mExecutorService.submit(task);
    }

    @Override
    public void shutdownIoThread() {
        mExecutorService.shutdown();
    }

    @Override
    public boolean deleteDatabase() {
        close();
        boolean hasData = !mDocuments.isEmpty() || !mMetadata.isEmpty() || !mSchema.isEmpty();
        mDocuments.clear();
        mMetadata.clear();
        mSchema.clear();
        return hasData;
    }
}
