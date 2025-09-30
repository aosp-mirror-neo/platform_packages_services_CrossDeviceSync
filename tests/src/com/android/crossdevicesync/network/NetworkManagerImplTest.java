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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import android.companion.AssociationInfo;
import android.companion.DevicePresenceEvent;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy.DevicePresenceListener;
import com.android.crossdevicesync.network.NetworkManager.Network;
import com.android.crossdevicesync.network.NetworkManager.NetworkListener;
import com.android.crossdevicesync.network.NetworkManager.RemoteDevice;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class NetworkManagerImplTest extends SyncServiceComponentTestBase {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock private NetworkListener mListener;

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

    @Test
    public void onDevicePresenceEvent_associationRemoved_updatesDevice() {
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED, null));
        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isAssociationRemoved()).isTrue();
    }

    @Test
    public void createNetwork_success() {
        Network network = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);
        assertThat(network).isNotNull();
        assertThat(network.getNetworkId()).isEqualTo("network1");
        assertThat(mNetworkManagerImpl.getNetworks().get("network1")).isSameInstanceAs(network);
    }

    @Test
    public void createNetwork_returnsExisting() {
        Network network1 = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);
        Network network2 = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);
        assertThat(network1).isSameInstanceAs(network2);
    }

    @Test
    public void createNetwork_throwsWhenNotInitialized() {
        mNetworkManagerImpl.destroy();
        assertThrows(
                IllegalStateException.class,
                () -> mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true));
    }

    @Test
    public void networkMembership_deviceAddedAndRemoved() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, false);

        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void networkMembership_deviceJoinsWhenTransportBecomesAvailable() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener, never()).onDeviceChanged(1);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);
    }

    @Test
    public void networkMembership_deviceLeavesWhenAssociationRemoved() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.removeAssociation(1);

        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void network_destroy() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);

        network.destroy();
        assertThat(mNetworkManagerImpl.getNetworks()).isEmpty();

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, false);
        verify(mListener, never()).onDeviceRemoved(1);
    }

    @Test
    public void network_listenersNotifiedOnChanges() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, false);

        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void network_messagingMethodsDoNotCrashWhenDestroyed() {
        Network network = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);
        network.destroy();
        assertThat(network.broadcastMessage(new byte[0], 0)).isEqualTo(-1);
        assertThat(network.unicastMessage(1, new byte[0], 0)).isEqualTo(-1);
        network.cancelMessage(0);
    }

    @Test
    public void unregisterListener_isNotNotified() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);
        verify(mListener).onDeviceChanged(1);

        network.unregisterListener(mListener);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, false);
        verify(mListener, never()).onDeviceRemoved(1);
    }

    @Test
    public void networkMembership_associationCondition() {
        // Create a network that only accepts devices with a specific metadata flag.
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1",
                        v -> true,
                        associationInfo ->
                                associationInfo.getMetadata().getBoolean("is_wearable", false));
        network.registerListener(mMainExecutor, mListener);

        // Add a device with empty metadata and verify it doesn't join.
        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);
        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener, never()).onDeviceChanged(1);

        // Update the association to add the "is_wearable" flag to the metadata.
        PersistableBundle metadata = new PersistableBundle();
        metadata.putBoolean("is_wearable", true);
        AssociationInfo wearableInfo =
                new AssociationInfo.Builder(info).setMetadata(metadata).build();
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), wearableInfo);

        // Verify the device now joins the network.
        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        // Update the association to use empty metadata again.
        AssociationInfo nonWearableInfo =
                new AssociationInfo.Builder(info).setMetadata(new PersistableBundle()).build();
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(
                mContext.getUserId(), nonWearableInfo);

        // Verify the device leaves the network.
        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void onDeviceChanged_isCalledForExistingMemberStateUpdate() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        verify(mListener).onDeviceChanged(1);
        assertThat(network.getRemoteDevices().get(1).isBleAppeared()).isFalse();
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        verify(mListener).onDeviceChanged(1);
        assertThat(network.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void registerListener_onDestroyedNetwork_doesNotRegister() {
        Network network = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);
        network.destroy();

        network.registerListener(mMainExecutor, mListener);

        AssociationInfo info = createAssociationInfo(1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(1, true);

        verify(mListener, never()).onDeviceChanged(1);
    }

    private AssociationInfo createAssociationInfo(int id) {
        return new AssociationInfo.Builder(id, mContext.getUserId(), "com.android.crossdevicesync")
                .setDisplayName("displayName" + id)
                .build();
    }
}
