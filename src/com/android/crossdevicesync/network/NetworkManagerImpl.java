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

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager.OnAssociationsChangedListener;
import android.companion.DevicePresenceEvent;
import android.content.Context;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.network.dagger.qualifier.NetworkLock;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.inject.Inject;

/** Default implementation of {@link NetworkManager}. */
@SyncServiceSingleton
public class NetworkManagerImpl implements NetworkManager {
    private static final String TAG = "NetworkManager";

    private final Object mLock;
    private final Executor mMainExecutor;
    private final CompanionDeviceManagerProxy mCompanionDeviceManager;
    private final Context mContext;
    private final Consumer<List<AssociationInfo>> mOnTransportChanged = this::onTransportChanged;
    private final OnAssociationsChangedListener mOnAssociationsChanged =
            this::onAssociationsChanged;
    private final Consumer<DevicePresenceEvent> mOnDevicePresenceEvent =
            this::onDevicePresenceEvent;

    @GuardedBy("mLock")
    private boolean mInitialized;

    @GuardedBy("mLock")
    private Map<Integer, RemoteDeviceImpl> mRemoteDevices = new HashMap<>();

    @GuardedBy("mLock")
    private Map<String, NetworkImpl> mNetworks = new HashMap<>();

    @GuardedBy("mLock")
    private boolean mPendingNetworkInvalidation;

