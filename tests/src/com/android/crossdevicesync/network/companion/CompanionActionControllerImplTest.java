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

import static com.android.crossdevicesync.common.Utils.isFutureFailed;
import static com.android.crossdevicesync.common.Utils.isFutureSucceeded;

import static com.google.common.truth.Truth.assertThat;

import android.companion.ActionRequest;
import android.companion.AssociationInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompanionActionControllerImplTest extends SyncServiceComponentTestBase {
    private static int ASSOCIATION1_ID = 1;
    private static int ASSOCIATION2_ID = 2;

    @Before
    public void setUp() {
        super.setUp();
        mCompanionActionController.init();
        mFakeCompanionDeviceManagerProxy.addAssociation(createAssociationInfo(ASSOCIATION1_ID));
        mFakeCompanionDeviceManagerProxy.addAssociation(createAssociationInfo(ASSOCIATION2_ID));
    }

    @Test
    public void startScanning() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_duplicateCall() {
        ListenableFuture<?> future1 =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        ListenableFuture<?> future2 =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();
    }

    @Test
    public void startScanning_alreadyScanning() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Another scanning request should succeed immediately.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_cancel() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();
        // Cancel the future won't cancel a request that's already sent to CDM.
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Timeout should not matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Result comes after cancel. Future remain cancelled.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(future.isCancelled()).isTrue();

        // Next request should succeed immediately.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_timeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed, but the CDM request remains active.
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();
    }

    @Test
    public void startScanning_alreadyTimeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Scan again.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Receive result now.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);

        // Future receives result.
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_fail() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Fail
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);

        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void startScanning_alreadyFailed() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureFailed(future)).isTrue();

        // Scanning again.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning_alreadyStopped() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Fail later.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);

        // Stop should succeed immediately.
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning_alreadyCancelled() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();

        // Stop again and can still stop scanning.
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning_duplicateCall() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        ListenableFuture<?> future1 =
                mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        ListenableFuture<?> future2 =
                mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future1)).isTrue();
    }

    @Test
    public void stopScanning_notStarted() {
        ListenableFuture<?> future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Succeed immediately.
        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();
    }

    @Test
    public void stopScanning_cancel() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();

        // Timeout doesn't matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    public void stopScanning_timeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed.
        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void stopScanning_alreadyTimeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Stop again.
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_duplicateCall() {
        ListenableFuture<?> future1 =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        ListenableFuture<?> future2 =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();
    }

    @Test
    public void startAdvertising_alreadyAdvertising() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Another advertising request should succeed immediately.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_cancel() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();
        // Cancel the future won't cancel a request that's already sent to CDM.
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Timeout should not matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Result comes after cancel. Future remain cancelled.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(future.isCancelled()).isTrue();

        // Next request should succeed immediately.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_timeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed, but the CDM request remains active.
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();
    }

    @Test
    public void startAdvertising_alreadyTimeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Scan again.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Receive result now.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);

        // Future receives result.
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_fail() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Fail
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);

        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void startAdvertising_alreadyFailed() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureFailed(future)).isTrue();

        // Advertising again.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising_alreadyStopped() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Fail later.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);

        // Stop should succeed immediately.
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising_alreadyCancelled() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();

        // Stop again and can still stop advertising.
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising_duplicateCall() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        ListenableFuture<?> future1 =
                mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        ListenableFuture<?> future2 =
                mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future1)).isTrue();
    }

    @Test
    public void stopAdvertising_notStarted() {
        ListenableFuture<?> future =
                mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Succeed immediately.
        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();
    }

    @Test
    public void stopAdvertising_cancel() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        // Timeout doesn't matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    public void stopAdvertising_timeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed.
        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void stopAdvertising_alreadyTimeout() {
        ListenableFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Stop again.
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_duplicateCall() {
        ListenableFuture<?> future1 = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        ListenableFuture<?> future2 = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();
    }

    @Test
    public void attachTransport_alreadyAdvertising() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Another advertising request should succeed immediately.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_cancel() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();
        // Cancel the future won't cancel a request that's already sent to CDM.
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Timeout should not matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Result comes after cancel. Future remain cancelled.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(future.isCancelled()).isTrue();

        // Next request should succeed immediately.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_timeout() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed, but the CDM request remains active.
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();
    }

    @Test
    public void attachTransport_alreadyTimeout() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Scan again.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Receive result now.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);

        // Future receives result.
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_fail() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Fail
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);

        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void attachTransport_alreadyFailed() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureFailed(future)).isTrue();

        // Advertising again.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport_alreadyStopped() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Fail later.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);

        // Stop should succeed immediately.
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport_alreadyCancelled() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();

        // Stop again and can still stop advertising.
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport_duplicateCall() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        ListenableFuture<?> future1 = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        ListenableFuture<?> future2 = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future1)).isTrue();
    }

    @Test
    public void detachTransport_notStarted() {
        ListenableFuture<?> future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Succeed immediately.
        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();
    }

    @Test
    public void detachTransport_cancel() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Timeout doesn't matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    public void detachTransport_timeout() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed.
        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void detachTransport_alreadyTimeout() {
        ListenableFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Stop again.
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void concurrentRequestsAcrossAssociations() {
        // Start scanning on ASSOCIATION1_ID
        ListenableFuture<?> scanFuture1 =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(scanFuture1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Start advertising on ASSOCIATION2_ID
        ListenableFuture<?> advertiseFuture2 =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION2_ID);
        assertThat(advertiseFuture2.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Attach transport on ASSOCIATION1_ID
        ListenableFuture<?> transportFuture1 =
                mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(transportFuture1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Simulate success for scanning on ASSOCIATION1_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(scanFuture1)).isTrue();
        assertThat(advertiseFuture2.isDone()).isFalse(); // Other futures should be unaffected
        assertThat(transportFuture1.isDone()).isFalse(); // Other futures should be unaffected

        // Simulate success for advertising on ASSOCIATION2_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION2_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(advertiseFuture2)).isTrue();
        assertThat(transportFuture1.isDone()).isFalse(); // Other futures should be unaffected

        // Simulate success for transport on ASSOCIATION1_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(transportFuture1)).isTrue();

        // Now stop some actions concurrently
        ListenableFuture<?> stopAdvertiseFuture2 =
                mCompanionActionController.stopNearbyAdvertising(ASSOCIATION2_ID);
        assertThat(stopAdvertiseFuture2.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        ListenableFuture<?> detachTransportFuture1 =
                mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(detachTransportFuture1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Simulate success for stopping advertising on ASSOCIATION2_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION2_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(stopAdvertiseFuture2)).isTrue();
        assertThat(detachTransportFuture1.isDone()).isFalse(); // Other futures unaffected

        // Simulate success for detaching transport on ASSOCIATION1_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(detachTransportFuture1)).isTrue();
    }

    @Test
    public void destroy_deactivateAllRequests() {
        // Start scanning on ASSOCIATION1_ID
        mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();
        // Start advertising on ASSOCIATION2_ID
        mCompanionActionController.startNearbyAdvertising(ASSOCIATION2_ID);
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();
        // Attach transport on ASSOCIATION1_ID
        mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Destroy
        mCompanionActionController.destroy();

        // No more requests should be active
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                mContext.getPackageName(),
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();
    }

    private AssociationInfo createAssociationInfo(int id) {
        return new AssociationInfo.Builder(id, mContext.getUserId(), "com.android.crossdevicesync")
                .setDisplayName("displayName" + id)
                .build();
    }
}
