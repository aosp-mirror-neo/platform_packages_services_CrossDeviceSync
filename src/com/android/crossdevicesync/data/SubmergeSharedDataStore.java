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

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.android.crossdevicesync.data.INetwork.OnNetworkMessageListener;
import com.android.crossdevicesync.data.SchemaProvider.DocumentSchemaInfo;
import com.android.crossdevicesync.data.proto.DocMetadata;
import com.android.crossdevicesync.data.proto.DocMetadata.DocumentMetadata;
import com.android.crossdevicesync.data.proto.NetworkMessageOuterClass.DocUpdateMessage;
import com.android.crossdevicesync.data.proto.NetworkMessageOuterClass.DocVersionMessage;
import com.android.crossdevicesync.data.proto.NetworkMessageOuterClass.NetworkMessage;

import com.google.android.submerge.Converter;
import com.google.android.submerge.DataStore;
import com.google.android.submerge.NetworkInterface;
import com.google.android.submerge.OlderBaseNeededException;
import com.google.android.submerge.StorageInterface;
import com.google.android.submerge.StorageInterface.StorageException;
import com.google.android.submerge.TimestampProvider;
import com.google.android.submerge.VersionVector;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import dagger.Lazy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * An implementation of the {@link SharedDataStore} using submerge, a distributed data consistency
 * management library.
 *
 * @param <T> the data type that this data store manages.
 */
public class SubmergeSharedDataStore<T> implements SharedDataStore<T>, OnNetworkMessageListener {
    private static final String TAG = "SubmergeSharedDataStore";

    /**
     * Prefix for a virtual node ID used for schema changes.
     *
     * <p>Submerge uses a "last change wins" strategy for concurrent updates. If multiple devices
     * update the schema concurrently, the one with the latest timestamp overwrites the others,
     * potentially causing data loss.
     *
     * <p>To avoid this, schema changes are attributed to a virtual node ID like "schema_node_xxx"
     * (where "xxx" is the document ID). The timestamp for this virtual node is the schema version.
     * This makes all schema updates appear to come from the same virtual node with the same
     * timestamp, allowing Submerge to merge changes correctly instead of overwriting the entire
     * subtree.
     *
     * <p>Warning: this should never change, or cross-device concurrent schema change will lead to
     * data loss.
     */
    private static final String SCHEMA_NODE_ID_PREFIX = "schema_node_";

