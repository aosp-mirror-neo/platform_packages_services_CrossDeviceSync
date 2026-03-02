/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.crossdevicesync.lib;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.android.crossdevicesync.data.Docs;
import com.android.crossdevicesync.data.SharedDataStore;
import com.android.crossdevicesync.data.SharedDataStore.OnRemoteChangeListener;
import com.android.crossdevicesync.data.SharedDataStore.TransactionApplier;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.StringConverter;
import com.android.crossdevicesync.feature.mode.ContextualModeSyncController;
import com.android.crossdevicesync.feature.mode.ModeSyncDocs;
import com.android.crossdevicesync.feature.mode.ModeSyncMetadata;
import com.android.crossdevicesync.lib.api.ContextualModeSyncDocument;
import com.android.crossdevicesync.metadata.MetadataPublisher;
import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.network.NetworkManager.Network;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Implementation of {@link ContextualModeSyncDocument}. */
public class ContextualModeSyncDocumentImpl
        implements ContextualModeSyncDocument, OnRemoteChangeListener {
    private static final String TAG = "ModeSyncDoc";
    private static final String DATASTORE_NAME = "contextual_mode_sync_data_store";

    private final SharedDataStoreHandle<String> mHandle;
    private final Network mNetwork;
    private final SharedDataStore<String> mDataStore;
    private final List<Pair<Executor, Consumer<Boolean>>> mListeners = new CopyOnWriteArrayList<>();

    /**
     * Constructs a new {@link ContextualModeSyncDocumentImpl}.
     *
     * @param context the application context.
     * @param factory the factory to create shared data store handles.
     * @param networkManager the network manager to create sync networks.
     * @param converter the string converter for the data store.
     */
    public ContextualModeSyncDocumentImpl(
            Context context,
            SharedDataStoreHandle.Factory factory,
            NetworkManager networkManager,
            StringConverter converter,
            MetadataPublisher metadataPublisher) {
        mHandle =
                factory.create(
                        DATASTORE_NAME,
                        converter,
                        ModeSyncDocs.getSchemaProvider(
                                doc -> {
                                    if (Docs.getString(
                                                    doc,
                                                    ModeSyncDocs.getManualDoNotDisturbStatePath())
                                            == null) {
                                        ModeSyncDocs.putModeStateActive(
                                                doc,
                                                ModeSyncDocs.getManualDoNotDisturbStatePath(),
                                                false);
                                    }
                                }));
        mNetwork =
                networkManager.createNetworkForUser(
                        context.getUser().getIdentifier(),
                        ContextualModeSyncController.NETWORK_ID,
                        /* feature= */ 0,
                        device -> true);
        mDataStore = mHandle.openDataStore(mNetwork).join();
        mDataStore.registerOnRemoteChangeListener(context.getMainExecutor(), this);

        metadataPublisher.putBooleanMetaData(
                context.getUserId(),
                ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED,
                true);
        metadataPublisher.putBooleanMetaData(
                context.getUserId(), ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_ENABLED, true);
        metadataPublisher.putIntMetaData(
                context.getUserId(),
                ModeSyncMetadata.METADATA_CONTEXTUAL_MODES_DOC_SCHEMA_VERSION,
                ModeSyncDocs.CURRENT_SCHEMA_VERSION);
    }

    private <R> R transactSync(TransactionApplier<String, R> applier) {
        return mDataStore.transact(ModeSyncDocs.CONTEXTUAL_MODES_DOC_ID, applier).join();
    }

    @Override
    public boolean isDndEnabled() {
        return transactSync(
                doc ->
                        ModeSyncDocs.isModeStateActive(
                                doc, ModeSyncDocs.getManualDoNotDisturbStatePath()));
    }

    @Override
    public void setDndEnabled(boolean enabled) {
        transactSync(
                doc -> {
                    ModeSyncDocs.putModeStateActive(
                            doc, ModeSyncDocs.getManualDoNotDisturbStatePath(), enabled);
                    return null;
                });
    }

    @Override
    public void addOnRemoteDndChangedListener(Executor executor, Consumer<Boolean> listener) {
        mListeners.add(Pair.create(executor, listener));
    }

    @Override
    public void removeOnRemoteDndChangedListener(Consumer<Boolean> listener) {
        mListeners.removeIf(pair -> pair.second == listener);
    }

    @Override
    public void onRemoteChange(List<String> paths) {
        if (paths.contains(ModeSyncDocs.getManualDoNotDisturbStatePath())) {
            boolean enabled = isDndEnabled();
            Log.d(TAG, "Remote DND state changed: " + enabled);
            mListeners.forEach(
                    listener -> listener.first.execute(() -> listener.second.accept(enabled)));
        }
    }

    @Override
    public void close() {
        mListeners.clear();
        mDataStore.unregisterOnRemoteChangeListener(this);
        mNetwork.destroy();
        mHandle.destroy();
    }
}
