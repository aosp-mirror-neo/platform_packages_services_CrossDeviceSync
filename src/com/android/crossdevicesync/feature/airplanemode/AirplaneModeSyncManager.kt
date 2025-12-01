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
package com.android.crossdevicesync.feature.airplanemode

import android.companion.AssociationRequest
import android.util.IndentingPrintWriter
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.crossdevicesync.dagger.qualifier.IsWatch
import com.android.crossdevicesync.dagger.qualifier.Main
import com.android.crossdevicesync.data.DataStoreInitHelper.getInitializedDataStore
import com.android.crossdevicesync.data.DocumentSchemaInfo
import com.android.crossdevicesync.data.RecordType.TYPE_REGISTER
import com.android.crossdevicesync.data.SchemaProvider
import com.android.crossdevicesync.data.SharedDataStore
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument
import com.android.crossdevicesync.data.SharedDataStore.OnRemoteChangeListener
import com.android.crossdevicesync.data.SharedDataStoreFactory
import com.android.crossdevicesync.data.StringConverter
import com.android.crossdevicesync.feature.FeatureManager
import com.android.crossdevicesync.network.NetworkManager
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton
import com.android.server.connectivity.Flags
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.inject.Inject
import javax.inject.Provider

/** Airplane mode sync feature manager. */
@SyncServiceSingleton
class AirplaneModeSyncManager
@Inject
internal constructor(
    private val mNetworkManager: NetworkManager,
    private val mSharedDataStoreFactory: SharedDataStoreFactory,
    private val mStringConverter: StringConverter,
    private val mAirplaneModeController: AirplaneModeController,
    @param:IsWatch private val mIsWatch: Provider<Boolean>,
    @param:Main private val mExecutor: Executor,
) : FeatureManager {
    @Volatile private lateinit var mDataStore: SharedDataStore<String>
    private val mAirplaneModeState = AtomicBoolean(false)

    private val mRemoteAirplaneModeChangeListener = OnRemoteChangeListener { paths: List<String> ->
        // TODO(b/427295302): Also check the Sync Toggle Setting of current device.
        if (paths.contains(APM_ENABLED_PATH)) {
            Log.d(TAG, "Remote APM changed")
            mDataStore.transact(DOC_ID) { doc ->
                doc.getRecord(APM_ENABLED_PATH)?.let { record ->
                    val apmEnabled = record.get() == AIRPLANE_MODE_ON
                    Log.d(TAG, "Remote APM state = $apmEnabled. Updating local setting")
                    mAirplaneModeState.set(apmEnabled)
                    mAirplaneModeController.updateAirplaneModeState(apmEnabled)
                }
            }
        }
    }

    private val mLocalAirplaneModeChangedListener =
        object : AirplaneModeController.Listener {
            override fun onAirplaneModeChanged(enabled: Boolean) {
                Log.d(TAG, "Local APM state changed to $enabled")
                // TODO(b/427295302): Also check the Sync Toggle Setting of current device.
                if (mAirplaneModeState.compareAndSet(!enabled, enabled)) {
                    Log.d(TAG, "Setting APM state to SharedDataStore")
                    Futures.addCallback(
                        mDataStore.transact(DOC_ID) { doc ->
                            doc.putData(
                                APM_ENABLED_PATH,
                                if (enabled) AIRPLANE_MODE_ON else AIRPLANE_MODE_OFF,
                            )
                        },
                        object : FutureCallback<Unit> {
                            override fun onSuccess(result: Unit) {
                                Log.d(TAG, "SharedDataStore update successful")
                            }

                            override fun onFailure(t: Throwable) {
                                Log.e(TAG, "Error updating SharedDataStore", t)
                            }
                        },
                        mExecutor,
                    )
                }
            }
        }

    override fun init() {
        if (!Flags.syncAirplaneModeWithWatches()) {
            return
        }
        val apmEnabled = mAirplaneModeController.getAirplaneModeState()
        Log.d(TAG, "Initializing with airplane mode state: $apmEnabled")
        mAirplaneModeState.set(apmEnabled)
        // TODO(b/450019814): Set Local Metadata and check remote Metadata
        val network =
            mNetworkManager.createNetwork(NETWORK_ID)
            /* remoteDeviceCondition= */ {
                // Whether this device is a watch, or the remote device is a watch.
                mIsWatch.get() || it.deviceProfile == AssociationRequest.DEVICE_PROFILE_WATCH
            }
        initDataStore({
            mSharedDataStoreFactory.create(
                DATA_STORE_NAME,
                mStringConverter,
                getSchemaProvider(),
                network,
            )
        })
    }

    private fun initDataStore(dataStoreSupplier: Supplier<SharedDataStore<String>>) {
        FluentFuture.from(getInitializedDataStore(dataStoreSupplier, mExecutor))
            .addCallback(
                object : FutureCallback<SharedDataStore<String>> {
                    override fun onSuccess(dataStore: SharedDataStore<String>) {
                        Log.i(TAG, "APM Data Store init success")
                        mDataStore = dataStore
                        mAirplaneModeController.registerAirplaneModeChangedListener(
                            mLocalAirplaneModeChangedListener
                        )
                        mDataStore.registerOnRemoteChangeListener(
                            mExecutor,
                            mRemoteAirplaneModeChangeListener,
                        )
                    }

                    override fun onFailure(t: Throwable) {
                        Log.e(TAG, "APM Data Store init error", t)
                    }
                },
                mExecutor,
            )
    }

    override fun destroy() {
        if (!Flags.syncAirplaneModeWithWatches()) {
            return
        }
        if (::mDataStore.isInitialized && mDataStore.isOpen) {
            mDataStore.unregisterOnRemoteChangeListener(mRemoteAirplaneModeChangeListener)
            mDataStore.close()
        }
        mAirplaneModeController.unregisterAirplaneModeChangedListener(
            mLocalAirplaneModeChangedListener
        )
    }

    private fun getSchemaProvider() =
        object : SchemaProvider<String> {
            override fun getAllDocumentSchema(): List<DocumentSchemaInfo> =
                DOCUMENT_SCHEMA_INFO_LIST

            override fun migrateDocument(document: MutableDocument<String>) {
                document.putData(
                    APM_ENABLED_PATH,
                    if (mAirplaneModeState.get()) AIRPLANE_MODE_ON else AIRPLANE_MODE_OFF,
                )
            }
        }

    override fun dump(pw: IndentingPrintWriter) {
        if (!Flags.syncAirplaneModeWithWatches()) {
            return
        }
        pw.println("AirplaneModeSyncManager:")
        pw.increaseIndent()
        pw.println("Data Store State = " + if (mDataStore.isOpen) "Opened" else "Closed")
        pw.println("Airplane Mode State = ${mAirplaneModeState.get()}")
        pw.decreaseIndent()
    }

    @VisibleForTesting
    internal companion object {
        const val TAG = "AirplaneModeSyncManager"
        const val DATA_STORE_NAME = "apm_sync_data_store" // DO NOT CHANGE!
        const val DOC_ID = "apm_sync_doc"
        const val DOC_VERSION = 1
        const val APM_ENABLED_PATH = "/apm/enabled"
        const val APM_ENABLED_VERSION = 1
        const val AIRPLANE_MODE_ON = "1"
        const val AIRPLANE_MODE_OFF = "0"
        const val NETWORK_ID = "apm_sync_network"

        val DOCUMENT_SCHEMA_INFO_LIST =
            listOf(
                DocumentSchemaInfo.builder()
                    .setDocId(DOC_ID)
                    .setVersion(DOC_VERSION)
                    .putPathSchema(APM_ENABLED_PATH, TYPE_REGISTER, APM_ENABLED_VERSION)
                    .build()
            )
    }
}