    // The data store is not initialized.
    private static final int STATE_UNINITIALIZED = 0;
    // The data store is performing an upgrade.
    private static final int STATE_UPGRADING = 1;
    // The data store failed to upgrade.
    private static final int STATE_FAILED_TO_UPGRADE = 2;
    // The data store is open and ready for use.
    private static final int STATE_OPEN = 3;
    // The data store is closed.
    private static final int STATE_CLOSED = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        STATE_UNINITIALIZED,
        STATE_UPGRADING,
        STATE_FAILED_TO_UPGRADE,
        STATE_OPEN,
        STATE_CLOSED
    })
    public @interface State {}

    private final Object mLock = new Object();

    private final String mName;
    private final DeviceNodeIdProvider mDeviceNodeIdProvider;
    private final Lazy<ListeningExecutorService> mIOThreadExecutor;
    private final IStorage mStorage;
    private final INetwork.Factory mNetworkFactory;
    private final TimestampProvider mTimestampProvider;
    private final Converter<T> mConverter;
    private final SchemaProvider<T> mSchemaProvider;
    private final NetworkInterface mNetworkInterface =
            new NetworkInterface() {
                @Override
                public void onNewUpdate(String docId, byte[] updateMessage) {
                    synchronized (mLock) {
                        requireStateLocked(STATE_UPGRADING, STATE_OPEN);
                        requireNonNull(mNetwork)
                                .broadcastMessage(encodeDocUpdateMessage(docId, updateMessage));
                    }
                }
            };
    private final StorageInterface mDocumentStorageInterface =
            new StorageInterface() {
                @Override
                public void onNewUpdate(String docId, byte[] serializedDoc)
                        throws StorageException {
                    mStorage.persistDocument(docId, serializedDoc);
                }

                @Nullable
                @Override
                public byte[] readFromStorage(String docId) throws StorageException {
                    return mStorage.getDocument(docId);
                }
            };
    private final StorageInterface mSchemaStorageInterface =
            new StorageInterface() {
                @Override
                public void onNewUpdate(String docId, byte[] serializedSchema)
                        throws StorageException {
                    mStorage.persistDocumentSchema(docId, serializedSchema);
                }

                @Nullable
                @Override
                public byte[] readFromStorage(String docId) throws StorageException {
                    return mStorage.getDocumentSchema(docId);
                }
            };

    @GuardedBy("mLock")
    @Nullable
    private DataStore<T> mSubmergeDataStore;

    @GuardedBy("mLock")
    @Nullable
    private INetwork mNetwork;

    @GuardedBy("mLock")
    @Nullable
    private String mNodeId;

    @GuardedBy("mLock")
    private final List<OnRemoteChangeListenerRecord> mRemoteChangeListeners = new ArrayList<>();

    @GuardedBy("mLock")
    @State
    private int mState = STATE_UNINITIALIZED;

    public SubmergeSharedDataStore(
            String name,
            DeviceNodeIdProvider deviceNodeIdProvider,
            Lazy<ListeningExecutorService> ioThreadExecutor,
            IStorage storage,
            INetwork.Factory networkFactory,
            TimestampProvider timestampProvider,
            Converter<T> converter,
            SchemaProvider<T> schemaProvider) {
        this.mName = name;
        this.mDeviceNodeIdProvider = deviceNodeIdProvider;
        this.mIOThreadExecutor = ioThreadExecutor;
        this.mStorage = storage;
        this.mNetworkFactory = networkFactory;
        this.mTimestampProvider = timestampProvider;
        this.mConverter = converter;
        this.mSchemaProvider = schemaProvider;
    }

    @SuppressWarnings("MustBeClosedChecker")
    @Override
    public ListenableFuture<Boolean> init() {
        synchronized (mLock) {
            requireStateLocked(STATE_UNINITIALIZED);
            mState = STATE_UPGRADING;
            mNodeId = mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(mName);
            mNetwork = mNetworkFactory.create(mNodeId);
            mSubmergeDataStore = openDocumentDataStore(mNodeId, mTimestampProvider);
            // Init the storage and upgrade the data store asynchronously.
            return mIOThreadExecutor.get().submit(this::doInit);
        }
    }

    private boolean doInit() throws Exception {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING);
            requireNonNull(mNetwork).registerNetworkMessageListener(mIOThreadExecutor.get(), this);
        }
        mStorage.init();
        upgrade();
        return true;
    }

    private void upgrade() throws Exception {
        String nodeId;
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING);
            nodeId = requireNonNull(mNodeId);
        }
        for (DocumentSchemaInfo schema : mSchemaProvider.getAllDocumentSchema()) {
            String docId = schema.getDocId();
            try (DataStore<T> schemaDataStore = openSchemaDataStore(docId, schema.getVersion());
                    SubmergeDocument<T> schemaDoc = openSchemaDoc(schemaDataStore, docId);
                    // Create a new submerge data store with timestamp == 0 so that changes made
                    // during data migration are less likely to override concurrent change from
                    // remote devices.
                    DataStore<T> documentDataStore =
                            openDocumentDataStore(nodeId, /* timestampProvider= */ () -> 0);
                    SubmergeDocument<T> document = openDocument(nodeId, documentDataStore, docId)) {
                int previousVersion = document.getSchemaVersion();
                if (previousVersion == schema.getVersion()) {
                    // Version unchanged. No need to upgrade.
                    Log.i(
                            TAG,
                            "Schema for "
                                    + getDebugDocIdentityString(docId)
                                    + " is up to date. Proceed to validation.");
                    mSchemaProvider.validateDocument(document);
                    continue;
                } else if (previousVersion > schema.getVersion()) {
                    throw new IllegalSchemaChangeException(
                            "upgrade: schema version of "
                                    + getDebugDocIdentityString(docId)
                                    + " is lower than current version "
                                    + previousVersion
                                    + "!");
                }
                Log.i(
                        TAG,
                        "Upgrading schema version from "
                                + previousVersion
                                + " to "
                                + schema.getVersion()
                                + " for "
                                + getDebugDocIdentityString(docId));
                for (Map.Entry<String, Integer> entry : schema.getPathSchema().entrySet()) {
                    String path = entry.getKey();
                    int type = entry.getValue();
                    switch (type) {
                        case SchemaProvider.TYPE_REGISTER:
                            schemaDoc.addRegisterSchema(path);
                            break;
                        case SchemaProvider.TYPE_UNMERGED:
                            schemaDoc.addUnmergedSchema(path);
                            break;
                        case SchemaProvider.TYPE_SET:
                            schemaDoc.addSetSchema(path);
                            break;
                        default:
                            throw new IllegalSchemaChangeException(
                                    "update: unknown data type " + type);
                    }
                }
                // Apply changes in transaction so that database can rollback in case upgrade fails.
                mStorage.transact(
                        s -> {
                            // Step 1: commit schema store, and retrieve a delta.
                            schemaDoc.commitTransaction();
                            byte[] schemaUpdateBytes = schemaDataStore.getFullUpdateMessage(docId);

                            // Step 2: merge the schema change.
                            Log.i(
                                    TAG,
                                    "Merging schema update into "
                                            + getDebugDocIdentityString(docId));
                            document.mergeLocalUpdate(schemaUpdateBytes);

                            // Step 3: perform feature specific migration steps.
                            Log.i(TAG, "Migrating document " + getDebugDocIdentityString(docId));
                            mSchemaProvider.migrateDocument(document);

                            // Step 4: set the new schema version if not already updated by the
                            // migration.
                            document.setSchemaVersion(schema.getVersion());

                            // Step 5: ensure the schema validation passes.
                            Log.i(
                                    TAG,
                                    "Validating upgraded document "
                                            + getDebugDocIdentityString(docId));
                            mSchemaProvider.validateDocument(document);

                            // Step 6: commit the document.
                            Log.i(TAG, "Committing document " + getDebugDocIdentityString(docId));
                            s.persistMetadata(docId, document.getMetaData().toByteArray());
                            document.commitTransaction();
                        });
                Log.i(
                        TAG,
                        "Successfully upgraded schema version from "
                                + previousVersion
                                + " to "
                                + schema.getVersion()
                                + " for "
                                + getDebugDocIdentityString(docId));
            } catch (Exception e) {
                Log.e(TAG, "upgrade: failed to upgrade schema for " + getSchemaNodeId(docId), e);
                // Advance state and re-throw.
                synchronized (mLock) {
                    if (mState == STATE_UPGRADING) {
                        mState = STATE_FAILED_TO_UPGRADE;
                    }
                }
                throw e;
            }
        }
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING);
            mState = STATE_OPEN;
        }
    }

    /** Get the node id used for creating a schema data store. */
    private static String getSchemaNodeId(String docId) {
        return SCHEMA_NODE_ID_PREFIX + docId;
    }

    /** Open the submerge schema data store. */
    @MustBeClosed
    private DataStore<T> openSchemaDataStore(String docId, int schemaVersion) {
        // Create a submerge data store representing a virtual device for updating schema. The
        // virtual device's node id is always SCHEMA_NODE_ID_PREFIX + docId. Its timestamp is always
        // the schemaVersion.
        return new DataStore<>(
                getSchemaNodeId(docId),
                new NoOpNetworkInterface(),
                mSchemaStorageInterface,
                /* timestampProvider= */ () -> schemaVersion,
                mConverter);
    }

    /** Open a schema doc. */
    @SuppressWarnings("MustBeClosedChecker")
    @MustBeClosed
    private SubmergeDocument<T> openSchemaDoc(DataStore<T> schemaDataStore, String docId) {
        return new SubmergeDocument<>(
                mName,
                docId,
                schemaDataStore.newDocumentTransaction(docId),
                getSchemaNodeId(docId),
                DocMetadata.DocumentMetadata.getDefaultInstance());
    }

    /** Open the submerge document data store. */
    @MustBeClosed
    private DataStore<T> openDocumentDataStore(String nodeId, TimestampProvider timestampProvider) {
        return new DataStore<>(
                nodeId,
                mNetworkInterface,
                mDocumentStorageInterface,
                timestampProvider,
                mConverter);
    }

    /** Open a document. */
    @SuppressWarnings("MustBeClosedChecker")
    @MustBeClosed
    private SubmergeDocument<T> openDocument(
            String nodeId, DataStore<T> documentDataStore, String docId)
            throws InvalidProtocolBufferException, StorageException {
        DocumentMetadata metadata = loadDocMetadata(docId);
        return new SubmergeDocument<>(
                mName, docId, documentDataStore.newDocumentTransaction(docId), nodeId, metadata);
    }

    @Override
    public String getLocalDeviceNodeId() {
        String nodeId;
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_FAILED_TO_UPGRADE, STATE_OPEN);
            nodeId = requireNonNull(mNodeId);
        }
        return nodeId;
    }

    @Override
    public <U> ListenableFuture<U> transact(String docId, TransactionApplier<T, U> applier) {
        synchronized (mLock) {
            try {
                requireStateLocked(STATE_UPGRADING, STATE_OPEN);
                Log.d(TAG, "Starting a transaction on " + getDebugDocIdentityString(docId));
                return mIOThreadExecutor.get().submit(() -> doTransact(docId, applier));
            } catch (IllegalStateException e) {
                return Futures.immediateFailedFuture(e);
            }
        }
    }

    /** Called on IO thread for transaction. */
    private <U> U doTransact(String docId, TransactionApplier<T, U> applier) throws Exception {
        DataStore<T> submergeDataStore;
        String nodeId;
        synchronized (mLock) {
            requireStateLocked(STATE_OPEN);
            submergeDataStore = requireNonNull(mSubmergeDataStore);
            nodeId = requireNonNull(mNodeId);
        }
        try (SubmergeDocument<T> document = openDocument(nodeId, submergeDataStore, docId)) {
            U result = applier.transact(document);
            mSchemaProvider.validateDocument(document);
            // Update metadata and submerge data in a single transaction so that the database can
            // rollback partial update upon failure.
            mStorage.transact(
                    s -> {
                        s.persistMetadata(docId, document.getMetaData().toByteArray());
                        document.commitTransaction();
                    });
            Log.d(TAG, "Transaction committed for " + getDebugDocIdentityString(docId));
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to commit transaction for " + getDebugDocIdentityString(docId), e);
            throw e;
        }
    }

    private DocMetadata.DocumentMetadata loadDocMetadata(String docId)
            throws StorageException, InvalidProtocolBufferException {
        byte[] metadataBytes = mStorage.getMetadata(docId);
        if (metadataBytes == null) {
            return DocMetadata.DocumentMetadata.getDefaultInstance();
        }
        return DocMetadata.DocumentMetadata.parseFrom(metadataBytes);
    }

    /** Called on IO thread when a network message is received. */
    @Override
    public void onNetworkMessage(String srcDeviceNodeId, byte[] message) {
        Log.i(TAG, "onNetworkMessage: srcDeviceNodeId = " + srcDeviceNodeId);
        DataStore<T> submergeDataStore;
        INetwork network;
        synchronized (mLock) {
            if (mState != STATE_OPEN) {
                Log.w(
                        TAG,
                        "onNetworkMessage: ignored - illegal state "
                                + stateToString(mState)
                                + " in data store \""
                                + mName
                                + "\".");
                return;
            }
            submergeDataStore = requireNonNull(mSubmergeDataStore);
            network = requireNonNull(mNetwork);
        }
        NetworkMessage networkMessage;
        try {
            networkMessage = NetworkMessage.parseFrom(message);
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "onNetworkUpdate: failed to parse network message", e);
            return;
        }
        if (networkMessage.hasDocUpdate()) {
            String docId = networkMessage.getDocUpdate().getDocId();
            Log.d(TAG, "Received network update for " + getDebugDocIdentityString(docId));
            try {
                List<String> changedPaths =
                        doTransact(
                                docId,
                                doc ->
                                        ((SubmergeDocument<T>) doc)
                                                .mergeNetworkUpdate(
                                                        networkMessage
                                                                .getDocUpdate()
                                                                .getDocUpdate()
                                                                .toByteArray()));
                for (String path : changedPaths) {
                    mRemoteChangeListeners.forEach(record -> record.onRemoteChange(path));
                }
            } catch (OlderBaseNeededException e) {
                Log.i(
                        TAG,
                        "onNetworkUpdate: need a older base to commit network update for "
                                + getDebugDocIdentityString(docId));
                // Send the needed version vector to request another update message with proper
                // version base.
                try (VersionVector v = e.getBaseVersionNeeded()) {
                    network.unicastMessage(srcDeviceNodeId, encodeVersionVectorMessage(docId, v));
                }
            } catch (Exception e) {
                Log.e(
                        TAG,
                        "onNetworkUpdate: failed to commit network update for "
                                + getDebugDocIdentityString(docId),
                        e);
            }
        } else if (networkMessage.hasDocVersion()) {
            String docId = networkMessage.getDocVersion().getDocId();
            Log.d(TAG, "Received version vector for " + getDebugDocIdentityString(docId));
            try (VersionVector otherVersion =
                            VersionVector.fromByteArray(
                                    networkMessage.getDocVersion().getDocVersion().toByteArray());
                    VersionVector myVersion = submergeDataStore.getDocumentVersion(docId)) {
                if (isPartiallyOlder(otherVersion, myVersion)) {
                    Log.i(
                            TAG,
                            "Sending delta update for "
                                    + getDebugDocIdentityString(docId)
                                    + " to "
                                    + srcDeviceNodeId);
                    network.unicastMessage(
                            srcDeviceNodeId,
                            encodeDocUpdateMessage(
                                    docId, submergeDataStore.calculateDelta(docId, otherVersion)));
                }
                if (isPartiallyOlder(myVersion, otherVersion)) {
                    Log.i(
                            TAG,
                            "Sending version of "
                                    + getDebugDocIdentityString(docId)
                                    + " to "
                                    + srcDeviceNodeId);
                    network.unicastMessage(
                            srcDeviceNodeId, encodeVersionVectorMessage(docId, myVersion));
                }
            }
        } else {
            Log.w(TAG, "onNetworkUpdate: unknown network message");
        }
    }

    private static boolean isPartiallyOlder(VersionVector a, VersionVector b) {
        int comp = a.compare(b);
        return comp == VersionVector.OLDER || comp == VersionVector.CONCURRENT;
    }

    private byte[] encodeVersionVectorMessage(String docId, VersionVector versionVector) {
        return NetworkMessage.newBuilder()
                .setDocVersion(
                        DocVersionMessage.newBuilder()
                                .setDocId(docId)
                                .setDocVersion(ByteString.copyFrom(versionVector.toByteArray()))
                                .build())
                .build()
                .toByteArray();
    }

    private byte[] encodeDocUpdateMessage(String docId, byte[] docUpdate) {
        NetworkMessage message =
                NetworkMessage.newBuilder()
                        .setDocUpdate(
                                DocUpdateMessage.newBuilder()
                                        .setDocId(docId)
                                        .setDocUpdate(ByteString.copyFrom(docUpdate)))
                        .build();
        return message.toByteArray();
    }

    @Override
    public void registerOnRemoteChangeListener(Executor executor, OnRemoteChangeListener listener) {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_FAILED_TO_UPGRADE, STATE_OPEN);
            mRemoteChangeListeners.add(new OnRemoteChangeListenerRecord(executor, listener));
        }
    }

    @Override
    public void unregisterOnRemoteChangeListener(OnRemoteChangeListener listener) {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_FAILED_TO_UPGRADE, STATE_OPEN);
            mRemoteChangeListeners.removeIf(record -> record.mListener == listener);
        }
    }

    /**
     * Initiate a sync with a remote device. This will asynchronously trigger exchange of all known
     * documents with the remote device.
     */
    public void requestSyncWithRemoteDevice(String remoteDeviceNodeId) {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_FAILED_TO_UPGRADE, STATE_OPEN);
            mIOThreadExecutor
                    .get()
                    .execute(() -> doRequestSyncWithRemoteDevice(remoteDeviceNodeId));
        }
    }

    /** Called on IO thread for syncing. */
    private void doRequestSyncWithRemoteDevice(String remoteDeviceNodeId) {
        DataStore<T> submergeDataStore;
        INetwork network;
        synchronized (mLock) {
            if (mState != STATE_OPEN) {
                Log.w(
                        TAG,
                        "doRequestSyncWithRemoteDevice: ignored - illegal state "
                                + stateToString(mState)
                                + " in data store \""
                                + mName
                                + "\".");
                return;
            }
            submergeDataStore = requireNonNull(mSubmergeDataStore);
            network = requireNonNull(mNetwork);
        }
        // Sending version vector to remote device will trigger a delta update.
        for (DocumentSchemaInfo schema : mSchemaProvider.getAllDocumentSchema()) {
            String docId = schema.getDocId();
            try (VersionVector version = submergeDataStore.getDocumentVersion(docId)) {
                network.unicastMessage(
                        remoteDeviceNodeId, encodeVersionVectorMessage(docId, version));
            }
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mState == STATE_UNINITIALIZED || mState == STATE_CLOSED) {
                Log.w(
                        TAG,
                        "close: ignored - illegal state "
                                + stateToString(mState)
                                + " in data store \""
                                + mName
                                + "\".");
                return;
            }
            Log.i(TAG, "closing data store \"" + mName + "\".");
            mState = STATE_CLOSED;
            mRemoteChangeListeners.clear();
            DataStore<T> submergeDataStore = requireNonNull(mSubmergeDataStore);
            mSubmergeDataStore = null;
            requireNonNull(mNetwork).unregisterNetworkMessageListener(this);
            mNetwork = null;
            mNodeId = null;
            mIOThreadExecutor
                    .get()
                    .execute(
                            () -> {
                                submergeDataStore.close();
                                mStorage.close();
                            });
            mIOThreadExecutor.get().shutdown();
        }
    }

    @Override
    public boolean isOpen() {
        synchronized (mLock) {
            return mState == STATE_OPEN;
        }
    }

    @Override
    public void delete() {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_FAILED_TO_UPGRADE, STATE_OPEN);
            mIOThreadExecutor.get().execute(this::doDelete);
        }
    }

    private void doDelete() {
        synchronized (mLock) {
            requireStateLocked(STATE_FAILED_TO_UPGRADE, STATE_OPEN);
        }
        Log.w(TAG, "Deleting data store \"" + mName + "\".");
        if (mStorage.deleteDatabase()) {
            mDeviceNodeIdProvider.noteDataStoreDeletion(mName);
            Log.w(TAG, "Deleted data store \"" + mName + "\".");
        }
        close();
    }

    @GuardedBy("mLock")
    private void requireStateLocked(@State int... states) {
        for (int state : states) {
            if (mState == state) {
                return;
            }
        }
        throw new IllegalStateException(
                "Data store"
                        + mName
                        + "'s current state "
                        + stateToString(mState)
                        + " is illegal! Expect one of "
                        + Arrays.stream(states)
                                .mapToObj(SubmergeSharedDataStore::stateToString)
                                .collect(Collectors.joining(", ")));
    }

    private String getDebugDocIdentityString(String docId) {
        return mName + "::" + docId;
    }

    private static String stateToString(@State int state) {
        return switch (state) {
            case STATE_UNINITIALIZED -> "UNINITIALIZED";
            case STATE_UPGRADING -> "UPGRADING";
            case STATE_FAILED_TO_UPGRADE -> "FAILED_TO_UPGRADE";
            case STATE_OPEN -> "OPEN";
            case STATE_CLOSED -> "CLOSED";
            default -> "UNKNOWN(" + state + ")";
        };
    }

    private static class OnRemoteChangeListenerRecord {
        private final Executor mExecutor;
        private final OnRemoteChangeListener mListener;

        OnRemoteChangeListenerRecord(Executor executor, OnRemoteChangeListener listener) {
            mExecutor = executor;
            mListener = listener;
        }

        void onRemoteChange(String path) {
            mExecutor.execute(() -> mListener.onRemoteChange(path));
        }
    }

    private static class NoOpNetworkInterface implements NetworkInterface {
        @Override
        public void onNewUpdate(String docId, byte[] updateMessage) {
            // Do nothing.
        }
    }
}