    @Inject
    NetworkManagerImpl(
            @NetworkLock Object lock,
            @Main Executor mainExecutor,
            CompanionDeviceManagerProxy companionDeviceManager,
            Context context) {
        mLock = lock;
        mMainExecutor = mainExecutor;
        mCompanionDeviceManager = companionDeviceManager;
        mContext = context;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mInitialized) {
                throw new IllegalStateException("Already initialized!");
            }
            mInitialized = true;
            processAssociationsLocked(
                    mCompanionDeviceManager.getAllAssociations(UserHandle.USER_ALL));
            mCompanionDeviceManager.addOnAssociationsChangedListener(
                    mMainExecutor, mOnAssociationsChanged, UserHandle.USER_ALL);
            mCompanionDeviceManager.addOnTransportsChangedListener(
                    mMainExecutor, mOnTransportChanged);
        }
    }

    @Override
    public void destroy() {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            mInitialized = false;
            mCompanionDeviceManager.removeOnAssociationsChangedListener(mOnAssociationsChanged);
            mCompanionDeviceManager.removeOnTransportsChangedListener(mOnTransportChanged);
            mCompanionDeviceManager.removeOnDevicePresenceEventListener(mContext.getPackageName());
            mRemoteDevices.clear();
            // Use while loop to prevent ConcurrentModificationException when calling NetworkImpl
            // .destroy().
            while (!mNetworks.isEmpty()) {
                mNetworks.values().iterator().next().destroy();
            }
            mPendingNetworkInvalidation = false;
        }
    }

    @Override
    public Network createNetwork(
            String networkId,
            Predicate<RemoteDevice> remoteDeviceCondition,
            Predicate<AssociationInfo> associationCondition) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            if (mNetworks.containsKey(networkId)) {
                return mNetworks.get(networkId);
            }
            NetworkImpl network =
                    new NetworkImpl(networkId, remoteDeviceCondition, associationCondition);
            mNetworks.put(networkId, network);
            invalidateNetworks();
            return network;
        }
    }

    @GuardedBy("mLock")
    private void throwIfUninitializedLocked() {
        if (!mInitialized) {
            throw new IllegalStateException("Network manager not initialized!");
        }
    }

    @Override
    public void invalidateNetworks() {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "invalidateNetworks: ignore since not initialized");
                return;
            }
            if (mPendingNetworkInvalidation) {
                return;
            }
            mPendingNetworkInvalidation = true;
            // Invalidate async to potentially batch multiple network invalidations.
            mMainExecutor.execute(this::doInvalidateNetworks);
        }
    }

    private void doInvalidateNetworks() {
        synchronized (mLock) {
            if (!mPendingNetworkInvalidation) {
                return;
            }
            processAssociationsLocked(
                    mCompanionDeviceManager.getAllAssociations(UserHandle.USER_ALL));
            mPendingNetworkInvalidation = false;
        }
    }

    private void onAssociationsChanged(List<AssociationInfo> associations) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onAssociationsChanged: ignore since not initialized");
                return;
            }
            processAssociationsLocked(associations);
        }
    }

    @SuppressWarnings("GuardedBy")
    @GuardedBy("mLock")
    private void processAssociationsLocked(List<AssociationInfo> allInfo) {
        // Add new info if not existing.
        Set<Integer> allInfoIds = new HashSet<>(allInfo.size());
        boolean changed = false;
        for (AssociationInfo info : allInfo) {
            int id = info.getId();
            allInfoIds.add(id);
            if (!mRemoteDevices.containsKey(id)) {
                RemoteDeviceImpl device = new RemoteDeviceImpl(info);
                mRemoteDevices.put(id, device);
                changed = true;
                Log.d(TAG, "Added remote device " + device);
            }
        }
        // Remove old info if not existing.
        changed |= mRemoteDevices.keySet().retainAll(allInfoIds);
        if (changed) {
            updateDevicePresenceListenerLocked();
        }
        // Update network members.
        Map<Integer, AssociationInfo> associationsMap =
                Maps.uniqueIndex(allInfo, AssociationInfo::getId);
        mNetworks.values().forEach(n -> n.invalidateLocked(associationsMap));
    }

    @GuardedBy("mLock")
    private void updateDevicePresenceListenerLocked() {
        int[] associationIds = new int[mRemoteDevices.size()];
        int i = 0;
        for (int id : mRemoteDevices.keySet()) {
            associationIds[i++] = id;
        }
        if (associationIds.length == 0) {
            mCompanionDeviceManager.removeOnDevicePresenceEventListener(mContext.getPackageName());
            return;
        }
        Log.d(TAG, "Update device presence listener for " + Arrays.toString(associationIds));
        mCompanionDeviceManager.setOnDevicePresenceEventListener(
                associationIds, mContext.getPackageName(), mMainExecutor, mOnDevicePresenceEvent);
    }

    @SuppressWarnings("GuardedBy")
    private void onTransportChanged(List<AssociationInfo> associationsWithTransport) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onTransportChanged: ignore since not initialized");
                return;
            }
            Set<Integer> idsWithTransport = new HashSet<>();
            for (AssociationInfo associationInfo : associationsWithTransport) {
                int id = associationInfo.getId();
                if (!mRemoteDevices.containsKey(id)) {
                    Log.w(TAG, "updateTransportFlag: unknown device " + id);
                    continue;
                }
                idsWithTransport.add(id);
            }
            boolean changed = false;
            for (RemoteDeviceImpl device : mRemoteDevices.values()) {
                boolean hasTransport = idsWithTransport.contains(device.getAssociationId());
                if (device.setHasTransport(hasTransport)) {
                    Log.d(
                            TAG,
                            "updateTransportFlag: "
                                    + device
                                    + (device.hasTransport() ? " established" : " lost")
                                    + " transport");
                    changed = true;
                    mNetworks
                            .values()
                            .forEach(n -> n.notifyDeviceChangedLocked(device.getAssociationId()));
                }
            }
            if (changed) {
                // Transport has changed. Need to re-evaluate network members.
                invalidateNetworks();
            }
        }
    }

    @SuppressWarnings("GuardedBy")
    private void onDevicePresenceEvent(DevicePresenceEvent event) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onDevicePresenceEvent: ignore since not initialized");
                return;
            }

            RemoteDeviceImpl device = mRemoteDevices.get(event.getAssociationId());
            if (device == null) {
                Log.w(
                        TAG,
                        "updateDevicePresenceLocked: unknown device " + event.getAssociationId());
                return;
            }
            boolean changed =
                    switch (event.getEvent()) {
                        case DevicePresenceEvent.EVENT_BLE_APPEARED -> device.setBleAppeared(true);
                        case DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                                device.setBleAppeared(false);
                        case DevicePresenceEvent.EVENT_BT_CONNECTED -> device.setBtConnected(true);
                        case DevicePresenceEvent.EVENT_BT_DISCONNECTED ->
                                device.setBtConnected(false);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED ->
                                device.setSelfManagedAppeared(true);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED ->
                                device.setSelfManagedAppeared(false);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY ->
                                device.setSelfManagedNearby(true);
                        case DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY ->
                                device.setSelfManagedNearby(false);
                        case DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED ->
                                device.setAssociationRemoved(true);
                        default -> {
                            Log.w(TAG, "Unhandled device presence event: " + event);
                            yield false;
                        }
                    };
            if (changed) {
                mNetworks
                        .values()
                        .forEach(n -> n.notifyDeviceChangedLocked(device.getAssociationId()));
                Log.d(TAG, "Device presence flag changed for " + device + ", event: " + event);
                // Device presence state changed. Need to re-evaluate network members.
                invalidateNetworks();
            }
        }
    }

    @Override
    public Map<Integer, RemoteDevice> getRemoteDevices() {
        synchronized (mLock) {
            return Collections.unmodifiableMap(mRemoteDevices);
        }
    }

    @Override
    public Map<String, Network> getNetworks() {
        synchronized (mLock) {
            return Collections.unmodifiableMap(mNetworks);
        }
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("NetworkManager:");
            pw.increaseIndent();
            pw.println("mInitialized=" + mInitialized);
            if (mRemoteDevices.isEmpty()) {
                pw.println("No remote devices.");
            } else {
                pw.println("Remote devices:");
                pw.increaseIndent();
                mRemoteDevices.values().forEach(device -> device.dump(pw));
                pw.decreaseIndent();
            }
            if (mNetworks.isEmpty()) {
                pw.println("No networks.");
            } else {
                pw.println("Networks:");
                pw.increaseIndent();
                mNetworks.values().forEach(network -> network.dump(pw));
                pw.decreaseIndent();
            }
            pw.decreaseIndent();
        }
    }

    /** A class representing a remote device. */
    private static class RemoteDeviceImpl implements RemoteDevice {
        private static final int FLAG_HAS_TRANSPORT = 1;
        private static final int FLAG_BLE_APPEARED = 1 << 1;
        private static final int FLAG_BT_CONNECTED = 1 << 2;
        private static final int FLAG_SELF_MANAGED_APPEARED = 1 << 3;
        private static final int FLAG_SELF_MANAGED_NEARBY = 1 << 4;
        private static final int FLAG_ASSOCIATION_REMOVED = 1 << 5;

        private final int mAssociationId;
        @Nullable private final CharSequence mDisplayName;
        @Nullable private final String mDeviceProfile;
        private int mFlags;

        RemoteDeviceImpl(AssociationInfo associationInfo) {
            mAssociationId = associationInfo.getId();
            mDisplayName = associationInfo.getDisplayName();
            mDeviceProfile = associationInfo.getDeviceProfile();
        }

        @Override
        public int getAssociationId() {
            return mAssociationId;
        }

        @Override
        public CharSequence getDisplayName() {
            return mDisplayName;
        }

        @Override
        public String getDeviceProfile() {
            return mDeviceProfile;
        }

        @Override
        public boolean hasTransport() {
            return (mFlags & FLAG_HAS_TRANSPORT) != 0;
        }

        public boolean setHasTransport(boolean hasTransport) {
            return setFlag(FLAG_HAS_TRANSPORT, hasTransport);
        }

        @Override
        public boolean isBleAppeared() {
            return (mFlags & FLAG_BLE_APPEARED) != 0;
        }

        public boolean setBleAppeared(boolean appeared) {
            return setFlag(FLAG_BLE_APPEARED, appeared);
        }

        @Override
        public boolean isBtConnected() {
            return (mFlags & FLAG_BT_CONNECTED) != 0;
        }

        public boolean setBtConnected(boolean connected) {
            return setFlag(FLAG_BT_CONNECTED, connected);
        }

        @Override
        public boolean isSelfManagedAppeared() {
            return (mFlags & FLAG_SELF_MANAGED_APPEARED) != 0;
        }

        public boolean setSelfManagedAppeared(boolean appeared) {
            return setFlag(FLAG_SELF_MANAGED_APPEARED, appeared);
        }

        @Override
        public boolean isSelfManagedNearby() {
            return (mFlags & FLAG_SELF_MANAGED_NEARBY) != 0;
        }

        public boolean setSelfManagedNearby(boolean nearby) {
            return setFlag(FLAG_SELF_MANAGED_NEARBY, nearby);
        }

        @Override
        public boolean isAssociationRemoved() {
            return (mFlags & FLAG_ASSOCIATION_REMOVED) != 0;
        }

        public boolean setAssociationRemoved(boolean removed) {
            return setFlag(FLAG_ASSOCIATION_REMOVED, removed);
        }

        private boolean isFlagSet(int flag) {
            return (mFlags & flag) != 0;
        }

        private boolean setFlag(int flag, boolean value) {
            if (isFlagSet(flag) == value) {
                return false;
            }
            if (value) {
                mFlags |= flag;
            } else {
                mFlags &= ~flag;
            }
            return true;
        }

        public void dump(IndentingPrintWriter pw) {
            pw.println("RemoteDevice:");
            pw.increaseIndent();
            pw.println("associationId=" + mAssociationId);
            pw.println("displayName='" + mDisplayName + "'");
            pw.println("deviceProfile='" + mDeviceProfile + "'");
            pw.println("hasTransport=" + hasTransport());
            pw.println("isBleAppeared=" + isBleAppeared());
            pw.println("isBtConnected=" + isBtConnected());
            pw.println("isSelfManagedAppeared=" + isSelfManagedAppeared());
            pw.println("isSelfManagedNearby=" + isSelfManagedNearby());
            pw.println("isAssociationRemoved=" + isAssociationRemoved());
            pw.decreaseIndent();
        }

        @Override
        public String toString() {
            return "RemoteDevice {"
                    + "associationId="
                    + mAssociationId
                    + ", displayName='"
                    + mDisplayName
                    + "'"
                    + ", deviceProfile='"
                    + mDeviceProfile
                    + "'"
                    + ", hasTransport="
                    + hasTransport()
                    + ", isBleAppeared="
                    + isBleAppeared()
                    + ", isBtConnected="
                    + isBtConnected()
                    + ", isSelfManagedAppeared="
                    + isSelfManagedAppeared()
                    + ", isSelfManagedNearby="
                    + isSelfManagedNearby()
                    + ", isAssociationRemoved="
                    + isAssociationRemoved()
                    + "}";
        }
    }

    /** A class representing a group of devices. */
    private class NetworkImpl implements Network {
        private final String mId;
        private final Predicate<RemoteDevice> mDeviceCondition;
        private final Predicate<AssociationInfo> mAssociationInfoCondition;

        @GuardedBy("mLock")
        private final Set<Integer> mAssociationIds = new HashSet<>();

        @GuardedBy("mLock")
        private final List<Pair<Executor, NetworkListener>> mListeners = new ArrayList<>();

        @GuardedBy("mLock")
        private boolean mDestroyed;

        NetworkImpl(
                String id,
                Predicate<RemoteDevice> deviceCondition,
                Predicate<AssociationInfo> associationInfoCondition) {
            mId = id;
            mDeviceCondition = deviceCondition;
            mAssociationInfoCondition = associationInfoCondition;
        }

        @Override
        public String getNetworkId() {
            return mId;
        }

        @Override
        public Map<Integer, RemoteDevice> getRemoteDevices() {
            synchronized (mLock) {
                return Maps.filterKeys(
                        NetworkManagerImpl.this.getRemoteDevices(), mAssociationIds::contains);
            }
        }

        @Override
        public long broadcastMessage(byte[] message, int flags) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "broadcastMessage: network " + mId + " is already destroyed");
                    return -1;
                }
                // TODO: implement
                return 0;
            }
        }

        @Override
        public long multicastMessage(List<Integer> associationIds, byte[] message, int flags) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "multicastMessage: network " + mId + " is already destroyed");
                    return -1;
                }
                // TODO: implmement
                return 0;
            }
        }

        @Override
        public void cancelMessage(long id) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "cancelMessage: network " + mId + " is already destroyed");
                    return;
                }
                // TODO: implmement
            }
        }

        @Override
        public void registerListener(Executor executor, NetworkListener listener) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "registerListener: network " + mId + " is already destroyed");
                    return;
                }
                mListeners.add(Pair.create(executor, listener));
            }
        }

        @Override
        public void unregisterListener(NetworkListener listener) {
            synchronized (mLock) {
                if (mDestroyed) {
                    Log.w(TAG, "unregisterListener: network " + mId + " is already destroyed");
                    return;
                }
                mListeners.removeIf(pair -> pair.second == listener);
            }
        }

        /** Re-evaluate devices in this network. */
        @GuardedBy("mLock")
        private void invalidateLocked(Map<Integer, AssociationInfo> associations) {
            for (RemoteDeviceImpl device : mRemoteDevices.values()) {
                int associationId = device.getAssociationId();
                AssociationInfo associationInfo = associations.get(associationId);
                if (associationInfo != null
                        && mDeviceCondition.test(device)
                        && mAssociationInfoCondition.test(associationInfo)
                        && !device.isAssociationRemoved()) {
                    if (mAssociationIds.add(associationId)) {
                        notifyListenersLocked(l -> l.onDeviceChanged(associationId));
                        Log.i(TAG, "Device " + device + " joined network " + mId);
                    }
                } else if (mAssociationIds.remove(associationId)) {
                    notifyListenersLocked(l -> l.onDeviceRemoved(associationId));
                    Log.i(TAG, "Device " + device + " left network " + mId);
                }
            }
            Set<Integer> set = new ArraySet<>(mAssociationIds);
            mAssociationIds.retainAll(associations.keySet());
            set.removeAll(mAssociationIds);
            for (Integer removedId : set) {
                Log.i(TAG, "Device with id " + removedId + " left network " + mId);
                notifyListenersLocked(l -> l.onDeviceRemoved(removedId));
            }
        }

        @GuardedBy("mLock")
        private void notifyListenersLocked(Consumer<NetworkListener> listenerConsumer) {
            mListeners.forEach(
                    pair -> pair.first.execute(() -> listenerConsumer.accept(pair.second)));
        }

        @GuardedBy("mLock")
        private void notifyDeviceChangedLocked(int associationId) {
            if (mAssociationIds.contains(associationId)) {
                notifyListenersLocked(l -> l.onDeviceChanged(associationId));
            }
        }

        @Override
        public void destroy() {
            synchronized (mLock) {
                if (mDestroyed) {
                    return;
                }
                mDestroyed = true;
                mAssociationIds.clear();
                mListeners.clear();
                mNetworks.remove(mId);
                Log.i(TAG, "Destroyed network " + mId);
            }
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("NetworkImpl:");
            pw.increaseIndent();
            synchronized (mLock) {
                pw.println("id=" + mId);
                pw.println("destroyed=" + mDestroyed);
                pw.println("listeners=" + mListeners.size());
                pw.println("devices=" + mAssociationIds);
            }
            pw.decreaseIndent();
        }
    }
}
