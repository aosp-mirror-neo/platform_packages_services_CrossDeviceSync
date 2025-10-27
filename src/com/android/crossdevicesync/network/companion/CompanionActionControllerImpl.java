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
package com.android.crossdevicesync.network.companion;

import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;

/** Implementation of {@link CompanionActionController}. */
@SyncServiceSingleton
public class CompanionActionControllerImpl implements CompanionActionController {

    @Inject
    CompanionActionControllerImpl() {}

    @Override
    public ListenableFuture<?> attachTransport(int associationId) {
        return null;
    }

    @Override
    public ListenableFuture<?> detachTransport(int associationId) {
        return null;
    }

    @Override
    public ListenableFuture<?> startNearbyScanning(int associationId) {
        return null;
    }

    @Override
    public ListenableFuture<?> stopNearbyScanning(int associationId) {
        return null;
    }

    @Override
    public ListenableFuture<?> startNearbyAdvertising(int associationId) {
        return null;
    }

    @Override
    public ListenableFuture<?> stopNearbyAdvertising(int associationId) {
        return null;
    }
}
