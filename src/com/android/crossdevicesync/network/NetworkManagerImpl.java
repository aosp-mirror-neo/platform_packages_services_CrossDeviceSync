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
import android.util.IndentingPrintWriter;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.network.dagger.qualifier.NetworkLock;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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
                Log.w(TAG, "init: ignore duplicate init()");
                return;
            }
            mInitialized = true;
            addOrRemoveRemoteDeviceLocked(
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
            if (!mInitialized) {
                Log.w(TAG, "destroy: ignore destroy() since not initialized");
                return;
            }
            mInitialized = false;
            mCompanionDeviceManager.removeOnAssociationsChangedListener(mOnAssociationsChanged);
            mCompanionDeviceManager.removeOnTransportsChangedListener(mOnTransportChanged);
            mCompanionDeviceManager.removeOnDevicePresenceEventListener(mContext.getPackageName());
            mRemoteDevices.clear();
        }
    }

    private void onAssociationsChanged(List<AssociationInfo> associations) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onAssociationsChanged: ignore since not initialized");
                return;
            }
            addOrRemoveRemoteDeviceLocked(associations);
        }
    }

    @GuardedBy("mLock")
    private void addOrRemoveRemoteDeviceLocked(List<AssociationInfo> allInfo) {
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
            for (RemoteDeviceImpl device : mRemoteDevices.values()) {
                boolean hasTransport = idsWithTransport.contains(device.getAssociationId());
                if (device.setHasTransport(hasTransport)) {
                    Log.d(
                            TAG,
                            "updateTransportFlag: "
                                    + device
                                    + (device.hasTransport() ? " established" : " lost")
                                    + " transport");
                }
            }
        }
    }

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
                Log.d(TAG, "Device presence flag changed for " + device + ", event: " + event);
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
            pw.decreaseIndent();
        }
    }

    /** A class representing a remote device. */
    private class RemoteDeviceImpl implements RemoteDevice {
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
}
