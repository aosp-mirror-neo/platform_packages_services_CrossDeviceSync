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

import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC
import android.companion.CompanionDeviceManager.FLAG_AIRPLANE_MODE
import android.content.Context
import android.os.UserHandle
import android.os.UserHandle.USER_ALL
import android.provider.Settings.Global.AIRPLANE_MODE_SYNC
import android.util.IndentingPrintWriter
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.android.crossdevicesync.common.DeviceUtils
import com.android.crossdevicesync.dagger.qualifier.Main
import com.android.crossdevicesync.data.DocumentSchemaInfo
import com.android.crossdevicesync.data.RecordType.TYPE_REGISTER
import com.android.crossdevicesync.data.SchemaProvider
import com.android.crossdevicesync.data.SharedDataStore
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument
import com.android.crossdevicesync.data.SharedDataStore.OnRemoteChangeListener
import com.android.crossdevicesync.data.SharedDataStoreHandleFactory
import com.android.crossdevicesync.data.StringConverter
import com.android.crossdevicesync.feature.FeatureManager
import com.android.crossdevicesync.metadata.MetadataPublisher
import com.android.crossdevicesync.network.NetworkManager
import com.android.crossdevicesync.notification.NotificationHelper
import com.android.crossdevicesync.notification.NotificationReason.Companion.AIRPLANE_MODE_SYNCED
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton
import com.android.internal.R
import com.android.server.connectivity.Flags
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Airplane mode sync feature manager. */
@SyncServiceSingleton
class AirplaneModeSyncManager
@Inject
internal constructor(
    context: Context,
    private val mNetworkManager: NetworkManager,
    private val mAirplaneModeController: AirplaneModeController,
    private val mMetadataPublisher: MetadataPublisher,
    private val mNotificationHelper: NotificationHelper,
    private val mDeviceUtils: DeviceUtils,
    @param:Main private val mExecutor: Executor,
    stringConverter: StringConverter,
    sharedDataStoreHandleFactory: SharedDataStoreHandleFactory,
) : FeatureManager, OnRemoteChangeListener, AirplaneModeController.Listener, DeviceUtils.Listener {
    private val mLock = Any()
    private val mSupportedRes: Boolean by lazy {
        context.resources.getBoolean(R.bool.config_supportAirplaneModeSync)
    }
    private val mSharedDataStoreHandle =
        sharedDataStoreHandleFactory.create(DATA_STORE_NAME, stringConverter, getSchemaProvider())
    @GuardedBy("mLock") private var mDataStore: SharedDataStore<String>? = null
    @GuardedBy("mLock") private var mNetwork: NetworkManager.Network? = null
    @GuardedBy("mLock") private var mState = STATE_INACTIVE
    @GuardedBy("mLock") private var mStarted = false
    @GuardedBy("mLock") private var mAirplaneModeState = false
    @GuardedBy("mLock") private var mIsKidsWatch = false
    private val mSyncEnabled = AtomicBoolean(false)

    override fun init() {
        if (!Flags.syncAirplaneModeWithWatches()) {
            return
        }
        val apmEnabled = mAirplaneModeController.isAirplaneModeEnabled()
        val syncEnabled = mAirplaneModeController.isAirplaneModeSyncEnabled()
        val isKidsWatch = mDeviceUtils.isKidsWatch()
        logD("Init : apmEnabled=$apmEnabled, syncEnabled=$syncEnabled, isKidsWatch=$isKidsWatch")

        synchronized(mLock) {
            mAirplaneModeState = apmEnabled
            mIsKidsWatch = isKidsWatch
            mStarted = true
            setApmSyncSupportedMetadataLocked()
            mAirplaneModeController.registerAirplaneModeChangedListener(this)
            mDeviceUtils.registerKidsWatchChangeListener(this)
            onAirplaneModeSyncEnabledStateChanged(syncEnabled)
            maybeUpdateDataStoreLocked()
        }
    }

    override fun onRemoteChange(paths: List<String>) {
        synchronized(mLock) {
            val localDataStore = mDataStore
            if (
                mState == STATE_ACTIVE && paths.contains(APM_ENABLED_PATH) && localDataStore != null
            ) {
                logD("Remote APM changed")
                localDataStore.transactLocked("Get remote state") { doc ->
                    synchronized(mLock) {
                        doc.getRecord(APM_ENABLED_PATH)?.let { record ->
                            val apmEnabled = record.get() == AIRPLANE_MODE_ON
                            logD("Remote APM state = $apmEnabled. Updating local setting")
                            mAirplaneModeState = apmEnabled
                            mAirplaneModeController.updateAirplaneModeState(apmEnabled)
                            if (apmEnabled) {
                                mNotificationHelper.maybeShowNotification(
                                    AIRPLANE_MODE_SYNCED,
                                    UserHandle.ALL,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAirplaneModeChanged(enabled: Boolean) {
        logD("Local APM state changed to $enabled")
        synchronized(mLock) {
            val localDataStore = mDataStore
            if (mAirplaneModeState != enabled) {
                mAirplaneModeState = enabled
                if (mState == STATE_ACTIVE && localDataStore != null) {
                    logD("Setting APM state to SharedDataStore")
                    localDataStore.transactLocked("Update local state") { doc ->
                        doc.putData(APM_ENABLED_PATH, enabled.toAirplaneModeState())
                    }
                }
            }
        }
    }

    override fun onKidsWatchChanged(isKidsWatch: Boolean) {
        synchronized(mLock) {
            mIsKidsWatch = isKidsWatch
            setApmSyncSupportedMetadataLocked()
            maybeUpdateDataStoreLocked()
        }
    }

    @GuardedBy("mLock")
    private fun setApmSyncSupportedMetadataLocked() {
        // TODO(b/469447704): Check if companion apps support APM Sync
        mMetadataPublisher.putBooleanMetaData(USER_ALL, APM_SYNC_SUPPORTED, isSyncSupportedLocked())
    }

    @GuardedBy("mLock") private fun isSyncSupportedLocked() = mSupportedRes && !mIsKidsWatch

    private fun SharedDataStore<String>.transactLocked(
        name: String,
        transaction: (MutableDocument<String>) -> Unit,
    ) {
        Futures.addCallback(
            transact(DOC_ID, transaction),
            object : FutureCallback<Unit> {
                override fun onSuccess(result: Unit?) {
                    logD("Transaction[$name] successful")
                }

                override fun onFailure(t: Throwable) {
                    Log.e(TAG, "Transaction[$name] failed", t)
                }
            },
            mExecutor,
        )
    }

    override fun onAirplaneModeSyncEnabledStateChanged(enabled: Boolean) {
        logD("Local APM sync enabled state changed to $enabled")
        mMetadataPublisher.putBooleanMetaData(USER_ALL, AIRPLANE_MODE_SYNC, enabled)
        synchronized(mLock) {
            if (mStarted && mSyncEnabled.compareAndSet(!enabled, enabled)) {
                mNetworkManager.invalidateNetworks()
            }
        }
    }

    @GuardedBy("mLock")
    private fun maybeUpdateDataStoreLocked() {
        if (!mStarted || !isSyncSupportedLocked()) {
            maybeCloseDataStoreLocked()
        } else {
            maybeOpenDataStoreLocked()
        }
    }

    @GuardedBy("mLock")
    private fun maybeOpenDataStoreLocked() {
        if (mState != STATE_INACTIVE) {
            logD("maybeEnableSync: ignored. Current state is " + stateToString(mState))
            return
        }

        mState = STATE_OPENING_DATA_STORE
        val network =
            mNetworkManager.createNetwork(NETWORK_ID)
            /* remoteDeviceCondition= */ {
                // Whether this device is a watch, or the remote device is a watch.
                (mDeviceUtils.isWatch || it.deviceProfile == DEVICE_PROFILE_WATCH) &&
                    // Whether the remote device supports sync and has the sync toggle enabled.
                    it.associationInfoCache.run {
                        getMetadata(FEATURE_CROSS_DEVICE_SYNC).run {
                            getBoolean(APM_SYNC_SUPPORTED) && getBoolean(AIRPLANE_MODE_SYNC)
                        } && (systemDataSyncFlags and FLAG_AIRPLANE_MODE) != 0
                    } &&
                    // Whether the sync toggle is enabled in this device
                    mSyncEnabled.get()
            }
        mNetwork = network

        Futures.addCallback(
            mSharedDataStoreHandle.openDataStore(network),
            object : FutureCallback<SharedDataStore<String>> {
                override fun onSuccess(dataStore: SharedDataStore<String>) {
                    onDataStoreInitialized(dataStore)
                }

                override fun onFailure(t: Throwable) {
                    onDataStoreFailedToInitialize(t)
                }
            },
            mExecutor,
        )
    }

    private fun onDataStoreInitialized(dataStore: SharedDataStore<String>) {
        synchronized(mLock) {
            if (mState != STATE_OPENING_DATA_STORE) {
                return
            }
            logD("APM Data Store init success")
            mState = STATE_ACTIVE
            mDataStore = dataStore
            dataStore.registerOnRemoteChangeListener(mExecutor, this)
            maybeUpdateDataStoreLocked()
        }
    }

    private fun onDataStoreFailedToInitialize(t: Throwable) {
        synchronized(mLock) {
            if (mState != STATE_OPENING_DATA_STORE) {
                return
            }
            Log.e(TAG, "APM Data Store init error", t)
            mState = STATE_INACTIVE
            mNetwork?.destroy()
            mNetwork = null
        }
    }

    @GuardedBy("mLock")
    private fun maybeCloseDataStoreLocked() {
        if (mState != STATE_ACTIVE) {
            logD("maybeDisableSync: ignored. Current state is " + stateToString(mState))
            return
        }
        mState = STATE_CLOSING_DATA_STORE
        mDataStore?.let {
            it.unregisterOnRemoteChangeListener(this)
            it.close().addListener({ onDataStoreClosed() }, mExecutor)
        }
    }

    private fun onDataStoreClosed() {
        synchronized(mLock) {
            if (mState != STATE_CLOSING_DATA_STORE) {
                return
            }
            mState = STATE_INACTIVE
            mDataStore = null
            mNetwork?.destroy()
            mNetwork = null
            maybeUpdateDataStoreLocked()
        }
    }

    override fun destroy() {
        if (!Flags.syncAirplaneModeWithWatches()) {
            return
        }
        synchronized(mLock) {
            if (!mStarted) {
                return
            }
            mStarted = false
            mState = STATE_INACTIVE
            mAirplaneModeController.unregisterAirplaneModeChangedListener(this)
            mDeviceUtils.unregisterKidsWatchChangeListener(this)
            mDataStore?.unregisterOnRemoteChangeListener(this)
            mDataStore = null
            mNetwork?.destroy()
            mNetwork = null
            mSharedDataStoreHandle.destroy()
        }
    }

    private fun getSchemaProvider() =
        object : SchemaProvider<String> {
            override fun getAllDocumentSchema(): List<DocumentSchemaInfo> =
                DOCUMENT_SCHEMA_INFO_LIST

            override fun migrateDocument(document: MutableDocument<String>) {
                synchronized(mLock) {
                    if (mState != STATE_OPENING_DATA_STORE) {
                        throw IllegalArgumentException(
                            "migrateDocument: illegal state " + stateToString(mState)
                        )
                    }
                    document.putData(APM_ENABLED_PATH, mAirplaneModeState.toAirplaneModeState())
                }
            }
        }

    override fun dump(pw: IndentingPrintWriter) {
        if (!Flags.syncAirplaneModeWithWatches()) {
            return
        }
        synchronized(mLock) {
            pw.println("AirplaneModeSyncManager:")
            pw.increaseIndent()
            pw.println("State = " + stateToString(mState))
            pw.println("Airplane Mode State = $mAirplaneModeState")
            pw.println("Airplane Mode Sync State = ${mSyncEnabled.get()}")
            pw.println("Is Kids Watch = $mIsKidsWatch")
            mNetwork?.let { pw.println("Network : $it") }
            mDataStore?.let { pw.println("Data Store : $it") }
            pw.decreaseIndent()
        }
    }

    private fun stateToString(state: Int): String {
        return when (state) {
            STATE_INACTIVE -> "INACTIVE"
            STATE_OPENING_DATA_STORE -> "OPENING_DATA_STORE"
            STATE_ACTIVE -> "ACTIVE"
            STATE_CLOSING_DATA_STORE -> "CLOSING_DATA_STORE"
            else -> "UNKNOWN($state)"
        }
    }

    @VisibleForTesting
    internal companion object {
        private const val TAG = "AirplaneModeSyncManager"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(log: String) {
            if (DEBUG) {
                Log.d(TAG, log)
            }
        }

        private const val DATA_STORE_NAME = "apm_sync_data_store" // DO NOT CHANGE!
        const val DOC_ID = "apm_sync_doc"
        private const val DOC_VERSION = 1
        const val APM_ENABLED_PATH = "/apm/enabled"
        private const val APM_ENABLED_VERSION = 1
        private const val AIRPLANE_MODE_ON = "1"
        private const val AIRPLANE_MODE_OFF = "0"
        const val NETWORK_ID = "apm_sync_network"

        private const val STATE_INACTIVE = 0
        private const val STATE_OPENING_DATA_STORE = 1
        private const val STATE_ACTIVE = 2
        private const val STATE_CLOSING_DATA_STORE = 3

        fun Boolean.toAirplaneModeState() = if (this) AIRPLANE_MODE_ON else AIRPLANE_MODE_OFF

        val DOCUMENT_SCHEMA_INFO_LIST =
            listOf(
                DocumentSchemaInfo.builder()
                    .setDocId(DOC_ID)
                    .setVersion(DOC_VERSION)
                    .putPathSchema(APM_ENABLED_PATH, TYPE_REGISTER, APM_ENABLED_VERSION)
                    .build()
            )

        // Shared metadata key across apps.
        const val APM_SYNC_SUPPORTED = "apm_sync_supported"
    }
}
