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
package com.android.crossdevicesync.network.messenger;

import android.companion.CompanionDeviceManager;

import androidx.annotation.GuardedBy;

import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.dagger.qualifier.Main;
import com.android.crossdevicesync.network.dagger.qualifier.NetworkLock;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import javax.inject.Inject;

/** Implementation of {@link Messenger} using CompanionDeviceManager. */
@SyncServiceSingleton
public class MessengerImpl implements Messenger {
    private static final String TAG = "Messenger";
    private final Object mLock;
    private final CompanionDeviceManagerProxy mCompanionDeviceManager;
    private final Executor mMainExecutor;
    private final BiConsumer<Integer, byte[]> mMessageListener = this::onMessage;

    @GuardedBy("mLock")
    private boolean mInitialized;

    @Inject
    MessengerImpl(
            @NetworkLock Object lock,
            CompanionDeviceManagerProxy companionDeviceManager,
            @Main Executor mainExecutor) {
        mLock = lock;
        mCompanionDeviceManager = companionDeviceManager;
        mMainExecutor = mainExecutor;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mInitialized) {
                throw new IllegalStateException("Already initialized!");
            }
            mInitialized = true;
            mCompanionDeviceManager.addOnMessageReceivedListener(
                    mMainExecutor,
                    CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                    mMessageListener);
        }
    }

    @Override
    public void destroy() {
        synchronized (mLock) {
            if (!mInitialized) {
                throw new IllegalStateException("Not initialized!");
            }
            mInitialized = false;
            mCompanionDeviceManager.removeOnMessageReceivedListener(
                    CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC, mMessageListener);
        }
    }

    @Override
    public ListenableFuture<?> sendMessage(
            String networkId, int associationId, byte[] message, int maxDeliveryAttempts) {
        // TODO: implement
        return null;
    }

    @Override
    public void registerMessageListener(Executor executor, MessageListener listener) {
        // TODO: implement
    }

    @Override
    public void unregisterMessageListener(MessageListener listener) {
        // TODO: implement
    }

    private void onMessage(int associationId, byte[] message) {
        // TODO: implement
    }
}
