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
package com.android.crossdevicesync.network.companion.fake;

import static com.android.crossdevicesync.common.Utils.isFutureFailed;

import android.util.IndentingPrintWriter;

import androidx.annotation.Nullable;

import com.android.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy;
import com.android.crossdevicesync.network.companion.CompanionActionController;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/** A fake implementation of {@link CompanionActionController}. */
@SyncServiceSingleton
public class FakeCompanionActionController implements CompanionActionController {
    private final FakeCompanionDeviceManagerProxy mCompanionDeviceManager;
    private final Map<Integer, SettableFuture<?>> mTransportRequests = new HashMap<>();
    private final Map<Integer, SettableFuture<?>> mScanningRequests = new HashMap<>();
    private final Map<Integer, SettableFuture<?>> mAdvertisingRequests = new HashMap<>();
    private boolean mAutomaticallyAttachTransport;
    private boolean mInitialized;

    @Inject
    FakeCompanionActionController(FakeCompanionDeviceManagerProxy companionDeviceManager) {
        mCompanionDeviceManager = companionDeviceManager;
    }

    public void setAutomaticallyAttachTransport(boolean enabled) {
        mAutomaticallyAttachTransport = enabled;
    }

    @Override
    public void init() {
        if (mInitialized) {
            return;
        }
        mInitialized = true;
    }

    @Override
    public void destroy() {
        if (!mInitialized) {
            return;
        }
        mInitialized = false;
        mTransportRequests.values().forEach(f -> f.setException(new RuntimeException("destroyed")));
        mTransportRequests.clear();
        mScanningRequests.values().forEach(f -> f.setException(new RuntimeException("destroyed")));
        mScanningRequests.clear();
        mAdvertisingRequests
                .values()
                .forEach(f -> f.setException(new RuntimeException("destroyed")));
        mAdvertisingRequests.clear();
    }

    private void throwIfUninitialized() {
        if (!mInitialized) {
            throw new IllegalStateException("Not initialized!");
        }
    }

    @Override
    public ListenableFuture<?> attachTransport(int associationId) {
        throwIfUninitialized();
        SettableFuture<?> future = newFutureIfAbsentOrFailed(mTransportRequests, associationId);
        if (mAutomaticallyAttachTransport && !future.isDone()) {
            mCompanionDeviceManager.setTransportAttachedState(associationId, true);
            future.set(null);
        }
        return future;
    }

    @Override
    public ListenableFuture<?> detachTransport(int associationId) {
        throwIfUninitialized();
        SettableFuture<?> future = mTransportRequests.remove(associationId);
        if (future != null) {
            future.cancel(true);
        }
        return Futures.immediateFuture(true);
    }

    @Nullable
    public SettableFuture<?> getAttachTransportFuture(int associationId) {
        throwIfUninitialized();
        return mTransportRequests.get(associationId);
    }

    @Override
    public ListenableFuture<?> startNearbyScanning(int associationId) {
        throwIfUninitialized();
        return newFutureIfAbsentOrFailed(mScanningRequests, associationId);
    }

    @Override
    public ListenableFuture<?> stopNearbyScanning(int associationId) {
        throwIfUninitialized();
        SettableFuture<?> future = mScanningRequests.remove(associationId);
        if (future != null) {
            future.cancel(true);
        }
        return Futures.immediateFuture(true);
    }

    @Nullable
    public SettableFuture<?> getScanningFuture(int associationId) {
        throwIfUninitialized();
        return mScanningRequests.get(associationId);
    }

    @Override
    public ListenableFuture<?> startNearbyAdvertising(int associationId) {
        throwIfUninitialized();
        return newFutureIfAbsentOrFailed(mAdvertisingRequests, associationId);
    }

    @Override
    public ListenableFuture<?> stopNearbyAdvertising(int associationId) {
        throwIfUninitialized();
        SettableFuture<?> future = mAdvertisingRequests.remove(associationId);
        if (future != null) {
            future.cancel(true);
        }
        return Futures.immediateFuture(true);
    }

    @Nullable
    public SettableFuture<?> getAdvertisingFuture(int associationId) {
        throwIfUninitialized();
        return mAdvertisingRequests.get(associationId);
    }

    private static SettableFuture<?> newFutureIfAbsentOrFailed(
            Map<Integer, SettableFuture<?>> map, int associationId) {
        SettableFuture<?> future = map.get(associationId);
        if (future == null || (future.isDone() && isFutureFailed(future))) {
            future = SettableFuture.create();
            map.put(associationId, future);
        }
        return future;
    }

    @Override
    public void dump(IndentingPrintWriter pw) {}
}
