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

import com.android.crossdevicesync.common.Dumpable;

import java.util.Map;

/** Interface for managing cross-device connections in the sync network. */
public interface NetworkManager extends Dumpable {

    /** Initialize network manager. */
    void init();

    /** Destroy the network manager. */
    void destroy();

    /** Returns the mapping of association ID to the remote device. */
    Map<Integer, RemoteDevice> getRemoteDevices();

    /** An interface representing a remote device. */
    interface RemoteDevice extends Dumpable {
        /** Returns the association ID. */
        int getAssociationId();

        /** Returns the display name. */
        CharSequence getDisplayName();

        /** Returns the device profile. */
        String getDeviceProfile();

        /** Returns {@code true} if the device has transport. */
        boolean hasTransport();

        /** Returns {@code true} if the device has BLE appeared. */
        boolean isBleAppeared();

        /** Returns {@code true} if the device has BT connected. */
        boolean isBtConnected();

        /** Returns {@code true} if the device has self-managed appeared. */
        boolean isSelfManagedAppeared();

        /** Returns {@code true} if the device is self-managed nearby. */
        boolean isSelfManagedNearby();

        /** Returns {@code true} if the association is removed. */
        boolean isAssociationRemoved();
    }
}
