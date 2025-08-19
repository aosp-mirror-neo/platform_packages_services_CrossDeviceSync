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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Transaction;
import androidx.room.Update;

import com.android.crossdevicesync.data.dagger.SharedDataStoreSingleton;
import com.android.crossdevicesync.data.dagger.qualifier.DataStoreName;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.internal.util.FunctionalUtils.ThrowingRunnable;

import java.util.List;

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
        mContext = context;
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
    public void onNewUpdate(String docId, byte[] serializedDoc) throws StorageException {
        try {
            mDao.createOrUpdateContent(docId, serializedDoc);
        } catch (Exception e) {
            Log.e(TAG, "onNewUpdate: failed!", e);
            throw new StorageException();
        }
    }

    @Override
    @Nullable
    public byte[] readFromStorage(String docId) throws StorageException {
        try {
            Document doc = mDao.get(docId);
            if (doc != null) {
                return doc.content;
            }
        } catch (Exception e) {
            Log.e(TAG, "readFromStorage: failed!", e);
            throw new StorageException();
        }
        return null;
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
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws StorageException {
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
    public void deleteDatabase() {
        if (mDatabase == null) {
            Log.e(TAG, "deleteDatabase: database is null!");
            return;
        }
        close();
        mContext.deleteDatabase(mName);
        Log.i(TAG, "deleteDatabase: database is deleted!");
    }

    /** Room DB schema of a document. */
    @Entity
    static class Document {
        @NonNull @PrimaryKey public String docId;
        public byte[] content;
        public byte[] metadata;
    }

    /** Room Dao for accessing {@link Document}. */
    @Dao
    abstract static class DocumentDao {
        @Query("SELECT * from document where docId=:docId LIMIT 1")
        abstract Document get(String docId);

        @Insert
        abstract void insert(Document document);

        @Update
        abstract void update(Document document);

        @Query(("SELECT docId from document"))
        abstract List<String> getAllDocIds();

        @Transaction
        void createOrUpdateContent(String docId, @Nullable byte[] content) {
            Document doc = get(docId);
            if (doc == null) {
                doc = new Document();
                doc.docId = docId;
                doc.content = content;
                insert(doc);
            } else {
                doc.content = content;
                update(doc);
            }
        }

        @Transaction
        void createOrUpdateMetadata(String docId, @Nullable byte[] metadata) {
            Document doc = get(docId);
            if (doc == null) {
                doc = new Document();
                doc.docId = docId;
                doc.metadata = metadata;
                insert(doc);
            } else {
                doc.metadata = metadata;
                update(doc);
            }
        }

        @Transaction
        void transact(ThrowingRunnable runnable) {
            runnable.run();
        }
    }

    /** Entry point to the room DB. */
    @Database(
            entities = {Document.class},
            version = 1)
    abstract static class DocumentDB extends RoomDatabase {

        /** Get a {@link DocumentDao}. */
        public abstract DocumentDao documentDao();
    }
}
