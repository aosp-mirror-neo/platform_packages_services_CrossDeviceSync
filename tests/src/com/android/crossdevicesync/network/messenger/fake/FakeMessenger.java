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
package com.android.crossdevicesync.network.messenger.fake;

import com.android.crossdevicesync.network.messenger.Messenger;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.inject.Inject;

/** A fake implementation of {@link Messenger} for use in tests. */
@SyncServiceSingleton
public class FakeMessenger implements Messenger {

    private final Map<MessageListener, Executor> mListeners = new HashMap<>();

    private final List<SentMessage> mSentMessages = new ArrayList<>();
    private boolean mIsDestroyed = false;
    private boolean mIsInitialized = false;

    @Inject
    public FakeMessenger() {}

    @Override
    public void init() {
        if (mIsInitialized) {
            throw new IllegalStateException("Messenger is already initialized.");
        }
        mIsInitialized = true;
        mIsDestroyed = false;
    }

    @Override
    public void destroy() {
        if (!mIsInitialized) {
            throw new IllegalStateException("Messenger is not initialized.");
        }
        mIsDestroyed = true;
        mIsInitialized = false;
        mListeners.clear();
        mSentMessages.clear();
    }

    @Override
    public ListenableFuture<?> sendMessage(
            String networkId, int associationId, byte[] message, int maxDeliveryAttempts) {
        if (mIsDestroyed || !mIsInitialized) {
            throw new IllegalStateException("Messenger is destroyed or not initialized.");
        }
        SettableFuture<Void> future = SettableFuture.create();
        mSentMessages.add(
                new SentMessage(networkId, associationId, message, maxDeliveryAttempts, future));
        return future;
    }

    @Override
    public void registerMessageListener(Executor executor, MessageListener listener) {
        if (mIsDestroyed || !mIsInitialized) {
            throw new IllegalStateException("Messenger is destroyed or not initialized.");
        }
        mListeners.put(listener, executor);
    }

    @Override
    public void unregisterMessageListener(MessageListener listener) {
        if (mIsDestroyed || !mIsInitialized) {
            throw new IllegalStateException("Messenger is destroyed or not initialized.");
        }
        mListeners.remove(listener);
    }

    /** Simulates receiving a message and notifies listeners. */
    public void receiveMessage(String networkId, int associationId, byte[] message) {
        if (mIsDestroyed || !mIsInitialized) {
            throw new IllegalStateException("Messenger is destroyed or not initialized.");
        }
        for (Map.Entry<MessageListener, Executor> entry : mListeners.entrySet()) {
            entry.getValue()
                    .execute(() -> entry.getKey().onMessage(networkId, associationId, message));
        }
    }

    /** Returns a list of all messages sent through this messenger. */
    public List<SentMessage> getSentMessages() {
        return mSentMessages;
    }

    /** Returns a set of the target association IDs for all messages sent through this messenger. */
    public Set<Integer> getTargetAssociationIds() {
        return mSentMessages.stream().map(s -> s.associationId).collect(Collectors.toSet());
    }

    /** A record of a message sent through the fake messenger. */
    public static class SentMessage {
        public final String networkId;
        public final int associationId;
        public final byte[] message;
        public final int maxDeliveryAttempts;
        private final SettableFuture<Void> mFuture;

        SentMessage(
                String networkId,
                int associationId,
                byte[] message,
                int maxDeliveryAttempts,
                SettableFuture<Void> future) {
            this.networkId = networkId;
            this.associationId = associationId;
            this.message = message;
            this.maxDeliveryAttempts = maxDeliveryAttempts;
            this.mFuture = future;
        }

        /** Completes the future for this message successfully. */
        public void completeSuccessfully() {
            mFuture.set(null);
        }

        /** Completes the future for this message with an exception. */
        public void completeWithException(Throwable t) {
            mFuture.setException(t);
        }

        public SettableFuture<Void> getFuture() {
            return mFuture;
        }
    }
}
