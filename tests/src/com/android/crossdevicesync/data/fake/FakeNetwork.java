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
package com.android.crossdevicesync.data.fake;

import android.util.Pair;

import com.android.crossdevicesync.data.INetwork;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;

/** Fake implementation of {@link INetwork}. */
public class FakeNetwork implements INetwork {
    private final String mNodeId;
    private final List<Pair<Executor, NetworkListener>> mListeners = new ArrayList<>();
    private final Set<FakeNetwork> mRemoteNetworks = new HashSet<>();

    private final Queue<Runnable> mMessageBuffer = new ArrayDeque<>();

    public FakeNetwork(String nodeId) {
        mNodeId = nodeId;
    }

    /** Connect to a remote network. */
    public void connect(FakeNetwork remoteNetwork) {
        mRemoteNetworks.add(remoteNetwork);
        mListeners.forEach(
                l -> l.first.execute(() -> l.second.onNodeAppeared(remoteNetwork.getNodeId())));
    }

    @Override
    public void broadcastMessage(byte[] message) {
        mRemoteNetworks.forEach(n -> mMessageBuffer.add(() -> n.receiveMessage(mNodeId, message)));
    }

    @Override
    public void unicastMessage(String destDeviceNodeId, byte[] message) {
        FakeNetwork network = null;
        for (FakeNetwork n : mRemoteNetworks) {
            if (n.getNodeId().equals(destDeviceNodeId)) {
                network = n;
                break;
            }
        }
        if (network != null) {
            final FakeNetwork finalNetwork = network;
            mMessageBuffer.add(() -> finalNetwork.receiveMessage(mNodeId, message));
        }
    }

    @Override
    public void registerNetworkListener(Executor executor, NetworkListener listener) {
        mListeners.add(new Pair<>(executor, listener));
    }

    @Override
    public void unregisterNetworkListener(NetworkListener listener) {
        mListeners.removeIf(l -> l.second == listener);
    }

    /** Receive message from the network. */
    public void receiveMessage(String srcDeviceNodeId, byte[] message) {
        mListeners.forEach(
                l -> l.first.execute(() -> l.second.onNetworkMessage(srcDeviceNodeId, message)));
    }

    public String getNodeId() {
        return mNodeId;
    }

    /** Flush all in-flight network messages initiated from local device. */
    public boolean flushMessages() {
        boolean hasMessage = false;
        while (!mMessageBuffer.isEmpty()) {
            mMessageBuffer.poll().run();
            hasMessage = true;
        }
        return hasMessage;
    }

    public static class Factory implements INetwork.Factory {

        private final Map<String, FakeNetwork> mInstances = new HashMap<>();

        @Override
        public FakeNetwork create(String nodeId) {
            return mInstances.computeIfAbsent(nodeId, FakeNetwork::new);
        }
    }
}
