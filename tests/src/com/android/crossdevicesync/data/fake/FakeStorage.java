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
package com.android.crossdevicesync.data.fake;

import android.annotation.Nullable;

import com.android.crossdevicesync.data.dagger.SharedDataStoreSingleton;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;

import com.google.android.submerge.StorageInterface;
import com.google.android.submerge.StorageInterface.StorageException;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/** Fake implementation of {@link StorageInterface}. */
@SharedDataStoreSingleton
public class FakeStorage implements IStorage {

    private final Map<String, byte[]> mDocuments = new HashMap<>();
    private final Map<String, byte[]> mMetadata = new HashMap<>();
    private final Map<String, byte[]> mSchema = new HashMap<>();
    private boolean mInitialized;

    @Inject
    FakeStorage() {}

    @Override
    public void init() {
        mInitialized = true;
    }

    @Nullable
    @Override
    public byte[] getDocument(String docId) throws StorageException {
        if (!mInitialized) {
            throw new StorageException();
        }
        return mDocuments.get(docId);
    }

    @Override
    public void persistDocument(String docId, byte[] document) throws StorageException {
        if (!mInitialized) {
            throw new StorageException();
        }
        mDocuments.put(docId, document);
    }

    @Override
    public byte[] getDocumentSchema(String docId) throws StorageException {
        if (!mInitialized) {
            throw new StorageException();
        }
        return mSchema.get(docId);
    }

    @Override
    public void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException {
        if (!mInitialized) {
            throw new StorageException();
        }
        mSchema.put(docId, documentSchema);
    }

    @Override
    @Nullable
    public byte[] getMetadata(String docId) throws StorageException {
        if (!mInitialized) {
            throw new StorageException();
        }
        return mMetadata.get(docId);
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        if (!mInitialized) {
            throw new StorageException();
        }
        mMetadata.put(docId, metadata);
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception {
        if (!mInitialized) {
            throw new StorageException();
        }
        transactionApplier.acceptOrThrow(this);
    }

    @Override
    public void close() {
        mInitialized = false;
    }

    @Override
    public boolean deleteDatabase() {
        close();
        mDocuments.clear();
        mMetadata.clear();
        mSchema.clear();
        return true;
    }

    public boolean isOpen() {
        return mInitialized;
    }

    /** Copy all content from another storage. */
    public void copyAll(FakeStorage other) {
        mDocuments.putAll(other.mDocuments);
        mMetadata.putAll(other.mMetadata);
        mSchema.putAll(other.mSchema);
    }
}
