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

import com.android.internal.util.FunctionalUtils.ThrowingConsumer;

import com.google.android.submerge.StorageInterface.StorageException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Implementation of {@link IStorage} using room DB. */
public class RoomStorage implements IStorage {
    private static final String TAG = "RoomStorage";
    private static final ThreadLocal<Boolean> IS_IO_THREAD = ThreadLocal.withInitial(() -> false);

    private final Context mContext;
    private final String mName;

    @Nullable private DocumentDB mDatabase;
    @Nullable private DocumentDao mDao;
    @Nullable private volatile ListeningExecutorService mIoThreadExecutorService;

    public RoomStorage(Context context, String name) {
        mContext = context.createDeviceProtectedStorageContext();
        mName = name;
    }

    @Override
    public void open() {
        requireIoThread();
        if (mDatabase != null) {
            return;
        }
        mDatabase = Room.databaseBuilder(mContext, DocumentDB.class, mName).build();
        mDao = mDatabase.documentDao();
        Log.i(TAG, "Database \"" + mName + "\" is open!");
    }

    @Override
    @Nullable
    public byte[] getDocument(String docId) throws StorageException {
        requireIoThread();
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
        requireIoThread();
        try {
            mDao.createOrUpdateContent(docId, serializedDoc);
        } catch (Exception e) {
            Log.e(TAG, "persistDocument: failed!", e);
            throw new StorageException();
        }
    }

    @Override
    public byte[] getDocumentSchema(String docId) throws StorageException {
        requireIoThread();
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
        requireIoThread();
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
        requireIoThread();
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
        requireIoThread();
        try {
            mDao.createOrUpdateMetadata(docId, metadata);
        } catch (Exception e) {
            Log.e(TAG, "persistMetadata: failed!", e);
            throw new StorageException();
        }
    }

    @Override
    public void transact(ThrowingConsumer<IStorage> transactionApplier) throws Exception {
        requireIoThread();
        try {
            mDao.transact(() -> transactionApplier.acceptOrThrow(this));
        } catch (Exception e) {
            throw new StorageException();
        }
    }

    @Override
    public List<String> getAllDocumentIds() throws StorageException {
        requireIoThread();
        try {
            return mDao.getAllDocIds();
        } catch (Exception e) {
            throw new StorageException();
        }
    }

    @Override
    public void deleteDocuments(List<String> docIds) throws StorageException {
        requireIoThread();
        try {
            mDao.deleteByDocIds(docIds);
        } catch (Exception e) {
            throw new StorageException();
        }
    }

    @Override
    public void close() {
        requireIoThread();
        if (mDatabase == null) {
            return;
        }
        mDatabase.close();
        mDatabase = null;
        mDao = null;
        Log.i(TAG, "Database \"" + mName + "\" is closed!");
    }

    @Override
    public boolean deleteDatabase() {
        requireIoThread();
        close();
        if (mContext.deleteDatabase(mName)) {
            Log.i(TAG, "deleteDatabase: database \"" + mName + "\" is deleted!");
            return true;
        }
        return false;
    }

    @Override
    public <R> ListenableFuture<R> submitToIoThread(Callable<R> task) {
        ListeningExecutorService ioExecutorService = getOrCreateIoThreadExecutor();
        if (ioExecutorService.isShutdown()) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("IO thread is shutdown!"));
        }
        return ioExecutorService.submit(task);
    }

    @Override
    public void shutdownIoThread() {
        ListeningExecutorService ioExecutorService = mIoThreadExecutorService;
        if (ioExecutorService != null) {
            ioExecutorService.shutdown();
        }
    }

    private ListeningExecutorService getOrCreateIoThreadExecutor() {
        if (mIoThreadExecutorService != null) {
            return mIoThreadExecutorService;
        }
        synchronized (this) {
            if (mIoThreadExecutorService == null) {
                mIoThreadExecutorService =
                        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
                mIoThreadExecutorService.execute(() -> IS_IO_THREAD.set(true));
            }
        }
        return mIoThreadExecutorService;
    }

    private static void requireIoThread() {
        if (!IS_IO_THREAD.get()) {
            throw new IllegalThreadStateException("Not in IO thread!");
        }
    }
}
