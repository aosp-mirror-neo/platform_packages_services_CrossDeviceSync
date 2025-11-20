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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.DocumentSchemaInfo;
import com.android.crossdevicesync.data.PathSchemaInfo;
import com.android.crossdevicesync.data.RecordType;
import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStore;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/** Fake implementation of {@link SharedDataStore} for testing. */
@SuppressWarnings("EffectivelyPrivate")
public class FakeSharedDataStore<T> implements SharedDataStore<T> {
    private final Map<String, FakeDocument<T>> mDocs = new HashMap<>();
    private final List<Pair<Executor, OnRemoteChangeListener>> mListeners = new ArrayList<>();
    private final String mName;
    private final DeviceNodeIdProvider mDeviceNodeIdProvider;
    @Nullable private final SchemaProvider<T> mSchemaProvider;
    private boolean mOpen;
    private boolean mAsync;
    @Nullable private String mSelfNodeId;
    @Nullable private SettableFuture<?> mInitFuture;
    @Nullable private SettableFuture<?> mDeleteFuture;
    @Nullable private SettableFuture<?> mCloseFuture;
    @Nullable private SettableFuture<?> mTransactFuture;

    /** Create an empty shared data store. */
    public FakeSharedDataStore() {
        this(
                "test_data_store",
                new DeviceNodeIdProvider() {
                    @Override
                    public String getOrCreateNodeIdForDataStore(String dataStore) {
                        return "test_node_id";
                    }

                    @Override
                    public void noteDataStoreDeletion(String dataStore) {
                        // Do nothing
                    }
                },
                /* schemaProvider= */ null);
    }

    /**
     * Create an empty shared data store with specified name, node id provider, and schema provider.
     */
    public FakeSharedDataStore(
            String name,
            DeviceNodeIdProvider deviceNodeIdProvider,
            @Nullable SchemaProvider<T> schemaProvider) {
        mName = name;
        mDeviceNodeIdProvider = deviceNodeIdProvider;
        mSchemaProvider = schemaProvider;
    }

    @Override
    public ListenableFuture<?> init() {
        mOpen = true;
        mSelfNodeId = mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(mName);
        List<ListenableFuture<?>> futures = new ArrayList<>();
        if (mSchemaProvider != null) {
            for (DocumentSchemaInfo schemaInfo : mSchemaProvider.getAllDocumentSchema()) {
                futures.add(
                        transact(
                                schemaInfo.getDocId(),
                                doc -> {
                                    ((FakeDocument<T>) doc).initWithSchema(schemaInfo);
                                    mSchemaProvider.migrateDocument(doc);
                                    return true;
                                }));
            }
        }
        if (mAsync) {
            mInitFuture = SettableFuture.create();
            futures.add(mInitFuture);
        }
        // Returned future should be successful only if all the pending tasks are done.
        return Futures.allAsList(futures);
    }

    @Nullable
    public SettableFuture<?> getInitFuture() {
        return mInitFuture;
    }

    @Override
    public String getLocalDeviceNodeId() {
        return mSelfNodeId;
    }

    @Override
    public <R> ListenableFuture<R> transact(String docId, TransactionApplier<T, R> applier) {
        if (mAsync) {
            // Re-use current future is possible since transact can be called multiple times
            // during test.
            if (mTransactFuture == null || mTransactFuture.isDone()) {
                mTransactFuture = SettableFuture.create();
            }
            return Futures.transformAsync(
                    mTransactFuture,
                    unused -> doTransact(docId, applier, /* isLocal= */ true, mSelfNodeId),
                    directExecutor());
        } else {
            return doTransact(docId, applier, /* isLocal= */ true, mSelfNodeId);
        }
    }

    /** Simulate a remote change. */
    public <R> ListenableFuture<R> transactAsRemote(
            String docId, TransactionApplier<T, R> applier, String remoteNodeId) {
        return doTransact(
                docId,
                doc -> {
                    Map<String, Object> before = ((FakeDocument<T>) doc).getAll();
                    R result = applier.transact(doc);
                    Map<String, Object> after = ((FakeDocument<T>) doc).getAll();
                    for (String path : after.keySet()) {
                        if (!Objects.equals(before.get(path), after.get(path))) {
                            mListeners.forEach(
                                    l -> l.first.execute(() -> l.second.onRemoteChange(path)));
                        }
                    }
                    return result;
                },
                /* isLocal= */ false,
                remoteNodeId);
    }

