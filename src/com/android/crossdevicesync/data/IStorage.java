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

import android.annotation.Nullable;

import com.android.internal.util.FunctionalUtils.ThrowingConsumer;

import com.google.android.submerge.StorageInterface;

/** Interface representing a persistent storage. */
public interface IStorage extends StorageInterface, AutoCloseable {

    /** Init the storage. */
    void init();

    /** Get the serialized metadata of a document. */
    @Nullable
    byte[] getMetadata(String docId) throws StorageException;

    /** Save the serialized document metadata into the persistent storage. */
    void persistMetadata(String docId, byte[] metadata) throws StorageException;

    /**
     * Open a database transaction and apply the given transaction applier within the transaction.
     */
    void transact(ThrowingConsumer<IStorage> transactionApplier) throws StorageException;

    /** Delete the database. */
    void deleteDatabase();

    /** Close the database. */
    void close();
}