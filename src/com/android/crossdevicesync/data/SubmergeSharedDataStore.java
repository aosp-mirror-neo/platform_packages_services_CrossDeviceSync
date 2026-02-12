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

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_NOT_CHANGING_LOCAL_STATE;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_UPDATES_LOCAL_STATE;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Trace;
import android.util.Log;

import com.android.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.crossdevicesync.data.model.DocumentMetadata;
import com.android.crossdevicesync.data.model.NetworkMessage;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.network.NetworkManager.Network;
import com.android.crossdevicesync.network.NetworkManager.NetworkListener;
import com.android.internal.annotations.GuardedBy;

import com.google.android.submerge.Converter;
import com.google.android.submerge.DataStore;
import com.google.android.submerge.NetworkInterface;
import com.google.android.submerge.OlderBaseNeededException;
import com.google.android.submerge.StorageInterface;
import com.google.android.submerge.TimestampProvider;
import com.google.android.submerge.VersionVector;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.MustBeClosed;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
public class SubmergeSharedDataStore<T> implements SharedDataStore<T>, NetworkListener {
    private static final String TAG = "SubmergeSharedDataStore";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // The data store is not initialized.
    private static final int STATE_UNINITIALIZED = 0;
    // The data store is performing an upgrade.
    private static final int STATE_UPGRADING = 1;
    // The data store is open and ready for use.
    private static final int STATE_OPEN = 2;
    // The data store is closed.
    private static final int STATE_CLOSED = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_UNINITIALIZED, STATE_UPGRADING, STATE_OPEN, STATE_CLOSED})
    public @interface State {}

    private final Object mLock;
    private final String mName;
    private final DeviceNodeIdProvider mDeviceNodeIdProvider;
    private final IStorage mStorage;
    private final FrameworkStatsLogProxy mFrameworkStatsLogProxy;
    private final TimestampProvider mTimestampProvider;
    private final Converter<T> mConverter;
    private final SchemaProvider<T> mSchemaProvider;
    private final SubmergeSchemaUpgradeHelper<T> mSchemaUpgradeHelper;
    private final SharedDataStoreHandle<T> mHandle;
    private final NetworkInterface mNetworkInterface = this::broadcastUpdateMessage;
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

    @GuardedBy("mLock")
    private final Map<String, Long> mDocBroadcastMessageIds = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<String, Map<String, Long>> mDocUnicastMessageIds = new HashMap<>();

    @GuardedBy("mLock")
    @Nullable
    private DataStore<T> mSubmergeDataStore;

    @GuardedBy("mLock")
    @Nullable
    private Network mNetwork;

    @GuardedBy("mLock")
    @Nullable
    private String mNodeId;

    @GuardedBy("mLock")
    private final List<OnRemoteChangeListenerRecord> mRemoteChangeListeners = new ArrayList<>();

    @GuardedBy("mLock")
    @State
    private int mState = STATE_UNINITIALIZED;

    @GuardedBy("mLock")
    @Nullable
    private ListenableFuture<Boolean> mInitFuture;

    @GuardedBy("mLock")
    @Nullable
    private ListenableFuture<Boolean> mCloseFuture;

    public SubmergeSharedDataStore(
            String name,
            Object lock,
            DeviceNodeIdProvider deviceNodeIdProvider,
            IStorage storage,
            FrameworkStatsLogProxy frameworkStatsLogProxy,
            TimestampProvider timestampProvider,
            Converter<T> converter,
            SchemaProvider<T> schemaProvider,
            SubmergeSchemaUpgradeHelper<T> schemaUpgradeHelper,
            SharedDataStoreHandle<T> handle) {
        mName = name;
        mLock = lock;
        mDeviceNodeIdProvider = deviceNodeIdProvider;
        mStorage = storage;
        mFrameworkStatsLogProxy = frameworkStatsLogProxy;
        mTimestampProvider = timestampProvider;
        mConverter = converter;
        mSchemaProvider = schemaProvider;
        mSchemaUpgradeHelper = schemaUpgradeHelper;
        mHandle = handle;
    }

    private void broadcastUpdateMessage(String docId, byte[] updateMessage) {
        Log.i(TAG, "Broadcasting update message for " + getDebugDocIdentityString(docId));
        synchronized (mLock) {
            broadcastMessageForDoc(
                    docId, encodeDocUpdateMessage(docId, updateMessage, requireNonNull(mNodeId)));
        }
    }

    @SuppressWarnings("MustBeClosedChecker")
    @Override
    public ListenableFuture<Boolean> init(Network network) {
        synchronized (mLock) {
            try {
                if (isOpen()) {
                    // Duplicate call.
                    return requireNonNull(mInitFuture);
                }
                requireStateLocked(STATE_UNINITIALIZED);
                if (!mHandle.lock(this)) {
                    throw new IllegalStateException("Data store \"" + mName + "\" is locked");
                }
                mState = STATE_UPGRADING;
                mNetwork = network;
                mNodeId = mDeviceNodeIdProvider.getOrCreateNodeIdForDataStore(mName);
                mSubmergeDataStore = openDocumentDataStore(mNodeId, mTimestampProvider);
                // Init the storage and upgrade the data store asynchronously.
                mInitFuture =
                        FluentFuture.from(mStorage.submitToIoThread(this::doInit))
                                .catchingAsync(
                                        Exception.class,
                                        e -> {
                                            // If init fails, close the data store and rethrow the
                                            // error.
                                            Log.e(TAG, "Failed to init data store", e);
                                            return FluentFuture.from(close())
                                                    .catchingAsync(
                                                            Exception.class,
                                                            unused ->
                                                                    Futures.immediateFailedFuture(
                                                                            e),
                                                            directExecutor())
                                                    .transformAsync(
                                                            unused ->
                                                                    Futures.immediateFailedFuture(
                                                                            e),
                                                            directExecutor());
                                        },
                                        directExecutor());
                return mInitFuture;
            } catch (IllegalStateException e) {
                return Futures.immediateFailedFuture(e);
            }
        }
    }

    private boolean doInit() throws Exception {
        Trace.beginSection("SubmergeSharedDataStore.doInit");
        try {
            synchronized (mLock) {
                requireStateLocked(STATE_UPGRADING);
                mNetwork.registerListener(mStorage.getIoThreadExecutor(), this);
            }
            mStorage.open();
            upgrade();
            return true;
        } finally {
            Trace.endSection();
        }
    }

    private void upgrade() throws Exception {
        String nodeId;
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING);
            nodeId = requireNonNull(mNodeId);
        }
        for (DocumentSchemaInfo schema : mSchemaProvider.getAllDocumentSchema()) {
            String docId = schema.getDocId();
            // Create a new submerge data store with timestamp == 0 so that changes made
            // during data migration are less likely to override concurrent change from
            // remote devices.
            try (DataStore<T> documentDataStore =
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
                // Apply changes in transaction so that database can rollback in case upgrade fails.
                mStorage.transact(
                        s -> {
                            // Step 1: upgrade schema data store, and retrieve an update message.
                            byte[] schemaUpdateBytes =
                                    mSchemaUpgradeHelper.upgradeDocumentSchema(schema);

                            // Step 2: merge the schema change.
                            Log.i(
                                    TAG,
                                    "Merging schema update into "
                                            + getDebugDocIdentityString(docId));
                            try {
                                document.mergeLocalUpdate(schemaUpdateBytes);
                            } catch (Exception e) {
                                // Convert merge exception to IllegalSchemaChangeException.
                                throw new IllegalSchemaChangeException(e);
                            }

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
            }
        }
        DataStore<T> submergeDataStore;
        String myNodeId;
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING);
            mState = STATE_OPEN;
            submergeDataStore = requireNonNull(mSubmergeDataStore);
            myNodeId = requireNonNull(mNodeId);
        }
        Log.i(TAG, "Broadcasting all versions for " + mName + ".");
        broadcastAllVersions(submergeDataStore, myNodeId);
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
            String nodeId, DataStore<T> documentDataStore, String docId) throws IOException {
        DocumentMetadata metadata = loadDocMetadata(docId);
        return new SubmergeDocument<>(
                mName, docId, documentDataStore.newDocumentTransaction(docId), nodeId, metadata);
    }

    private void broadcastAllVersions(DataStore<T> submergeDataStore, String senderNodeId) {
        // Sending version vector to remote device will trigger a delta update.
        for (DocumentSchemaInfo schema : mSchemaProvider.getAllDocumentSchema()) {
            broadcastVersion(submergeDataStore, schema.getDocId(), senderNodeId);
        }
    }

    private void broadcastVersion(
            DataStore<T> submergeDataStore, String docId, String senderNodeId) {
        try (VersionVector version = submergeDataStore.getDocumentVersion(docId)) {
            broadcastMessageForDoc(
                    docId, encodeVersionVectorMessage(docId, version.toByteArray(), senderNodeId));
        }
    }

    private void broadcastMessageForDoc(String docId, byte[] message) {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_OPEN);
            if (mDocBroadcastMessageIds.containsKey(docId)) {
                mNetwork.cancelMessage(mDocBroadcastMessageIds.get(docId));
            }
            long msgId =
                    mNetwork.broadcastMessage(
                            message,
                            Network.MESSAGE_FLAG_STICKY
                                    | Network.MESSAGE_FLAG_NEARBY_ONLY
                                    | Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
            mDocBroadcastMessageIds.put(docId, msgId);
        }
    }

    @Override
    public String getLocalDeviceNodeId() {
        String nodeId;
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_OPEN);
            nodeId = requireNonNull(mNodeId);
        }
        return nodeId;
    }

    @Override
    public <U> ListenableFuture<U> transact(String docId, TransactionApplier<T, U> applier) {
        synchronized (mLock) {
            try {
                requireStateLocked(STATE_UPGRADING, STATE_OPEN);
                if (DEBUG) {
                    Log.d(TAG, "Starting a transaction on " + getDebugDocIdentityString(docId));
                }
                return mStorage.submitToIoThread(() -> doTransact(docId, applier));
            } catch (IllegalStateException e) {
                return Futures.immediateFailedFuture(e);
            }
        }
    }

    /** Called on IO thread for transaction. */
    private <U> U doTransact(String docId, TransactionApplier<T, U> applier) throws Exception {
        Trace.beginSection("SubmergeSharedDataStore.doTransact");
        try {
            return doTransactNoTracing(docId, applier);
        } finally {
            Trace.endSection();
        }
    }

    private <U> U doTransactNoTracing(String docId, TransactionApplier<T, U> applier)
            throws Exception {
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
            if (DEBUG) {
                Log.d(TAG, "Transaction committed for " + getDebugDocIdentityString(docId));
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to commit transaction for " + getDebugDocIdentityString(docId), e);
            throw e;
        }
    }

    private DocumentMetadata loadDocMetadata(String docId) throws IOException {
        byte[] metadataBytes = mStorage.getMetadata(docId);
        if (metadataBytes == null) {
            return new DocumentMetadata(0, new ArrayList<>());
        }
        return DocumentMetadata.parseFrom(metadataBytes);
    }

    /** Called on IO thread when a network message is received. */
    @Override
    public void onNetworkMessage(int associationId, byte[] message) {
        Trace.beginSection("SubmergeSharedDataStore.onNetworkMessage");
        try {
            onNetworkMessageNoTracing(associationId, message);
        } finally {
            Trace.endSection();
        }
    }

    private void onNetworkMessageNoTracing(int associationId, byte[] message) {
        Log.i(TAG, "onNetworkMessage: associationId = " + associationId);
        DataStore<T> submergeDataStore;
        String myNodeId;
        int myFeature;
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
            myNodeId = requireNonNull(mNodeId);
            myFeature = requireNonNull(mNetwork).getFeature();
        }
        NetworkMessage networkMessage;
        try {
            networkMessage = NetworkMessage.parseFrom(message);
        } catch (IOException e) {
            Log.e(TAG, "onNetworkUpdate: failed to parse network message", e);
            return;
        }
        if (!networkMessage.receiverNodeId().isEmpty()
                && !myNodeId.equals(networkMessage.receiverNodeId())) {
            // The message is targeting another node. Ignore.
            return;
        }
        String docId = networkMessage.docId();
        byte[] responseUpdate = null;
        byte[] responseVersionVector = null;
        boolean broadcastedVersion = false;
        if (networkMessage.docUpdate().length > 0) {
            Log.d(TAG, "Received network update for " + getDebugDocIdentityString(docId));
            boolean remoteChangeSynced = false;
            try {
                List<String> changedPaths =
                        doTransact(
                                docId,
                                doc ->
                                        ((SubmergeDocument<T>) doc)
                                                .mergeNetworkUpdate(networkMessage.docUpdate()));
                if (!changedPaths.isEmpty()) {
                    mRemoteChangeListeners.forEach(record -> record.onRemoteChange(changedPaths));
                    remoteChangeSynced = true;
                    // Broadcast latest version.
                    broadcastVersion(submergeDataStore, docId, myNodeId);
                    broadcastedVersion = true;
                }
            } catch (OlderBaseNeededException e) {
                Log.i(
                        TAG,
                        "onNetworkUpdate: need a older base to commit network update for "
                                + getDebugDocIdentityString(docId));
                // Send the needed version vector to request another update message with proper
                // version base.
                responseVersionVector = e.getBaseVersionNeeded().toByteArray();
            } catch (Exception e) {
                Log.e(
                        TAG,
                        "onNetworkUpdate: failed to commit network update for "
                                + getDebugDocIdentityString(docId),
                        e);
                return;
            } finally {
                mFrameworkStatsLogProxy.logSyncEvent(
                        getRemoteChangeSyncEvent(/* success= */ remoteChangeSynced), myFeature);
            }
        }
        if (networkMessage.docVersion().length > 0) {
            Log.d(TAG, "Received version vector for " + getDebugDocIdentityString(docId));
            try (VersionVector otherVersion =
                            VersionVector.fromByteArray(networkMessage.docVersion());
                    VersionVector myVersion = submergeDataStore.getDocumentVersion(docId)) {
                if (isPartiallyOlder(otherVersion, myVersion)) {
                    responseUpdate = submergeDataStore.calculateDelta(docId, otherVersion);
                }
                if (responseVersionVector == null
                        && isPartiallyOlder(myVersion, otherVersion)
                        && !broadcastedVersion) {
                    responseVersionVector = myVersion.toByteArray();
                }
            }
        }
        if (responseUpdate != null || responseVersionVector != null) {
            synchronized (mLock) {
                if (mState != STATE_OPEN) {
                    return;
                }
                Log.i(
                        TAG,
                        "onNetworkMessage: sending response to association "
                                + associationId
                                + " for "
                                + getDebugDocIdentityString(docId)
                                + ". Has doc: "
                                + (responseUpdate != null)
                                + ". Has version: "
                                + (responseVersionVector != null)
                                + ".");
                Map<String, Long> unicastMessages =
                        mDocUnicastMessageIds.computeIfAbsent(docId, k -> new HashMap<>());
                String targetNode = networkMessage.senderNodeId();
                Long existingMessage = unicastMessages.get(targetNode);
                if (existingMessage != null) {
                    mNetwork.cancelMessage(existingMessage);
                }
                long msgId =
                        mNetwork.unicastMessage(
                                associationId,
                                encodeMessage(
                                        docId,
                                        responseUpdate,
                                        responseVersionVector,
                                        myNodeId,
                                        targetNode),
                                Network.MESSAGE_FLAG_NEARBY_ONLY
                                        | Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT
                                        | Network.MESSAGE_FLAG_STICKY);
                unicastMessages.put(targetNode, msgId);
            }
        }
    }

    private static int getRemoteChangeSyncEvent(boolean success) {
        return success
                ? CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_UPDATES_LOCAL_STATE
                : CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_SYNC_NOT_CHANGING_LOCAL_STATE;
    }

    private static boolean isPartiallyOlder(VersionVector a, VersionVector b) {
        int comp = a.compare(b);
        return comp == VersionVector.OLDER || comp == VersionVector.CONCURRENT;
    }

    private byte[] encodeVersionVectorMessage(String docId, byte[] version, String senderNodeId) {
        return encodeMessage(
                docId, /* docUpdate= */ null, version, senderNodeId, /* receiverNodeId= */ null);
    }

    private byte[] encodeDocUpdateMessage(String docId, byte[] docUpdate, String senderNodeId) {
        return encodeMessage(
                docId, docUpdate, /* version= */ null, senderNodeId, /* receiverNodeId= */ null);
    }

    private byte[] encodeMessage(
            String docId,
            @Nullable byte[] docUpdate,
            @Nullable byte[] version,
            String senderNodeId,
            @Nullable String receiverNodeId) {
        if (docUpdate == null && version == null) {
            throw new IllegalArgumentException("Must have a docUpdate or version.");
        }
        return new NetworkMessage(
                        docId,
                        senderNodeId,
                        receiverNodeId == null ? "" : receiverNodeId,
                        version == null ? new byte[0] : version,
                        docUpdate == null ? new byte[0] : docUpdate)
                .toByteArray();
    }

    @Override
    public void registerOnRemoteChangeListener(Executor executor, OnRemoteChangeListener listener) {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_OPEN);
            mRemoteChangeListeners.add(new OnRemoteChangeListenerRecord(executor, listener));
        }
    }

    @Override
    public void unregisterOnRemoteChangeListener(OnRemoteChangeListener listener) {
        synchronized (mLock) {
            requireStateLocked(STATE_UPGRADING, STATE_OPEN);
            mRemoteChangeListeners.removeIf(record -> record.mListener == listener);
        }
    }

    @Override
    public ListenableFuture<Boolean> close() {
        synchronized (mLock) {
            if (mState == STATE_CLOSED) {
                // Duplicate call.
                return requireNonNull(mCloseFuture);
            }
            Log.i(TAG, "closing data store \"" + mName + "\".");
            mState = STATE_CLOSED;
            mRemoteChangeListeners.clear();
            if (mNetwork != null) {
                mNetwork.unregisterListener(this);
                mDocBroadcastMessageIds.values().forEach(mNetwork::cancelMessage);
                mDocUnicastMessageIds
                        .values()
                        .forEach(
                                docMessages ->
                                        docMessages.values().forEach(mNetwork::cancelMessage));
                mNetwork = null;
            }
            mDocBroadcastMessageIds.clear();
            mDocUnicastMessageIds.clear();
            mNodeId = null;
            if (mSubmergeDataStore != null) {
                final DataStore<T> submergeDataStore = mSubmergeDataStore;
                mSubmergeDataStore = null;
                mCloseFuture =
                        mStorage.submitToIoThread(
                                () -> {
                                    submergeDataStore.close();
                                    mStorage.close();
                                    mHandle.unlock(this);
                                    return true;
                                });
            } else {
                mHandle.unlock(this);
                mCloseFuture = Futures.immediateFuture(true);
            }
            return mCloseFuture;
        }
    }

    @Override
    public boolean isOpen() {
        synchronized (mLock) {
            return mState == STATE_UPGRADING || mState == STATE_OPEN;
        }
    }

    @GuardedBy("mLock")
    private void requireStateLocked(@State int... states) {
        for (int state : states) {
            if (mState == state) {
                return;
            }
        }
        throw new IllegalStateException(
                "Data store "
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

    @Override
    public String toString() {
        synchronized (mLock) {
            return "SharedDataStore{name="
                    + mName
                    + ", state="
                    + stateToString(mState)
                    + ", docs="
                    + mSchemaProvider.getAllDocumentSchema().stream()
                            .map(DocumentSchemaInfo::getDocId)
                            .toList()
                    + "}";
        }
    }

    private static String stateToString(@State int state) {
        return switch (state) {
            case STATE_UNINITIALIZED -> "UNINITIALIZED";
            case STATE_UPGRADING -> "UPGRADING";
            case STATE_OPEN -> "OPEN";
            case STATE_CLOSED -> "CLOSED";
            default -> "UNKNOWN(" + state + ")";
        };
    }

    private record OnRemoteChangeListenerRecord(
            Executor mExecutor, OnRemoteChangeListener mListener) {

        void onRemoteChange(List<String> paths) {
            mExecutor.execute(() -> mListener.onRemoteChange(paths));
        }
    }
}
