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
package com.android.crossdevicesync.data;

import java.util.concurrent.Executor;

/** Interface for data layer code to interact with the sync network. */
public interface INetwork {

    /**
     * Broadcast a message to the sync network. The network interface should make a best-effort to
     * send the update messages to other nodes on the network, retrying as desired.
     */
    void broadcastMessage(byte[] message);

    /**
     * Unicast a message to a specific device.The network interface should make a best-effort to
     * send the update messages to other nodes on the network, retrying as desired.
     */
    void unicastMessage(String destDeviceNodeId, byte[] message);

    /** Register a listener to monitor incoming messages from the network. */
    void registerNetworkListener(Executor executor, NetworkListener listener);

    /** Unregister the listener. */
    void unregisterNetworkListener(NetworkListener listener);

    /** A listener for monitoring events from the network. */
    interface NetworkListener {

        /**
         * Called when a message is received from the network.
         *
         * @param nodeId the node id of the source device.
         * @param message the message.
         */
        void onNetworkMessage(String nodeId, byte[] message);

        /**
         * Called when a node appears on the network. This happens if the node is first seen, or it
         * gets reconnected after being disconnected.
         *
         * @param nodeId the node id of the appeared device.
         */
        void onNodeAppeared(String nodeId);
    }

    /** Interface for creating {@link INetwork} instances. */
    interface Factory {

        /** Create a network with the given local node ID. */
        INetwork create(String localNodeId);
    }
}
