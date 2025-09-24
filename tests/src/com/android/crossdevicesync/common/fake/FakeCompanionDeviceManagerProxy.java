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
package com.android.crossdevicesync.common.fake;

import android.annotation.UserIdInt;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager.OnAssociationsChangedListener;

import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

/** A fake implementation of {@link CompanionDeviceManagerProxy} for testing. */
@Singleton
public class FakeCompanionDeviceManagerProxy implements CompanionDeviceManagerProxy {

    private final Map<Integer, List<AssociationInfo>> mAssociations = new HashMap<>();
    private final List<OnAssociationsChangedListener> mAssociationListeners = new ArrayList<>();
    private final List<Consumer<List<AssociationInfo>>> mTransportListeners = new ArrayList<>();
    private final Map<Integer, List<BiConsumer<Integer, byte[]>>> mMessageListeners =
            new HashMap<>();
    private final List<SentMessage> mSentMessages = new ArrayList<>();

    @Inject
    FakeCompanionDeviceManagerProxy() {}

    @Override
    public List<AssociationInfo> getAllAssociations(@UserIdInt int userId) {
        return mAssociations.getOrDefault(userId, Collections.emptyList());
    }

    @Override
    public void addOnAssociationsChangedListener(
            Executor executor, OnAssociationsChangedListener listener, @UserIdInt int userId) {
        mAssociationListeners.add(listener);
    }

    @Override
    public void removeOnAssociationsChangedListener(OnAssociationsChangedListener listener) {
        mAssociationListeners.remove(listener);
    }

    @Override
    public void addOnTransportsChangedListener(
            Executor executor, Consumer<List<AssociationInfo>> listener) {
        mTransportListeners.add(listener);
    }

    @Override
    public void removeOnTransportsChangedListener(Consumer<List<AssociationInfo>> listener) {
        mTransportListeners.remove(listener);
    }

    @Override
    public void sendMessage(int messageType, byte[] data, int[] associationIds) {
        mSentMessages.add(new SentMessage(messageType, data, associationIds));
    }

    @Override
    public void addOnMessageReceivedListener(
            Executor executor, int messageType, BiConsumer<Integer, byte[]> listener) {
        mMessageListeners.computeIfAbsent(messageType, k -> new ArrayList<>()).add(listener);
    }

    @Override
    public void removeOnMessageReceivedListener(
            int messageType, BiConsumer<Integer, byte[]> listener) {
        if (mMessageListeners.containsKey(messageType)) {
            mMessageListeners.get(messageType).remove(listener);
        }
    }

    /** Adds an association for the given user and notifies listeners. */
    public void addAssociationForUser(@UserIdInt int userId, AssociationInfo associationInfo) {
        mAssociations.computeIfAbsent(userId, k -> new ArrayList<>()).add(associationInfo);
        for (OnAssociationsChangedListener listener : mAssociationListeners) {
            listener.onAssociationsChanged(getAllAssociations(userId));
        }
    }

    /** Simulates receiving a message and notifies the appropriate listeners. */
    public void simulateMessageReceived(int messageType, int associationId, byte[] data) {
        if (mMessageListeners.containsKey(messageType)) {
            for (BiConsumer<Integer, byte[]> listener : mMessageListeners.get(messageType)) {
                listener.accept(associationId, data);
            }
        }
    }

    /** Returns the list of messages sent via {@link #sendMessage}. */
    public List<SentMessage> getSentMessages() {
        return mSentMessages;
    }

    /** Clears the list of sent messages. */
    public void clearSentMessages() {
        mSentMessages.clear();
    }

    /** A container for a message sent via {@link #sendMessage}. */
    public static class SentMessage {
        public final int messageType;
        public final byte[] data;
        public final int[] associationIds;

        SentMessage(int messageType, byte[] data, int[] associationIds) {
            this.messageType = messageType;
            this.data = data;
            this.associationIds = associationIds;
        }
    }
}
