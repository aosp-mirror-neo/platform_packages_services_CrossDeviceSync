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
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.crossdevicesync.data.DocumentSchemaInfo
import com.android.crossdevicesync.data.SchemaProvider
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument
import com.android.crossdevicesync.data.SharedDataStore.Record
import com.android.crossdevicesync.data.SharedDataStoreComponentTestBase
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.Companion.AIRPLANE_MODE_OFF
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.Companion.AIRPLANE_MODE_ON
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.Companion.APM_ENABLED_PATH
import com.android.crossdevicesync.network.fake.FakeNetworkManager.FakeRemoteDevice
import com.android.crossdevicesync.services.SyncServiceComponentTestBase
import com.android.server.connectivity.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
class AirplaneModeSyncManagerTest : SyncServiceComponentTestBase() {
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    private lateinit var mRemoteDeviceContext: RemoteDeviceContext

    @Before
    override fun setUp() {
        super.setUp()
        mFakeNetworkManager.init()
        mRemoteDeviceContext = RemoteDeviceContext()
    }

    @After
    fun tearDown() {
        mAirplaneModeSyncManager.destroy()
        mRemoteDeviceContext.close()
        mFakeNetworkManager.destroy()
    }

    @Test
    fun testInit_isWatch_allRemoteDevicesEligibleForAirplaneModeSync() {
        mIsWatchProvider.isWatch = true
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        val fakeNetwork =
            mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, UserHandle.USER_ALL)

        val remoteDevice = FakeRemoteDevice(1, UserHandle.USER_ALL)
        assertThat(fakeNetwork.isDeviceEligibleForNetwork(remoteDevice)).isTrue()
    }

    @Test
    fun testInit_isNotWatch_onlyAllowsRemoteWatchesForAirplaneModeSyncNetwork() {
        mIsWatchProvider.isWatch = false
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        val fakeNetwork =
            mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, UserHandle.USER_ALL)

        val remoteDevice = FakeRemoteDevice(1, UserHandle.USER_ALL)
        assertThat(fakeNetwork.isDeviceEligibleForNetwork(remoteDevice)).isFalse()
        val watchDevice =
            FakeRemoteDevice(1, UserHandle.USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
        assertThat(fakeNetwork.isDeviceEligibleForNetwork(watchDevice)).isTrue()
    }

    @Test
    fun testInit_syncsCurrentState() {
        // Enable airplane mode locally before initialization.
        setAirplaneMode(true)

        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()
        flushNetworks()

        // Verify remote device has the initial state.
        transactRemoteDataStore { doc ->
            val record = doc.getRecord(APM_ENABLED_PATH) as Record<String>
            assertThat(record.get()).isEqualTo(AIRPLANE_MODE_ON)
        }
    }

    @Test
    fun testLocalChange_syncToRemote() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode locally after init, which should trigger the remote device sync.
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device receives the update.
        transactRemoteDataStore { doc ->
            val record = doc.getRecord(APM_ENABLED_PATH) as Record<String>
            assertThat(record.get()).isEqualTo(AIRPLANE_MODE_ON)
        }

        // Disable airplane mode locally.
        setAirplaneMode(false)
        flushNetworks()

        // Verify remote device receives the update.
        transactRemoteDataStore { doc ->
            val record = doc.getRecord(APM_ENABLED_PATH) as Record<String>
            assertThat(record.get()).isEqualTo(AIRPLANE_MODE_OFF)
        }

        // Enable airplane mode locally after destroy, which should not trigger the remote sync.
        mAirplaneModeSyncManager.destroy()
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device stays off.
        transactRemoteDataStore { doc ->
            val record = doc.getRecord(APM_ENABLED_PATH) as Record<String>
            assertThat(record.get()).isEqualTo(AIRPLANE_MODE_OFF)
        }
    }

    @Test
    fun testRemoteChange_syncToLocal() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        transactRemoteDataStore { doc -> doc.putData(APM_ENABLED_PATH, AIRPLANE_MODE_ON) }
        flushNetworks()

        // Verify local airplane mode is enabled.
        assertThat(isAirplaneModeOn()).isTrue()

        // Disable airplane mode on remote device.
        transactRemoteDataStore { doc -> doc.putData(APM_ENABLED_PATH, AIRPLANE_MODE_OFF) }
        flushNetworks()

        // Verify local airplane mode is disabled.
        assertThat(isAirplaneModeOn()).isFalse()

        // Enable airplane mode on remote device after destroy.
        mAirplaneModeSyncManager.destroy()
        transactRemoteDataStore { doc -> doc.putData(APM_ENABLED_PATH, AIRPLANE_MODE_ON) }
        flushNetworks()

        // Verify local airplane mode stays disabled.
        assertThat(isAirplaneModeOn()).isFalse()
    }

    private fun setAirplaneMode(enabled: Boolean) {
        mFakeAirplaneModeController.updateAirplaneModeState(enabled)
    }

    private fun isAirplaneModeOn(): Boolean {
        return mFakeAirplaneModeController.getAirplaneModeState()
    }

    @Suppress("UNCHECKED_CAST")
    private fun transactRemoteDataStore(consumer: (MutableDocument<String>) -> Unit) {
        mRemoteDeviceContext.sharedDataStore
            .transact(AirplaneModeSyncManager.DOC_ID) { doc ->
                consumer(doc as MutableDocument<String>)
                true
            }
            .get()
    }

    private fun connectDevicesToNetwork() {
        val remoteDevice =
            FakeRemoteDevice(1, UserHandle.USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setRemoteNetworkManager(mRemoteDeviceContext.networkManager)
        this.mFakeNetworkManager.addRemoteDevice(remoteDevice)

        val localDevice =
            FakeRemoteDevice(1, UserHandle.USER_ALL)
                .setRemoteNetworkManager(this.mFakeNetworkManager)
        mRemoteDeviceContext.networkManager.addRemoteDevice(localDevice)
    }

    private fun flushNetworks() {
        var shouldFlushMessages = true
        while (shouldFlushMessages) {
            shouldFlushMessages =
                this.mFakeNetworkManager.flushAllMessages() or
                    mRemoteDeviceContext.networkManager.flushAllMessages()
        }
    }

    companion object {
        private class RemoteDeviceContext : SharedDataStoreComponentTestBase() {
            init {
                super.setUp()
                mSharedDataStore.init()
            }

            override fun getSchemaProvider() =
                object : SchemaProvider<String> {
                    override fun getAllDocumentSchema(): List<DocumentSchemaInfo> =
                        AirplaneModeSyncManager.DOCUMENT_SCHEMA_INFO_LIST

                    override fun migrateDocument(document: MutableDocument<String>) {}
                }

            override fun getNetworkId() = AirplaneModeSyncManager.NETWORK_ID

            fun close() = mSharedDataStore.close()
        }
    }
}
