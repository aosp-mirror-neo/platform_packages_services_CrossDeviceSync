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
package com.android.crossdevicesync.metadata;

import static android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC;

import static com.android.crossdevicesync.metadata.MetadataPublisherImpl.LAST_METADATA_UPDATE_TIMESTAMP_PREFIX;
import static com.android.crossdevicesync.metadata.MetadataPublisherImpl.NETWORK_ID;
import static com.android.crossdevicesync.metadata.MetadataPublisherImpl.NETWORK_ID_USER_ALL;

import static com.google.common.truth.Truth.assertThat;

import android.companion.AssociationInfo;
import android.os.PersistableBundle;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.network.fake.FakeNetworkManager.FakeRemoteDevice;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MetadataPublisherImplTest extends SyncServiceComponentTestBase {
    @Before
    public void setUp() {
        super.setUp();
        mFakeNetworkManager.init();
    }

    @After
    public void tearDown() {
        mSharedPreferences.edit().clear().commit();
    }

    @Test
    public void init_hasNewMetadata_pingRemoteDevices() {
        int userId = 2;
        mSharedPreferences
                .edit()
                .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId, 10)
                .commit();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 5));

        mMetadataPublisher.init();

        // Verify that we pinged the remote device.
        assertThat(device.getSentMessages()).hasSize(1);
    }

    @Test
    public void init_noNewMetadata_notPingRemoteDevices() {
        int userId = 2;
        mSharedPreferences
                .edit()
                .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId, 10)
                .commit();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        mMetadataPublisher.init();

        // Verify that we pinged the remote device.
        assertThat(device.getSentMessages()).isEmpty();
    }

    @Test
    public void init_newDeviceIsPinged() {
        int userId = 2;
        mSharedPreferences
                .edit()
                .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId, 10)
                .commit();
        mMetadataPublisher.init();

        // A new device is added.
        FakeRemoteDevice device = mFakeNetworkManager.addRemoteDeviceForUser(userId);

        // Verify that we pinged the new device.
        assertThat(device.getSentMessages()).hasSize(1);
        assertThat(mFakeNetworkManager.getNetworks().get(0).getNetworkId()).isEqualTo(NETWORK_ID);
    }

    @Test
    public void setMetadata_metadataSentToCdm() {
        int userId = 2;
        mMetadataPublisher.init();

        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        assertThat(
                        mFakeCompanionDeviceManagerProxy
                                .getLocalMetadata(userId)
                                .getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC)
                                .getBoolean("key"))
                .isTrue();
        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId))
                .isTrue();
    }

    @Test
    public void setMetadata_pingRemoteDevices() {
        int userId = 2;
        mMetadataPublisher.init();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        assertThat(device.getSentMessages()).hasSize(1);
        assertThat(mFakeNetworkManager.getNetworks().get(0).getNetworkId()).isEqualTo(NETWORK_ID);
    }

    @Test
    public void setMetadata_userAll_pingRemoteDevices() {
        mMetadataPublisher.init();
        FakeRemoteDevice device1 =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(
                                /* id= */ 1, /* userId= */ 1, /* timeMetadataSent= */ 10));
        FakeRemoteDevice device2 =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(
                                /* id= */ 2, /* userId= */ 2, /* timeMetadataSent= */ 10));

        mMetadataPublisher.putBooleanMetaData(UserHandle.USER_ALL, "key", true);

        assertThat(device1.getSentMessages()).hasSize(1);
        assertThat(device2.getSentMessages()).hasSize(1);
        assertThat(mFakeNetworkManager.getNetworks().get(0).getNetworkId())
                .isEqualTo(NETWORK_ID_USER_ALL);
    }

    @Test
    public void setMetadata_newDeviceIsPinged() {
        int userId = 2;
        mMetadataPublisher.init();
        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        // New device is added
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        // Verify that we pinged the new device.
        assertThat(device.getSentMessages()).hasSize(1);
        assertThat(mFakeNetworkManager.getNetworks().get(0).getNetworkId()).isEqualTo(NETWORK_ID);
    }

    @Test
    public void setMetadata_noChange_doNothing() {
        int userId = 2;
        PersistableBundle metadata = new PersistableBundle();
        metadata.putBoolean("key", true);
        mFakeCompanionDeviceManagerProxy.setLocalMetadata(
                userId, FEATURE_CROSS_DEVICE_SYNC, metadata);
        mMetadataPublisher.init();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        // Set metadata.
        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        // Verify that we didn't update shared prefs.
        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId))
                .isFalse();
        // Verify that we didn't ping any devices.
        assertThat(device.getSentMessages()).isEmpty();
    }

    private AssociationInfo createAssociationInfo(int id, int userId, long timeMetadataSent) {
        return new AssociationInfo.Builder(id, userId, "com.android.crossdevicesync")
                .setDisplayName("displayName" + id)
                .setTimeMetadataSent(timeMetadataSent)
                .build();
    }
}
