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

package com.android.crossdevicesync.network;

import static com.google.common.truth.Truth.assertThat;

import android.companion.AssociationInfo;
import android.companion.DevicePresenceEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy.DevicePresenceListener;
import com.android.crossdevicesync.network.NetworkManager.RemoteDevice;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class NetworkManagerImplTest extends SyncServiceComponentTestBase {

    @Before
    public void setUp() {
        super.setUp();
        mNetworkManagerImpl.init();
    }

    @Test
    public void init_noAssociations_noRemoteDevice() {
        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
    }

    @Test
    public void init_withAssociations_createsRemoteDevices() {
        // Clear the associations added by setUp().
        mNetworkManagerImpl.destroy();
        AssociationInfo info1 = createAssociationInfo(1);
        AssociationInfo info2 = createAssociationInfo(2);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info2);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(2, true);

        mNetworkManagerImpl.init();

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1, 2);
        assertThat(devices.get(1).hasTransport()).isFalse();
        assertThat(devices.get(2).hasTransport()).isTrue();
    }

    @Test
    public void onAssociationsChanged_addAssociation_createsRemoteDevice() {
        AssociationInfo info = createAssociationInfo(1);

        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1);
    }

    @Test
    public void onAssociationsChanged_addAssociation_registersPresenceListener() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).hasSize(1);
        DevicePresenceListener listener =
                mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners().get(0);
        assertThat(listener.serviceName()).isEqualTo(mContext.getPackageName());
        assertThat(listener.associationIds()).asList().containsExactly(1);
    }

    @Test
    public void onAssociationsChanged_removeAssociation_removesRemoteDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.removeAssociation(1);

        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_removeAssociation_unregistersPresenceListener() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.removeAssociation(1);

        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_noChange_doesNothing() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        // Adding the same info again should not trigger the listener.
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1);
        assertThat(devices.get(1).hasTransport()).isFalse();
    }

    @Test
    public void onTransportChanged_addTransport_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).hasTransport()).isTrue();
    }

    @Test
    public void onTransportChanged_removeTransport_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, false);

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).hasTransport()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_bleAppeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_bleDisappeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BLE_DISAPPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_btConnected_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BT_CONNECTED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBtConnected()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_btDisconnected_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BT_CONNECTED, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BT_DISCONNECTED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBtConnected()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedAppeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedDisappeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        1, DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedAppeared()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedNearby_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedNearby()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedNotNearby_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        1, DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedNearby()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_duplicateEvent_doesNotChangeState() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));
        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();

        // Simulate the same event again.
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void destroy_listenersAreCleared() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mNetworkManagerImpl.destroy();
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(
                mContext.getUserId(), createAssociationInfo(2));

        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).isEmpty();
    }

    private AssociationInfo createAssociationInfo(int id) {
        return new AssociationInfo.Builder(id, mContext.getUserId(), "com.android.crossdevicesync")
                .setDisplayName("displayName" + id)
                .build();
    }

    @Test
    public void onDevicePresenceEvent_associationRemoved_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isAssociationRemoved()).isTrue();
    }
}