    private <R> ListenableFuture<R> doTransact(
            String docId, TransactionApplier<T, R> applier, boolean isLocal, String nodeId) {
        if (!mOpen) {
            return Futures.immediateFailedFuture(new IllegalStateException("Closed"));
        }
        final int schemaVersion;
        if (mSchemaProvider == null) {
            schemaVersion = 1;
        } else {
            DocumentSchemaInfo schemaInfo = mSchemaProvider.findSchema(docId);
            if (schemaInfo == null) {
                return Futures.immediateFailedFuture(new IllegalStateException("No schema"));
            }
            schemaVersion = schemaInfo.getVersion();
        }
        try {
            FakeDocument<T> doc =
                    mDocs.computeIfAbsent(
                                    docId, k -> new FakeDocument<>(docId, schemaVersion, nodeId))
                            .withIdentity(isLocal, nodeId);
            R result = applier.transact(doc);
            if (mSchemaProvider != null) {
                try {
                    mSchemaProvider.validateDocument(doc);
                } catch (Exception e) {
                    return Futures.immediateFailedFuture(e);
                }
            }
            return Futures.immediateFuture(result);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public void registerOnRemoteChangeListener(Executor executor, OnRemoteChangeListener listener) {
        mListeners.add(Pair.create(executor, listener));
    }

    @Override
    public void unregisterOnRemoteChangeListener(OnRemoteChangeListener listener) {
        mListeners.removeIf(l -> l.second == listener);
    }

    @Override
    public ListenableFuture<?> closeWithCallback() {
        mOpen = false;
        if (mAsync) {
            mCloseFuture = SettableFuture.create();
            return mCloseFuture;
        }
        return Futures.immediateVoidFuture();
    }

    @Nullable
    public SettableFuture<?> getCloseFuture() {
        return mCloseFuture;
    }

    @Override
    public boolean isOpen() {
        return mOpen;
    }

    @Override
    public ListenableFuture<?> delete() {
        mDocs.clear();
        mOpen = false;
        mDeviceNodeIdProvider.noteDataStoreDeletion(mName);
        if (mAsync) {
            mDeleteFuture = SettableFuture.create();
            return mDeleteFuture;
        }
        return Futures.immediateVoidFuture();
    }

    @Nullable
    public SettableFuture<?> getDeleteFuture() {
        return mDeleteFuture;
    }

    /** Make init/close/delete/transact operations async. */
    public FakeSharedDataStore<T> setAsync(boolean async) {
        mAsync = async;
        if (!mAsync) {
            if (mInitFuture != null) {
                mInitFuture.set(null);
                mInitFuture = null;
            }
            if (mTransactFuture != null) {
                mTransactFuture.set(null);
                mTransactFuture = null;
            }
            if (mCloseFuture != null) {
                mCloseFuture.set(null);
                mCloseFuture = null;
            }
            if (mDeleteFuture != null) {
                mDeleteFuture.set(null);
                mDeleteFuture = null;
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return "{name=" + mName + ", open=" + mOpen + ", docs=" + mDocs + "}";
    }

    private static class FakeRecord<T> implements Record<T> {
        @Nullable private T mData;
        private final FakeMetadata mMetadata = new FakeMetadata();

        @Nullable
        @Override
        public T get() {
            return mData;
        }

        @Override
        public FakeMetadata getMetadata() {
            return mMetadata;
        }

        public void set(T val) {
            mData = val;
        }

        @Override
        public String toString() {
            return "{data="
                    + (mData == null ? "null" : mData.toString())
                    + ", metadata="
                    + mMetadata
                    + "}";
        }
    }

    private static class FakeSetRecord<T> implements SetRecord<T> {
        private final Set<T> mSet = new HashSet<>();
        private final FakeMetadata mMetadata = new FakeMetadata();

        @Override
        public Set<T> entries() {
            return mSet;
        }

        @Override
        public FakeMetadata getMetadata() {
            return mMetadata;
        }

        @Override
        public String toString() {
            return "{set=" + mSet + ", metadata=" + mMetadata + "}";
        }
    }

    private static class FakeUnmergedRecord<T> implements UnmergedRecord<T> {

        private final Map<String, T> mEntries = new HashMap<>();
        private final FakeMetadata mMetadata = new FakeMetadata();
        private final String mSelfNodeIf;

        FakeUnmergedRecord(String selfNodeId) {
            mSelfNodeIf = selfNodeId;
        }

        @Override
        public Map<String, T> entries() {
            return mEntries;
        }

        @Nullable
        @Override
        public T get() {
            return mEntries.get(mSelfNodeIf);
        }

        @Override
        public FakeMetadata getMetadata() {
            return mMetadata;
        }

        @Override
        public String toString() {
            return "{entries=" + mEntries + ", metadata=" + mMetadata + "}";
        }
    }

    private static class FakeDocument<T> implements MutableDocument<T> {
        private final Map<String, Record<T>> mRecords = new HashMap<>();
        private final String mId;
        private final int mSchemaVersion;
        private final String mSelfNodeId;
        private boolean mIsNextUpdateLocal;
        private String mNextUpdateNodeId;

        FakeDocument(String id, int schemaVersion, String selfNodeId) {
            mId = id;
            mSchemaVersion = schemaVersion;
            mSelfNodeId = selfNodeId;
            mIsNextUpdateLocal = true;
            mNextUpdateNodeId = mSelfNodeId;
        }

        @Override
        public void putData(String path, @Nullable T data) {
            FakeRecord<T> r =
                    ((FakeRecord<T>) mRecords.computeIfAbsent(path, k -> new FakeRecord<>()));
            if (Objects.equals(r.get(), data)) {
                return;
            }
            r.set(data);
            r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
        }

        @Override
        public void putUnmergedData(String path, @Nullable T data) {
            FakeUnmergedRecord<T> r =
                    ((FakeUnmergedRecord<T>)
                            mRecords.computeIfAbsent(
                                    path, k -> new FakeUnmergedRecord<>(mSelfNodeId)));
            if (Objects.equals(r.entries().get(mSelfNodeId), data)) {
                return;
            }
            r.entries().put(mNextUpdateNodeId, data);
            r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
        }

        @Override
        public void addDataToSet(String path, T data) {
            FakeSetRecord<T> r =
                    ((FakeSetRecord<T>) mRecords.computeIfAbsent(path, k -> new FakeSetRecord<>()));
            if (r.entries().add(data)) {
                r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
            }
        }

        @Override
        public void removeDataFromSet(String path, T data) {
            FakeSetRecord<T> r =
                    ((FakeSetRecord<T>) mRecords.computeIfAbsent(path, k -> new FakeSetRecord<>()));
            if (r.entries().remove(data)) {
                r.getMetadata().setLastModifiedByLocalDevice(mIsNextUpdateLocal);
            }
        }

        @Override
        public String getDocId() {
            return mId;
        }

        @Override
        public int getSchemaVersion() {
            return mSchemaVersion;
        }

        @Nullable
        @Override
        public Record<T> getRecord(String path) {
            return mRecords.get(path);
        }

        @Override
        public boolean containsPath(String path) {
            return mRecords.containsKey(path);
        }

        @Override
        public String getDebugStringForPath(String path) {
            return mId + "::" + path;
        }

        public Map<String, Object> getAll() {
            Map<String, Object> res = new HashMap<>();
            for (Map.Entry<String, Record<T>> entry : mRecords.entrySet()) {
                String path = entry.getKey();
                Record<T> r = entry.getValue();
                Object val;
                if (r instanceof UnmergedRecord<?> unmerged) {
                    val = unmerged.entries();
                } else if (r instanceof SetRecord<?> set) {
                    val = set.entries();
                } else {
                    val = r.get();
                }
                res.put(path, val);
            }
            return res;
        }

        /** Save the identity of the caller. */
        public FakeDocument<T> withIdentity(boolean isLocal, String nodeId) {
            mIsNextUpdateLocal = isLocal;
            if (isLocal) {
                mNextUpdateNodeId = mSelfNodeId;
            } else {
                mNextUpdateNodeId = nodeId;
            }
            return this;
        }

        public void initWithSchema(DocumentSchemaInfo schema) {
            for (PathSchemaInfo pathSchema : schema.getPathSchema()) {
                switch (pathSchema.type()) {
                    case RecordType.TYPE_REGISTER ->
                            mRecords.put(pathSchema.path(), new FakeRecord<>());
                    case RecordType.TYPE_SET ->
                            mRecords.put(pathSchema.path(), new FakeSetRecord<>());
                    case RecordType.TYPE_UNMERGED ->
                            mRecords.put(pathSchema.path(), new FakeUnmergedRecord<>(mSelfNodeId));
                }
            }
        }

        @Override
        public String toString() {
            return "{id="
                    + mId
                    + ", schemaVersion="
                    + mSchemaVersion
                    + ", records="
                    + mRecords
                    + "}";
        }
    }

    private static class FakeMetadata implements Metadata {
        private boolean mIsLastModifiedByLocalDevice = true;

        @Override
        public boolean isLastModifiedByLocalDevice() {
            return mIsLastModifiedByLocalDevice;
        }

        public void setLastModifiedByLocalDevice(boolean val) {
            mIsLastModifiedByLocalDevice = val;
        }

        @Override
        public String toString() {
            return "{lastModifiedByLocalDevice=" + mIsLastModifiedByLocalDevice + "}";
        }
    }
}
