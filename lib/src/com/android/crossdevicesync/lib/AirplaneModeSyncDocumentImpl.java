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

import static android.provider.Settings.Global.AIRPLANE_MODE_SYNC;

import android.content.Context;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import com.android.crossdevicesync.data.Docs;
import com.android.crossdevicesync.data.DocumentSchemaInfo;
import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStore;
import com.android.crossdevicesync.data.SharedDataStore.OnRemoteChangeListener;
import com.android.crossdevicesync.data.SharedDataStore.TransactionApplier;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.StringConverter;
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager;
import com.android.crossdevicesync.lib.api.AirplaneModeSyncDocument;
import com.android.crossdevicesync.lib.common.FakeCompanionDeviceManagerProxy;
import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.network.NetworkManager.Network;

import com.google.common.util.concurrent.Futures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Implementation of {@link AirplaneModeSyncDocument}. */
public class AirplaneModeSyncDocumentImpl
        implements AirplaneModeSyncDocument, OnRemoteChangeListener {
    private static final String TAG = "ApmSyncDoc";
    private static final String DATA_STORE_NAME = "apm_sync_data_store";
    private static final String AIRPLANE_MODE_ON = "1";
    private static final String AIRPLANE_MODE_OFF = "0";

    private final SharedDataStoreHandle<String> mHandle;
    private final Network mNetwork;
    private final SharedDataStore<String> mDataStore;
    private final List<Pair<Executor, Consumer<Boolean>>> mListeners = new CopyOnWriteArrayList<>();

    public AirplaneModeSyncDocumentImpl(
            Context context,
            SharedDataStoreHandle.Factory factory,
            NetworkManager networkManager,
            StringConverter converter,
            FakeCompanionDeviceManagerProxy cdm) {
        mHandle =
                factory.create(
                        DATA_STORE_NAME,
                        converter,
                        new SchemaProvider<>() {
                            @Override
                            public List<DocumentSchemaInfo> getAllDocumentSchema() {
                                return AirplaneModeSyncManager.DOCUMENT_SCHEMA_INFO_LIST;
                            }

                            @Override
                            public void migrateDocument(
                                    SharedDataStore.MutableDocument<String> document) {
                                document.putData(
                                        AirplaneModeSyncManager.APM_ENABLED_PATH,
                                        AIRPLANE_MODE_OFF);
                            }
                        });
        mNetwork =
                networkManager.createNetworkForUser(
                        context.getUser().getIdentifier(),
                        AirplaneModeSyncManager.NETWORK_ID,
                        /* feature= */ 0,
                        device -> true);
        mDataStore = Futures.getUnchecked(mHandle.openDataStore(mNetwork));
        mDataStore.registerOnRemoteChangeListener(context.getMainExecutor(), this);

        cdm.setLocalMetadata(UserHandle.USER_ALL, AirplaneModeSyncManager.APM_SYNC_SUPPORTED, true);
        cdm.setLocalMetadata(UserHandle.USER_ALL, AIRPLANE_MODE_SYNC, true);
    }

    private <R> R transactSync(TransactionApplier<String, R> applier) {
        return Futures.getUnchecked(mDataStore.transact(AirplaneModeSyncManager.DOC_ID, applier));
    }

    @Override
    public boolean isAirplaneModeEnabled() {
        return transactSync(
                doc ->
                        AIRPLANE_MODE_ON.equals(
                                Docs.getString(doc, AirplaneModeSyncManager.APM_ENABLED_PATH)));
    }

    @Override
    public void setAirplaneModeEnabled(boolean enabled) {
        transactSync(
                doc -> {
                    Docs.putString(
                            doc,
                            AirplaneModeSyncManager.APM_ENABLED_PATH,
                            enabled ? AIRPLANE_MODE_ON : AIRPLANE_MODE_OFF);
                    return null;
                });
    }

    @Override
    public void addOnRemoteAirplaneModeChangedListener(
            Executor executor, Consumer<Boolean> listener) {
        mListeners.add(Pair.create(executor, listener));
    }

    @Override
    public void removeOnRemoteAirplaneModeChangedListener(Consumer<Boolean> listener) {
        mListeners.removeIf(pair -> pair.second == listener);
    }

    @Override
    public void onRemoteChange(List<String> paths) {
        if (paths.contains(AirplaneModeSyncManager.APM_ENABLED_PATH)) {
            boolean enabled = isAirplaneModeEnabled();
            Log.d(TAG, "Remote APM state changed: " + enabled);
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
