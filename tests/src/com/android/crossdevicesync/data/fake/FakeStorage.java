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
    // Generic data for test usage.
    private final Map<String, Object> mObjects = new HashMap<>();
    private boolean mInitialized;
    private RuntimeException mException;

    @Inject
    FakeStorage() {}

    @Override
    public void init() {
        maybeThrowException();
        mInitialized = true;
    }

    @Nullable
    @Override
    public byte[] getDocument(String docId) throws StorageException {
        maybeThrowException();
        if (!mInitialized) {
            throw new StorageException();
        }
        return mDocuments.get(docId);
    }

    @Override
    public void persistDocument(String docId, byte[] document) throws StorageException {
        maybeThrowException();
        if (!mInitialized) {
            throw new StorageException();
        }
        mDocuments.put(docId, document);
    }

    @Override
    public byte[] getDocumentSchema(String docId) throws StorageException {
        maybeThrowException();
        if (!mInitialized) {
            throw new StorageException();
        }
        return mSchema.get(docId);
    }

    @Override
    public void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException {
        maybeThrowException();
        if (!mInitialized) {
            throw new StorageException();
        }
        mSchema.put(docId, documentSchema);
    }

    @Override
    @Nullable
    public byte[] getMetadata(String docId) throws StorageException {
        maybeThrowException();
        if (!mInitialized) {
            throw new StorageException();
        }
        return mMetadata.get(docId);
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        maybeThrowException();
        if (!mInitialized) {
            throw new StorageException();
        }
        mMetadata.put(docId, metadata);
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception {
        maybeThrowException();
        if (!mInitialized) {
            throw new StorageException();
        }
        transactionApplier.acceptOrThrow(this);
    }

    /** Persist a generic object. */
    public <T> void persistObject(String key, T val) {
        maybeThrowException();
        mObjects.put(key, val);
    }

    /** Get a generic object. */
    @SuppressWarnings("TypeParameterUnusedInFormals")
    @Nullable
    public <T> T getObject(String val) {
        maybeThrowException();
        return getObjectUnchecked(val);
    }

    /** Get all persistent generic objects without any precondition check. */
    @Nullable
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public <T> T getObjectUnchecked(String val) {
        return (T) mObjects.get(val);
    }

    public Map<String, Object> getAllObjectsUnchecked() {
        return mObjects;
    }

    @Override
    public void close() {
        maybeThrowException();
        mInitialized = false;
    }

    @Override
    public boolean deleteDatabase() {
        maybeThrowException();
        close();
        boolean hasData =
                !mDocuments.isEmpty()
                        || !mMetadata.isEmpty()
                        || !mSchema.isEmpty()
                        || !mObjects.isEmpty();
        mDocuments.clear();
        mMetadata.clear();
        mSchema.clear();
        mObjects.clear();
        return hasData;
    }

    public void setRuntimeException(@Nullable RuntimeException e) {
        mException = e;
    }

    private void maybeThrowException() {
        if (mException != null) {
            RuntimeException e = mException;
            mException = null;
            throw e;
        }
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
