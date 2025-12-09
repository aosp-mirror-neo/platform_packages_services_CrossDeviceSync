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

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC
import android.os.PersistableBundle
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.util.IndentingPrintWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.crossdevicesync.data.DocumentSchemaInfo
import com.android.crossdevicesync.data.SchemaProvider
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument
import com.android.crossdevicesync.data.SharedDataStore.Record
import com.android.crossdevicesync.data.SharedDataStoreComponentTestBase
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.Companion.APM_ENABLED_PATH
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.Companion.APM_SYNC_SUPPORTED
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.Companion.toAirplaneModeState
import com.android.crossdevicesync.network.fake.FakeNetworkManager.FakeRemoteDevice
import com.android.crossdevicesync.notification.NotificationReason.Companion.AIRPLANE_MODE_SYNCED
import com.android.crossdevicesync.services.SyncServiceComponentTestBase
import com.android.internal.R
import com.android.server.connectivity.Flags
import com.google.common.truth.Truth.assertThat
import java.io.StringWriter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirplaneModeSyncManagerTest : SyncServiceComponentTestBase() {
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    private lateinit var mRemoteDeviceContext: RemoteDeviceContext

    // Use real shared data store in this test.
    override fun useRealSharedDataStoreImpl() = true

    @Before
    override fun setUp() {
        super.setUp()
        mFakeNetworkManager.init()
        mFakeMetadataPublisher.init()
        mRemoteDeviceContext = RemoteDeviceContext()
    }

    @After
    fun tearDown() {
        mAirplaneModeSyncManager.destroy()
        mRemoteDeviceContext.close()
        mFakeNetworkManager.destroy()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_syncEnabled_publishesMetadata() {
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()

        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(UserHandle.USER_ALL)
                    .getBoolean(APM_SYNC_SUPPORTED)
            )
            .isTrue()
        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(UserHandle.USER_ALL)
                    .getBoolean(Settings.Global.AIRPLANE_MODE_SYNC)
            )
            .isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_syncDisabled_publishesMetadata() {
        setAirplaneModeSyncEnabled(false)
        mAirplaneModeSyncManager.init()

        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(UserHandle.USER_ALL)
                    .getBoolean(APM_SYNC_SUPPORTED)
            )
            .isTrue()
        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(UserHandle.USER_ALL)
                    .getBoolean(Settings.Global.AIRPLANE_MODE_SYNC)
            )
            .isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_isWatch_allRemoteDevicesEligibleForAirplaneModeSync() {
        mIsWatchProvider.isWatch = true
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        val fakeNetwork =
            mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, UserHandle.USER_ALL)

        val remoteDevice =
            FakeRemoteDevice(1, UserHandle.USER_ALL)
                .setAssociationInfoCache(createAssociationWitApmSyncEnabled(true))
        assertThat(fakeNetwork.isDeviceEligibleForNetwork(remoteDevice)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_isNotWatch_onlyAllowsRemoteWatchesWithSyncEnabledForAirplaneModeSyncNetwork() {
        mIsWatchProvider.isWatch = false
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        val fakeNetwork =
            mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, UserHandle.USER_ALL)

        val remoteDevice =
            FakeRemoteDevice(1, UserHandle.USER_ALL)
                .setAssociationInfoCache(createAssociationWitApmSyncEnabled(true))
        assertThat(fakeNetwork.isDeviceEligibleForNetwork(remoteDevice)).isFalse()

        val watchDeviceWithSyncDisabled =
            FakeRemoteDevice(1, UserHandle.USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setAssociationInfoCache(createAssociationWitApmSyncEnabled(false))
        assertThat(fakeNetwork.isDeviceEligibleForNetwork(watchDeviceWithSyncDisabled)).isFalse()

        val watchDevice =
            FakeRemoteDevice(1, UserHandle.USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setAssociationInfoCache(createAssociationWitApmSyncEnabled(true))
        assertThat(fakeNetwork.isDeviceEligibleForNetwork(watchDevice)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_syncsCurrentState() {
        // Enable airplane mode locally before initialization.
        setAirplaneMode(true)

        // Initialize the manager.
        connectDevicesToNetwork()
        mAirplaneModeSyncManager.init()
        flushNetworks()

        // Verify remote device has the initial state.
        verifyRemoteAirplaneModeEnabled(true)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testLocalChange_syncToRemote() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode locally after init, which should trigger the remote device sync.
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device receives the update.
        verifyRemoteAirplaneModeEnabled(true)

        // Disable airplane mode locally.
        setAirplaneMode(false)
        flushNetworks()

        // Verify remote device receives the update.
        verifyRemoteAirplaneModeEnabled(false)

        // Enable airplane mode locally after destroy, which should not trigger the remote sync.
        mAirplaneModeSyncManager.destroy()
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device stays off.
        verifyRemoteAirplaneModeEnabled(false)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testRemoteChange_syncToLocal() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode is enabled.
        assertThat(isAirplaneModeOn()).isTrue()

        // Disable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(false)
        flushNetworks()

        // Verify local airplane mode is disabled.
        assertThat(isAirplaneModeOn()).isFalse()

        // Enable airplane mode on remote device after destroy.
        mAirplaneModeSyncManager.destroy()
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode stays disabled.
        assertThat(isAirplaneModeOn()).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_flagDisabled_doesNothing() {
        mAirplaneModeSyncManager.init()
        assertThat(mFakeMetadataPublisher.getMetadata(UserHandle.USER_ALL).isEmpty).isTrue()
    }

    @Test
    @DisableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDestroy_flagDisabled_doesNothing() {
        // No exception should be thrown.
        mAirplaneModeSyncManager.destroy()
    }

    @Test
    @DisableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDump_flagDisabled_doesNothing() {
        val stringWriter = StringWriter()
        val pw = IndentingPrintWriter(stringWriter, "  ")

        mAirplaneModeSyncManager.dump(pw)

        assertThat(stringWriter.toString()).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testLocalChange_syncDisabled_doesNothing() {
        // Disable airplane mode sync.
        setAirplaneModeSyncEnabled(false)

        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode locally after init, which should not trigger the remote device sync.
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device does not receive any update.
        verifyRemoteAirplaneModeEnabled(null)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testRemoteChange_syncDisabled_doesNothing() {
        // Disable airplane mode sync.
        setAirplaneModeSyncEnabled(false)

        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode is not enabled.
        assertThat(isAirplaneModeOn()).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testRemoteChange_syncEnabled_showsNotification() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify notification is shown.
        assertThat(mFakeNotificationHelper.mShownNotifications).contains(AIRPLANE_MODE_SYNCED)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_isKidsWatch_publishesNotSupported() {
        mIsWatchProvider.isKidsWatch = true
        mAirplaneModeSyncManager.init()
        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(UserHandle.USER_ALL)
                    .getBoolean(APM_SYNC_SUPPORTED)
            )
            .isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_configIsNotSupported_publishesNotSupported() {
        mFakeResources.overrideResource(R.bool.config_supportAirplaneModeSync, false)
        mAirplaneModeSyncManager.init()
        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(UserHandle.USER_ALL)
                    .getBoolean(APM_SYNC_SUPPORTED)
            )
            .isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testOnAirplaneModeSyncEnabledStateChanged_enabled_remoteChangeUpdatesLocal() {
        // Start with sync disabled.
        setAirplaneModeSyncEnabled(false)
        mAirplaneModeSyncManager.init()

        // Enable sync. This will trigger the listener.
        setAirplaneModeSyncEnabled(true)
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode is enabled.
        assertThat(isAirplaneModeOn()).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testOnAirplaneModeSyncEnabledStateChanged_disabled_remoteChangeDoesNotUpdateLocal() {
        // Start with sync enabled.
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()
        flushNetworks()

        // Verify sync works
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()
        assertThat(isAirplaneModeOn()).isTrue()

        // Disable sync.
        setAirplaneModeSyncEnabled(false)

        // Try to sync a change from remote
        setRemoteAirplaneModeEnabled(false)
        flushNetworks()

        // Verify local state does not change
        assertThat(isAirplaneModeOn()).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDump_printsCorrectState() {
        val stringWriter = StringWriter()
        val pw = IndentingPrintWriter(stringWriter, "  ")

        // 1. Initial state with sync disabled
        setAirplaneModeSyncEnabled(false)
        setAirplaneMode(true)
        mAirplaneModeSyncManager.init()
        mAirplaneModeSyncManager.dump(pw)
        var dumpString = stringWriter.toString()
        assertThat(dumpString).contains("AirplaneModeSyncManager:")
        assertThat(dumpString).contains("Airplane Mode State = true")
        assertThat(dumpString).doesNotContain("Network :")
        assertThat(dumpString).doesNotContain("Data Store :")

        // 2. Enable sync
        stringWriter.buffer.setLength(0)
        setAirplaneModeSyncEnabled(true)
        connectDevicesToNetwork()
        flushNetworks()
        mAirplaneModeSyncManager.dump(pw)
        dumpString = stringWriter.toString()
        assertThat(dumpString).contains("AirplaneModeSyncManager:")
        assertThat(dumpString).contains("Airplane Mode State = true")
        assertThat(dumpString).contains("Network :")
        assertThat(dumpString).contains("Data Store :")

        // 3. Disable sync again
        stringWriter.buffer.setLength(0)
        setAirplaneModeSyncEnabled(false)
        flushNetworks()
        mAirplaneModeSyncManager.dump(pw)
        dumpString = stringWriter.toString()
        assertThat(dumpString).contains("AirplaneModeSyncManager:")
        assertThat(dumpString).contains("Airplane Mode State = true")
        assertThat(dumpString).doesNotContain("Network :")
        assertThat(dumpString).doesNotContain("Data Store :")
    }

    private fun createAssociationWitApmSyncEnabled(syncEnabled: Boolean = true): AssociationInfo {
        return AssociationInfo.Builder(1, UserHandle.USER_ALL, "packageName")
            .setDisplayName("test_device")
            .setMetadata(
                PersistableBundle().apply {
                    putPersistableBundle(
                        FEATURE_CROSS_DEVICE_SYNC,
                        PersistableBundle().apply {
                            putBoolean(APM_SYNC_SUPPORTED, syncEnabled)
                            putBoolean(Settings.Global.AIRPLANE_MODE_SYNC, syncEnabled)
                        },
                    )
                }
            )
            .build()
    }

    private fun setAirplaneMode(enabled: Boolean) {
        mFakeAirplaneModeController.updateAirplaneModeState(enabled)
    }

    private fun isAirplaneModeOn(): Boolean {
        return mFakeAirplaneModeController.isAirplaneModeEnabled()
    }

    private fun setAirplaneModeSyncEnabled(enabled: Boolean) {
        mFakeAirplaneModeController.setAirplaneModeSyncEnabledState(enabled)
    }

    private fun setRemoteAirplaneModeEnabled(enabled: Boolean) {
        transactRemoteDataStore { doc ->
            doc.putData(APM_ENABLED_PATH, enabled.toAirplaneModeState())
        }
    }

    private fun verifyRemoteAirplaneModeEnabled(enabled: Boolean?) {
        transactRemoteDataStore { doc ->
            val record = doc.getRecord(APM_ENABLED_PATH) as Record<String>
            assertThat(record.get()).isEqualTo(enabled?.toAirplaneModeState())
        }
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
                .setAssociationInfoCache(createAssociationWitApmSyncEnabled(true))
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
