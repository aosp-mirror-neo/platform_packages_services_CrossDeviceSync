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
package com.android.crossdevicesync.data.storage;

import android.annotation.Nullable;

import com.android.internal.util.FunctionalUtils.ThrowingConsumer;

import com.google.android.submerge.StorageInterface.StorageException;

/** Interface representing a persistent storage. */
public interface IStorage extends AutoCloseable {

    /** Init the storage. */
    void init();

    /** Get the serialized document. */
    byte[] getDocument(String docId) throws StorageException;

    /** Save the serialized document into the persistent storage. */
    void persistDocument(String docId, byte[] document) throws StorageException;

    /**
     * Get the serialized document schema. This is a submerge document that can be merged via
     * network update to mutate schema.
     */
    byte[] getDocumentSchema(String docId) throws StorageException;

    /** Save the serialized document schema into the persistent storage. */
    void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException;

    /** Get the serialized metadata of a document. */
    @Nullable
    byte[] getMetadata(String docId) throws StorageException;

    /** Save the serialized document metadata into the persistent storage. */
    void persistMetadata(String docId, byte[] metadata) throws StorageException;

    /**
     * Open a database transaction and apply the given transaction applier within the transaction.
     */
    void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception;

    /** Delete the database. */
    boolean deleteDatabase();

    /** Close the database. */
    void close();
}
