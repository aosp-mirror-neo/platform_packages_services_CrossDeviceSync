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

import static com.android.crossdevicesync.network.MessageRecord.MAX_DELIVERY_ATTEMPT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.companion.AssociationInfo;
import android.companion.DevicePresenceEvent;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy.DevicePresenceListener;
import com.android.crossdevicesync.network.NetworkManager.Network;
import com.android.crossdevicesync.network.NetworkManager.NetworkListener;
import com.android.crossdevicesync.network.NetworkManager.RemoteDevice;
import com.android.crossdevicesync.network.advertiser.Advertiser.AdvertisingSession;
import com.android.crossdevicesync.network.messenger.fake.FakeMessenger.SentMessage;
import com.android.crossdevicesync.network.scanner.fake.FakeScanner;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
        AssociationInfo info1 = createAssociationInfo(/* id= */ 1);
        AssociationInfo info2 = createAssociationInfo(/* id= */ 2);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info2);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 2, /* attached= */ true);

        mNetworkManagerImpl.init();

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1, 2);
        assertThat(devices.get(1).hasTransport()).isFalse();
        assertThat(devices.get(2).hasTransport()).isTrue();
    }

    @Test
    public void destroy_listenersAreCleared() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mNetworkManagerImpl.destroy();
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(
                mContext.getUserId(), createAssociationInfo(/* id= */ 2));

        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_addAssociation_createsRemoteDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);

        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1);
    }

    @Test
    public void onAssociationsChanged_addAssociation_registersPresenceListener() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).hasSize(1);
        DevicePresenceListener listener =
                mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners().get(0);
        assertThat(listener.serviceName()).isEqualTo(mContext.getPackageName());
        assertThat(listener.associationIds()).asList().containsExactly(1);
    }

    @Test
    public void onAssociationsChanged_removeAssociation_removesRemoteDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.removeAssociation(/* associationId= */ 1);

        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_removeAssociation_unregistersPresenceListener() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.removeAssociation(/* associationId= */ 1);

        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_noChange_doesNothing() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        // Adding the same info again should not trigger the listener.
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1);
        assertThat(devices.get(1).hasTransport()).isFalse();
    }

    @Test
    public void onTransportChanged_addTransport_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).hasTransport()).isTrue();
    }

    @Test
    public void onTransportChanged_removeTransport_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ false);

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).hasTransport()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_bleAppeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_bleDisappeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_DISAPPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_btConnected_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BT_CONNECTED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBtConnected()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_btDisconnected_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BT_CONNECTED, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BT_DISCONNECTED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBtConnected()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedAppeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedDisappeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED,
                        null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedAppeared()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedNearby_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedNearby()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedNotNearby_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                        null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedNearby()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_duplicateEvent_doesNotChangeState() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));
        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();

        // Simulate the same event again.
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_associationRemoved_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED,
                        null));
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
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ false);

        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void networkMembership_deviceJoinsWhenTransportBecomesAvailable() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener, never()).onDeviceChanged(1);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);
    }

    @Test
    public void networkMembership_deviceLeavesWhenAssociationRemoved() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);
        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.removeAssociation(/* associationId= */ 1);

        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void network_destroy() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);
        assertThat(network.getRemoteDevices()).hasSize(1);
        verify(mListener).onDeviceChanged(1);

        network.destroy();

        assertThat(mNetworkManagerImpl.getNetworks()).isEmpty();
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ false);
        verify(mListener, never()).onDeviceRemoved(1);
    }

    @Test
    public void network_listenersNotifiedOnChanges() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);
        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        verify(mListener).onDeviceChanged(1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ false);

        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void network_messagingMethodsDoNotCrashWhenDestroyed() {
        Network network = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);

        network.destroy();

        assertThat(network.broadcastMessage(new byte[0], 0)).isEqualTo(-1);
        assertThat(network.multicastMessage(Collections.singletonList(1), new byte[0], 0))
                .isEqualTo(-1);
        network.cancelMessage(0);
    }

    @Test
    public void unregisterListener_isNotNotified() {
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1", RemoteDevice::hasTransport, v -> true);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);
        verify(mListener).onDeviceChanged(1);

        network.unregisterListener(mListener);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ false);
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
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);
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
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);
        verify(mListener).onDeviceChanged(1);
        assertThat(network.getRemoteDevices().get(1).isBleAppeared()).isFalse();
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        verify(mListener).onDeviceChanged(1);
        assertThat(network.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void registerListener_onDestroyedNetwork_doesNotRegister() {
        Network network = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);
        network.destroy();

        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* attached= */ true);

        verify(mListener, never()).onDeviceChanged(1);
    }

    @Test
    public void broadcastMessage_sendsToAllDevicesInNetwork() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(new byte[] {1, 2, 3}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage message1 = mFakeMessenger.getSentMessages().get(0);
        assertThat(message1.associationId).isEqualTo(1);
        assertThat(message1.message).isEqualTo(new byte[] {1, 2, 3});
        SentMessage message2 = mFakeMessenger.getSentMessages().get(1);
        assertThat(message2.associationId).isEqualTo(2);
    }

    @Test
    public void multicastMessage_sendsToSpecifiedDevices() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.multicastMessage(Collections.singletonList(2), new byte[] {4, 5, 6}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        assertThat(message.associationId).isEqualTo(2);
        assertThat(message.message).isEqualTo(new byte[] {4, 5, 6});
    }

    @Test
    public void cancelMessage_preventsMessageFromBeingSent() {
        setupTwoDevices(/* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        long messageId =
                network.broadcastMessage(new byte[] {7, 8, 9}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        network.cancelMessage(messageId);
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void messageToOfflineDevice_waitsForPresence() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    public void messageWithScanFlag_triggersScanner() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(new byte[] {2}, Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);

        assertThat(mFakeScanner.getSessions()).hasSize(1);
        assertThat(mFakeScanner.getSessions().iterator().next().getAssociationId()).isEqualTo(1);
    }

    @Test
    public void messageWithNearbyOnlyFlag_notSentToFarDevice() {
        setupTwoDevices(/* present= */ true, /* nearby= */ false);
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(new byte[] {3}, Network.MESSAGE_FLAG_NEARBY_ONLY);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        assertThat(mFakeMessenger.getSentMessages().get(0).associationId).isEqualTo(1);
    }

    @Test
    public void multicastMessage_sendsToSubsetOfDevicesInNetwork() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        setupDevice(/* id= */ 3, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        assertThat(network.getRemoteDevices()).hasSize(3);

        network.multicastMessage(List.of(1, 3), new byte[] {4, 5, 6}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getTargetAssociationIds()).containsExactly(1, 3);
    }

    @Test
    public void invalidateNetworks_reEvaluatesNetworkMembership() {
        AtomicBoolean condition = new AtomicBoolean(true);
        Network network =
                mNetworkManagerImpl.createNetwork("test", v -> condition.get(), v -> true);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        assertThat(network.getRemoteDevices()).hasSize(1);

        condition.set(false);
        // No change yet, because no event has triggered re-evaluation.
        assertThat(network.getRemoteDevices()).hasSize(1);

        mNetworkManagerImpl.invalidateNetworks();

        // Now the device should be removed.
        assertThat(network.getRemoteDevices()).isEmpty();
    }

    @Test
    public void destroy_listenersAreNotNotifiedOfSubsequentChanges() {
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        network.registerListener(mMainExecutor, mListener);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(mListener, atLeastOnce()).onDeviceChanged(captor.capture());
        int countBeforeDestroy = captor.getAllValues().size();
        assertThat(countBeforeDestroy).isGreaterThan(0);

        network.destroy();

        // This would have triggered onDeviceChanged if the listener was still registered.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ false);

        verify(mListener, times(countBeforeDestroy)).onDeviceChanged(anyInt());
    }

    @Test
    public void messageWithStickyAndNearbyOnlyFlags_sendsWhenDeviceBecomesNearby() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(
                new byte[] {1}, Network.MESSAGE_FLAG_STICKY | Network.MESSAGE_FLAG_NEARBY_ONLY);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        assertThat(mFakeMessenger.getSentMessages().get(0).associationId).isEqualTo(1);
    }

    @Test
    public void destroyNetwork_cancelsPendingMessages() {
        setupTwoDevices(/* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        network.broadcastMessage(new byte[] {4}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        network.destroy();
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);
        makeDevicePresent(/* associationId= */ 2, /* isNearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void inboundMessage_notifiesListener() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        network.registerListener(mMainExecutor, mListener);

        mFakeMessenger.receiveMessage("test", 2, new byte[] {5, 6});

        verify(mListener).onNetworkMessage(2, new byte[] {5, 6});
    }

    @Test
    public void inboundMessage_forUnknownNetwork_isDropped() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        network.registerListener(mMainExecutor, mListener);

        mFakeMessenger.receiveMessage("unknown_network", 1, new byte[] {7, 8});

        verify(mListener, never()).onNetworkMessage(1, new byte[] {7, 8});
    }

    @Test
    public void messageLifecycle_success() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(new byte[] {1}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage sentMessage1 = mFakeMessenger.getSentMessages().get(0);
        SentMessage sentMessage2 = mFakeMessenger.getSentMessages().get(1);
        sentMessage1.completeSuccessfully();
        sentMessage2.completeSuccessfully();
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
    }

    @Test
    public void messageLifecycle_failureAndRetry_passesCorrectAttemptCount() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_RETRY_IF_FAILED);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getSentMessages().get(0).maxDeliveryAttempts)
                .isEqualTo(MAX_DELIVERY_ATTEMPT);
        assertThat(mFakeMessenger.getSentMessages().get(1).maxDeliveryAttempts)
                .isEqualTo(MAX_DELIVERY_ATTEMPT);
    }

    @Test
    public void messageLifecycle_noRetry_passesCorrectAttemptCount() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        network.broadcastMessage(new byte[] {1}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getSentMessages().get(0).maxDeliveryAttempts).isEqualTo(1);
        assertThat(mFakeMessenger.getSentMessages().get(1).maxDeliveryAttempts).isEqualTo(1);
    }

    @Test
    public void cancelMessage_cancelsInFlightMessage() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        long messageId = network.broadcastMessage(new byte[] {1, 2, 3}, 0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage sentMessage = mFakeMessenger.getSentMessages().get(0);
        assertThat(sentMessage.getFuture().isCancelled()).isFalse();

        network.cancelMessage(messageId);
        mNetworkManagerImpl.invalidateNetworks();

        assertThat(sentMessage.getFuture().isCancelled()).isTrue();
    }

    @Test
    public void messageLifecycle_failed() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        network.broadcastMessage(new byte[] {1}, 0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage sentMessage = mFakeMessenger.getSentMessages().get(0);

        sentMessage.completeWithException(new Exception("Failed to send"));
        mNetworkManagerImpl.invalidateNetworks();

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
    }

    @Test
    public void inboundMessage_fromNonMemberDevice_isDropped() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "test", device -> device.getAssociationId() == 1, v -> true);
        network.registerListener(mMainExecutor, mListener);
        assertThat(network.getRemoteDevices()).hasSize(1);
        assertThat(network.getRemoteDevices().get(1)).isNotNull();

        mFakeMessenger.receiveMessage("test", 2, new byte[] {1, 2, 3});

        verify(mListener, never()).onNetworkMessage(2, new byte[] {1, 2, 3});
    }

    @Test
    public void broadcastMessage_deliversToDevicesJoiningNetworkAfterBroadcast() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Broadcast a sticky message while device 1 is online.
        network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages().size()).isEqualTo(1);
        assertThat(mFakeMessenger.getTargetAssociationIds()).containsExactly(1);

        // Add device 2 to the network, also online.
        setupDevice(/* id= */ 2, /* present= */ true, /* nearby= */ true);

        // The broadcast should have been delivered to the new member.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getTargetAssociationIds()).containsExactly(1, 2);
    }

    @Test
    public void recreatingNetwork_doesNotInheritOldListeners() {
        Network network1 = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        network1.registerListener(mMainExecutor, mListener);

        network1.destroy();

        Network network2 = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        // Verify the listener from the old network was not notified.
        verify(mListener, never()).onDeviceChanged(anyInt());
    }

    @Test
    public void recreatingNetwork_doesNotInheritOldMessages() {
        Network network1 = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        network1.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        network1.destroy();

        Network network2 = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        // Verify the sticky message from the old network was not sent.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void cancelStickyBroadcast_isNotDeliveredToNewDevices() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a sticky broadcast to the first device.
        long messageId = network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Cancel the broadcast.
        network.cancelMessage(messageId);

        // Add a new device to the network.
        setupDevice(/* id= */ 2, /* present= */ true, /* nearby= */ true);

        // Verify that the cancelled sticky message is NOT sent to the new device.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void stickyMessageFailed_resendAfterReappearingUntilSuccess() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a sticky message to the first device.
        network.unicastMessage(/* associationId= */ 1, new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Broadcast failed.
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Message is sent again.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Still waiting for message.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);

        // Broadcast failed.
        message = mFakeMessenger.getSentMessages().get(1);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Message is sent again.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(3);

        // Succeed!
        message = mFakeMessenger.getSentMessages().get(2);
        message.completeSuccessfully();

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // No new message is sent.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(3);
    }

    @Test
    public void stickyBroadcastDeliveredToNewDevices() {
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a sticky broadcast.
        long messageId = network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Add a new device to the network.
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        // Verify that the message is sent to that device.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void nonStickyBroadcastNotDeliveredToNewDevices() {
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a non-sticky broadcast.
        long messageId = network.broadcastMessage(new byte[] {1}, /* flags= */ 0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Add a new device to the network.
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        // Verify that the message is not sent to that device.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);
    }

    @Test
    public void scanMessageTriggersScanningUntilDeviceBecomesPresent() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);

        // Verify that scanning has started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        // Now, make the device present.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        // Verify the message was sent and the scan was stopped to save resources.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void scanNearbyMessageTriggersScanningUntilDeviceBecomesNearby() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT | Network.MESSAGE_FLAG_NEARBY_ONLY);

        // Verify that scanning has started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        // Make the device present but not nearby.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ false);

        // Verify that scan continues and message is not sent
        assertThat(session.isActive()).isTrue();
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Device becomes nearby
        makeDevicePresent(/* associationid= */ 1, /* isNearby= */ true);

        // Verify that scan stops and message is sent
        assertThat(session.isActive()).isFalse();
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void nonStickyScanMessageFailed_notRescanOrResentAfterReappear() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Device present and message failed.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears.
        makeDeviceDisappear(/* associationId= */ 1);

        // Verify that scanning is not started.
        assertThat(mFakeScanner.getSessions()).hasSize(0);

        // Device appears.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        // Verify that message is not resent.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void stickyScanMessageFailed_rescanAndResentAfterReappear() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a sticky message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT | Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Device present and message failed.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears.
        makeDeviceDisappear(/* associationId= */ 1);

        // Verify that scanning is started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        assertThat(mFakeScanner.getAnySession().isActive()).isTrue();

        // Device appears.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        // Verify that scanning stopped and the message is resent.
        assertThat(mFakeScanner.getSessions()).hasSize(0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);

        // Device disappear after message sent.
        message = mFakeMessenger.getSentMessages().get(1);
        message.completeSuccessfully();
        makeDeviceDisappear(/* associationId= */ 1);

        // Verify that scanning is not started.
        assertThat(mFakeScanner.getSessions()).hasSize(0);

        // Device appears.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        // Verify that message is not resent.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
    }

    @Test
    public void stickyScanBroadcastTriggerScanForNewDevices() {
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);

        // Send a sticky scan broadcast.
        long messageId =
                network.broadcastMessage(
                        new byte[] {1},
                        Network.MESSAGE_FLAG_STICKY | Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Add a new device to the network.
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);

        // Verify that scanning started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        assertThat(mFakeScanner.getAnySession().isActive()).isTrue();

        // New device is present.
        makeDevicePresent(/* associationId= */ 1, /* isNearby= */ true);

        // Verify that scanning stopped and the message is sent to that device.
        assertThat(mFakeScanner.getSessions()).hasSize(0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void scanningStoppedWhenDeviceLeavesNetwork() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "test", device -> device.getAssociationId() == 1, v -> true);
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        mFakeCompanionDeviceManagerProxy.removeAssociation(1);

        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void scanningStoppedWhenMessageCancelled() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        long messageId =
                network.unicastMessage(
                        /* associationId= */ 1,
                        new byte[] {1},
                        Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        network.cancelMessage(messageId);

        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void scanningStoppedWhenNetworkDestroyed() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        network.destroy();

        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void advertisingStartedWhenDeviceJoinsNetwork() {
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        assertThat(mFakeAdvertiser.getSessions()).isEmpty();

        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        assertThat(mFakeAdvertiser.getSessions()).hasSize(1);
        assertThat(mFakeAdvertiser.getAnySession().isActive()).isTrue();
    }

    @Test
    public void advertisingStoppedWhenDeviceLeavesNetwork() {
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        assertThat(mFakeAdvertiser.getSessions()).hasSize(1);
        AdvertisingSession advertisingSession = mFakeAdvertiser.getAnySession();
        assertThat(advertisingSession.isActive()).isTrue();

        mFakeCompanionDeviceManagerProxy.removeAssociation(1);

        assertThat(mFakeAdvertiser.getSessions()).isEmpty();
        assertThat(advertisingSession.isActive()).isFalse();
    }

    @Test
    public void advertisingStoppedWhenNetworkDestroyed() {
        Network network = mNetworkManagerImpl.createNetwork("test", v -> true, v -> true);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        assertThat(mFakeAdvertiser.getSessions()).hasSize(1);
        AdvertisingSession advertisingSession = mFakeAdvertiser.getAnySession();
        assertThat(advertisingSession.isActive()).isTrue();

        network.destroy();

        assertThat(mFakeAdvertiser.getSessions()).isEmpty();
        assertThat(advertisingSession.isActive()).isFalse();
    }

    @Test
    public void messageSentToMultipleNetworks_allSentInFIFOOrder() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network1 = mNetworkManagerImpl.createNetwork("network1", v -> true, v -> true);
        Network network2 = mNetworkManagerImpl.createNetwork("network2", v -> true, v -> true);

        network1.broadcastMessage(new byte[] {1}, 0);
        network2.unicastMessage(1, new byte[] {2}, 0);
        network1.multicastMessage(List.of(1, 2), new byte[] {3}, 0);
        network2.broadcastMessage(new byte[] {4}, 0);

        List<SentMessage> sentMessages = mFakeMessenger.getSentMessages();
        assertThat(sentMessages).hasSize(7);

        // network1.broadcastMessage(new byte[] {1}, 0);
        assertThat(sentMessages.get(0).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(0).associationId).isEqualTo(1);
        assertThat(sentMessages.get(0).message).isEqualTo(new byte[] {1});
        assertThat(sentMessages.get(1).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(1).associationId).isEqualTo(2);
        assertThat(sentMessages.get(1).message).isEqualTo(new byte[] {1});

        // network2.unicastMessage(1, new byte[] {2}, 0);
        assertThat(sentMessages.get(2).networkId).isEqualTo("network2");
        assertThat(sentMessages.get(2).associationId).isEqualTo(1);
        assertThat(sentMessages.get(2).message).isEqualTo(new byte[] {2});

        // network1.multicastMessage(List.of(1, 2), new byte[] {3}, 0);
        assertThat(sentMessages.get(3).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(3).associationId).isEqualTo(1);
        assertThat(sentMessages.get(3).message).isEqualTo(new byte[] {3});
        assertThat(sentMessages.get(4).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(4).associationId).isEqualTo(2);
        assertThat(sentMessages.get(4).message).isEqualTo(new byte[] {3});

        // network2.broadcastMessage(new byte[] {4}, 0);
        assertThat(sentMessages.get(5).networkId).isEqualTo("network2");
        assertThat(sentMessages.get(5).associationId).isEqualTo(1);
        assertThat(sentMessages.get(5).message).isEqualTo(new byte[] {4});
        assertThat(sentMessages.get(6).networkId).isEqualTo("network2");
        assertThat(sentMessages.get(6).associationId).isEqualTo(2);
        assertThat(sentMessages.get(6).message).isEqualTo(new byte[] {4});
    }

    private void setupDevice(int id, boolean present, boolean nearby) {
        AssociationInfo info = createAssociationInfo(id);
        mFakeCompanionDeviceManagerProxy.addAssociationForUser(mContext.getUserId(), info);
        if (present) {
            makeDevicePresent(id, nearby);
        }
    }

    private void setupTwoDevices(boolean present, boolean nearby) {
        setupDevice(/* id= */ 1, present, nearby);
        setupDevice(/* id= */ 2, present, nearby);
    }

    private void makeDevicePresent(int associationId, boolean isNearby) {
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        associationId, DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED, null));
        if (isNearby) {
            mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                    new DevicePresenceEvent(
                            associationId, DevicePresenceEvent.EVENT_BT_CONNECTED, null));
        } else {
            mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                    new DevicePresenceEvent(
                            associationId, DevicePresenceEvent.EVENT_BT_DISCONNECTED, null));
        }
    }

    private void makeDeviceDisappear(int associationId) {
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        associationId, DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED, null));
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        associationId, DevicePresenceEvent.EVENT_BT_DISCONNECTED, null));
    }

    private AssociationInfo createAssociationInfo(int id) {
        return new AssociationInfo.Builder(id, mContext.getUserId(), "com.android.crossdevicesync")
                .setDisplayName("displayName" + id)
                .build();
    }
}
