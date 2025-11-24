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

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.room.Room;

import com.android.crossdevicesync.data.dagger.SharedDataStoreSingleton;
import com.android.crossdevicesync.data.dagger.qualifier.DataStoreName;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;

import com.google.android.submerge.StorageInterface.StorageException;

import javax.inject.Inject;

/** Implementation of {@link IStorage} using room DB. */
@SharedDataStoreSingleton
public class RoomStorage implements IStorage {
    private static final String TAG = "RoomStorage";

    private final Context mContext;
    private final String mName;

    @Nullable private DocumentDB mDatabase;
    @Nullable private DocumentDao mDao;

    @Inject
    RoomStorage(Context context, @DataStoreName String name) {
        mContext = context.createDeviceProtectedStorageContext();
        mName = name;
    }

    @Override
    public void init() {
        if (mDatabase != null) {
            Log.w(TAG, "init: already initialized!");
            return;
        }
        mDatabase = Room.databaseBuilder(mContext, DocumentDB.class, mName).build();
        mDao = mDatabase.documentDao();
    }

    @Override
    @Nullable
    public byte[] getDocument(String docId) throws StorageException {
        try {
            Document doc = mDao.get(docId);
            if (doc != null) {
                return doc.content;
            }
        } catch (Exception e) {
            Log.e(TAG, "getDocument: failed!", e);
            throw new StorageException();
        }
        return null;
    }

    @Override
    public void persistDocument(String docId, byte[] serializedDoc) throws StorageException {
        try {
            mDao.createOrUpdateContent(docId, serializedDoc);
        } catch (Exception e) {
            Log.e(TAG, "persistDocument: failed!", e);
            throw new StorageException();
        }
    }

    @Override
    public byte[] getDocumentSchema(String docId) throws StorageException {
        try {
            Document doc = mDao.get(docId);
            if (doc != null) {
                return doc.schema;
            }
        } catch (Exception e) {
            Log.e(TAG, "getDocumentSchema: failed!", e);
            throw new StorageException();
        }
        return null;
    }

    @Override
    public void persistDocumentSchema(String docId, byte[] documentSchema) throws StorageException {
        try {
            mDao.createOrUpdateSchema(docId, documentSchema);
        } catch (Exception e) {
            Log.e(TAG, "persistDocumentSchema: failed!", e);
            throw new StorageException();
        }
    }

    @Override
    @Nullable
    public byte[] getMetadata(String docId) throws StorageException {
        try {
            Document doc = mDao.get(docId);
            if (doc != null) {
                return doc.metadata;
            }
        } catch (Exception e) {
            Log.e(TAG, "getMetadata: failed!", e);
            throw new StorageException();
        }
        return null;
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        try {
            mDao.createOrUpdateMetadata(docId, metadata);
        } catch (Exception e) {
            Log.e(TAG, "persistMetadata: failed!", e);
            throw new StorageException();
        }
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception {
        try {
            mDao.transact(() -> transactionApplier.acceptOrThrow(this));
        } catch (Exception e) {
            throw new StorageException();
        }
    }

    @Override
    public void close() {
        if (mDatabase == null) {
            Log.e(TAG, "close: database is null!");
            return;
        }
        mDatabase.close();
        mDatabase = null;
        mDao = null;
    }

    @Override
    public boolean deleteDatabase() {
        close();
        if (mContext.deleteDatabase(mName)) {
            Log.i(TAG, "deleteDatabase: database is deleted!");
            return true;
        }
        return false;
    }
}
