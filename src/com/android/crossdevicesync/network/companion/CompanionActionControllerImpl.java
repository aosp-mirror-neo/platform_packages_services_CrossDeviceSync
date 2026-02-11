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

import static com.android.crossdevicesync.common.Utils.toIntArray;

import static java.util.Objects.requireNonNull;

import android.companion.ActionRequest;
import android.companion.ActionResult;
import android.content.Context;
import android.os.Trace;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.crossdevicesync.common.Clock;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.DelayedExecutor;
import com.android.crossdevicesync.common.Dumpable;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Implementation of {@link CompanionActionController}. */
public class CompanionActionControllerImpl implements CompanionActionController {
    private static final String TAG = "CompanionActionCtrl";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    @VisibleForTesting static final long WAITING_TIMEOUT_MS = 2000;

    private final Object mLock;
    private final String mPackageName;
    private final CompanionDeviceManagerProxy mCompanionDeviceManager;
    private final DelayedExecutor mMainExecutor;
    private final Clock mClock;
    private final Runnable mInvalidateRunnable = this::doInvalidation;

    @GuardedBy("mLock")
    private final Map<Integer, AssociationState> mAssociationStates = new HashMap<>();

    @GuardedBy("mLock")
    private final Set<Integer> mListeningAssociations = new HashSet<>();

    @GuardedBy("mLock")
    private boolean mInitialized;

    @GuardedBy("mLock")
    private long mNextInvalidationTime = Long.MAX_VALUE;

    public CompanionActionControllerImpl(
            Object networkLock,
            Context context,
            CompanionDeviceManagerProxy companionDeviceManager,
            DelayedExecutor mainExecutor,
            Clock clock) {
        mLock = networkLock;
        mPackageName = context.getPackageName();
        mCompanionDeviceManager = companionDeviceManager;
        mMainExecutor = mainExecutor;
        mClock = clock;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mInitialized) {
                throw new IllegalStateException("Already initialized!");
            }
            mInitialized = true;
        }
    }

    @Override
    public void destroy() {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            mInitialized = false;
            long now = mClock.elapsedRealtime();
            clearAllActionRequestsLocked();
            mAssociationStates.values().forEach(state -> state.destroy(now));
            mAssociationStates.clear();
            mListeningAssociations.clear();
            mCompanionDeviceManager.clearOnActionResultListener(mPackageName);
            mNextInvalidationTime = Long.MAX_VALUE;
            mMainExecutor.cancel(mInvalidateRunnable);
        }
    }

    @GuardedBy("mLock")
    private void clearAllActionRequestsLocked() {
        int[] associationIds = toIntArray(mAssociationStates.keySet());
        mCompanionDeviceManager.requestAction(
                new ActionRequest.Builder(
                                ActionRequest.REQUEST_TRANSPORT, ActionRequest.OP_DEACTIVATE)
                        .build(),
                mPackageName,
                associationIds);
        mCompanionDeviceManager.requestAction(
                new ActionRequest.Builder(
                                ActionRequest.REQUEST_NEARBY_SCANNING, ActionRequest.OP_DEACTIVATE)
                        .build(),
                mPackageName,
                associationIds);
        mCompanionDeviceManager.requestAction(
                new ActionRequest.Builder(
                                ActionRequest.REQUEST_NEARBY_ADVERTISING,
                                ActionRequest.OP_DEACTIVATE)
                        .build(),
                mPackageName,
                associationIds);
    }

    @Override
    public ListenableFuture<?> attachTransport(int associationId) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            long now = mClock.elapsedRealtime();
            AssociationState state = getOrCreateAssociationStateLocked(associationId, now);
            return requestStateChangeLocked(state.transportState, /* activate= */ true, now);
        }
    }

    @Override
    public ListenableFuture<?> detachTransport(int associationId) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            long now = mClock.elapsedRealtime();
            AssociationState state = getOrCreateAssociationStateLocked(associationId, now);
            return requestStateChangeLocked(state.transportState, /* activate= */ false, now);
        }
    }

    @Override
    public ListenableFuture<?> startNearbyScanning(int associationId) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            long now = mClock.elapsedRealtime();
            AssociationState state = getOrCreateAssociationStateLocked(associationId, now);
            return requestStateChangeLocked(state.scanningState, /* activate= */ true, now);
        }
    }

    @Override
    public ListenableFuture<?> stopNearbyScanning(int associationId) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            long now = mClock.elapsedRealtime();
            AssociationState state = getOrCreateAssociationStateLocked(associationId, now);
            return requestStateChangeLocked(state.scanningState, /* activate= */ false, now);
        }
    }

    @Override
    public ListenableFuture<?> startNearbyAdvertising(int associationId) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            long now = mClock.elapsedRealtime();
            AssociationState state = getOrCreateAssociationStateLocked(associationId, now);
            return requestStateChangeLocked(state.advertisingState, /* activate= */ true, now);
        }
    }

    @Override
    public ListenableFuture<?> stopNearbyAdvertising(int associationId) {
        synchronized (mLock) {
            throwIfUninitializedLocked();
            long now = mClock.elapsedRealtime();
            AssociationState state = getOrCreateAssociationStateLocked(associationId, now);
            return requestStateChangeLocked(state.advertisingState, /* activate= */ false, now);
        }
    }

    @GuardedBy("mLock")
    private void throwIfUninitializedLocked() {
        if (!mInitialized) {
            throw new IllegalStateException("Not initialized!");
        }
    }

    @GuardedBy("mLock")
    private AssociationState getOrCreateAssociationStateLocked(int associationId, long now) {
        return mAssociationStates.computeIfAbsent(
                associationId, k -> new AssociationState(associationId, now));
    }

    @GuardedBy("mLock")
    private ListenableFuture<?> requestStateChangeLocked(
            ActionState state, boolean activate, long now) {
        ListenableFuture<?> future = state.noteRequest(activate, now);
        future.addListener(
                () -> {
                    if (!future.isCancelled()) {
                        return;
                    }
                    synchronized (mLock) {
                        if (state.noteCancellation(
                                future, mClock.elapsedRealtime(), "cancelled by client")) {
                            invalidate();
                        }
                    }
                },
                Runnable::run);
        invalidate();
        return future;
    }

    private void onActionResult(int associationId, ActionResult result) {
        synchronized (mLock) {
            if (!mInitialized) {
                Log.w(TAG, "onActionResult: ignore since not initialized");
                return;
            }
            AssociationState state = mAssociationStates.get(associationId);
            if (state == null) {
                Log.w(TAG, "Received result for unknown association: " + associationId);
                return;
            }
            Log.d(TAG, "onActionResult: associationId=" + associationId + ", result=" + result);
            ActionState actionState =
                    switch (result.getAction()) {
                        case ActionRequest.REQUEST_NEARBY_SCANNING -> state.scanningState;
                        case ActionRequest.REQUEST_NEARBY_ADVERTISING -> state.advertisingState;
                        case ActionRequest.REQUEST_TRANSPORT -> state.transportState;
                        default ->
                                throw new IllegalStateException(
                                        "Unknown action: " + result.getAction());
                    };
            if (actionState.noteActionResult(mClock.elapsedRealtime(), isActivated(result))) {
                invalidate();
            }
        }
    }

    private static boolean isActivated(ActionResult result) {
        return result.getResultCode() == ActionResult.RESULT_ACTIVATED;
    }

    private void invalidate() {
        invalidate(/* delay= */ 0);
    }

    private void invalidate(long delay) {
        synchronized (mLock) {
            if (!mInitialized) {
                return;
            }
            long now = mClock.elapsedRealtime();
            delay = Math.max(delay, 0);
            long scheduleTime = now + delay;
            if (scheduleTime >= mNextInvalidationTime) {
                return;
            }
            mNextInvalidationTime = scheduleTime;
            if (DEBUG && delay > 0) {
                Log.d(TAG, "Invalidating in " + delay + " ms");
            }
            mMainExecutor.cancel(mInvalidateRunnable);
            mMainExecutor.executeDelayed(mInvalidateRunnable, delay);
        }
    }

    private void doInvalidation() {
        Trace.beginSection("CompanionActionControllerImpl.doInvalidation");
        try {
            synchronized (mLock) {
                if (!mInitialized) {
                    return;
                }
                final long now = mClock.elapsedRealtime();
                if (now < mNextInvalidationTime) {
                    return;
                }
                if (DEBUG) {
                    Log.d(TAG, "doInvalidation: now=" + now);
                }
                mNextInvalidationTime = Long.MAX_VALUE;
                maybeTimeoutRequestsLocked(now);
                mAssociationStates.values().removeIf(AssociationState::allDeactivated);
                maybeUpdateActionResultListenerLocked();
                maybeSendActionRequestLocked(
                        now, s -> s.transportState, ActionRequest.REQUEST_TRANSPORT);
                maybeSendActionRequestLocked(
                        now, s -> s.scanningState, ActionRequest.REQUEST_NEARBY_SCANNING);
                maybeSendActionRequestLocked(
                        now, s -> s.advertisingState, ActionRequest.REQUEST_NEARBY_ADVERTISING);
                long nextInvalidation = getNextInvalidationTimeLocked();
                if (nextInvalidation != Long.MAX_VALUE) {
                    invalidate(nextInvalidation - now);
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    @GuardedBy("mLock")
    private void maybeTimeoutRequestsLocked(long now) {
        for (AssociationState associationState : mAssociationStates.values()) {
            int associationId = associationState.associationId;
            maybeTimeoutRequest(associationState.transportState, now, associationId, "transport");
            maybeTimeoutRequest(associationState.scanningState, now, associationId, "scanning");
            maybeTimeoutRequest(
                    associationState.advertisingState, now, associationId, "advertising");
        }
    }

    private static void maybeTimeoutRequest(
            ActionState s, long now, long associationId, String actionName) {
        if (now >= s.getFutureExpirationTimestamp()) {
            boolean requestedActivation = s.hasRequestedActivation();
            if (s.noteCancellation(now, "timeout")) {
                if (DEBUG) {
                    Log.w(
                            TAG,
                            "Timeout waiting for "
                                    + (requestedActivation ? "activating " : "deactivating ")
                                    + actionName
                                    + " for association "
                                    + associationId);
                }
            }
        }
    }

    @GuardedBy("mLock")
    private void maybeSendActionRequestLocked(
            long now, Function<AssociationState, ActionState> actionStateFetcher, int action) {
        Set<Integer> associationIdsToActivate = new ArraySet<>();
        Set<Integer> associationIdsToDeactivate = new ArraySet<>();
        for (AssociationState associationState : mAssociationStates.values()) {
            ActionState state = actionStateFetcher.apply(associationState);
            if (state.noteRequestSent(now)) {
                if (state.hasRequestedActivation()) {
                    associationIdsToActivate.add(associationState.associationId);
                } else {
                    associationIdsToDeactivate.add(associationState.associationId);
                }
            }
        }
        if (!associationIdsToActivate.isEmpty()) {
            Log.d(
                    TAG,
                    "Requesting activation for "
                            + actionToString(action)
                            + " on associations"
                            + associationIdsToActivate);
            mCompanionDeviceManager.requestAction(
                    new ActionRequest.Builder(action, ActionRequest.OP_ACTIVATE).build(),
                    mPackageName,
                    toIntArray(associationIdsToActivate));
        }
        if (!associationIdsToDeactivate.isEmpty()) {
            Log.d(
                    TAG,
                    "Requesting deactivation for "
                            + actionToString(action)
                            + " on associations"
                            + associationIdsToDeactivate);
            mCompanionDeviceManager.requestAction(
                    new ActionRequest.Builder(action, ActionRequest.OP_DEACTIVATE).build(),
                    mPackageName,
                    toIntArray(associationIdsToDeactivate));
        }
    }

    @GuardedBy("mLock")
    private void maybeUpdateActionResultListenerLocked() {
        Set<Integer> associationIdsToListen = new ArraySet<>();
        for (AssociationState associationState : mAssociationStates.values()) {
            if (!associationState.allDeactivated()) {
                associationIdsToListen.add(associationState.associationId);
            }
        }
        if (associationIdsToListen.equals(mListeningAssociations)) {
            return;
        }
        mListeningAssociations.clear();
        mListeningAssociations.addAll(associationIdsToListen);
        if (mListeningAssociations.isEmpty()) {
            if (DEBUG) {
                Log.d(TAG, "Clearing action result listener.");
            }
            mCompanionDeviceManager.clearOnActionResultListener(mPackageName);
        } else {
            if (DEBUG) {
                Log.d(
                        TAG,
                        "Registering action result listener for " + mListeningAssociations + ".");
            }
            mCompanionDeviceManager.setOnActionResultListener(
                    toIntArray(mListeningAssociations),
                    mPackageName,
                    mMainExecutor,
                    this::onActionResult);
        }
    }

    @GuardedBy("mLock")
    private long getNextInvalidationTimeLocked() {
        long nextInvalidationTime = Long.MAX_VALUE;
        for (AssociationState associationState : mAssociationStates.values()) {
            nextInvalidationTime =
                    Math.min(
                            nextInvalidationTime,
                            associationState.transportState.getFutureExpirationTimestamp());
            nextInvalidationTime =
                    Math.min(
                            nextInvalidationTime,
                            associationState.scanningState.getFutureExpirationTimestamp());
            nextInvalidationTime =
                    Math.min(
                            nextInvalidationTime,
                            associationState.advertisingState.getFutureExpirationTimestamp());
        }
        return nextInvalidationTime;
    }

    private static String actionToString(int action) {
        return switch (action) {
            case ActionRequest.REQUEST_TRANSPORT -> "REQUEST_TRANSPORT";
            case ActionRequest.REQUEST_NEARBY_SCANNING -> "REQUEST_NEARBY_SCANNING";
            case ActionRequest.REQUEST_NEARBY_ADVERTISING -> "REQUEST_NEARBY_ADVERTISING";
            default -> "UNKNOWN(" + action + ")";
        };
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("CompanionActionController:");
            pw.increaseIndent();
            pw.println("mInitialized=" + mInitialized);
            pw.println("mNextInvalidationTime=" + mNextInvalidationTime);
            pw.println("mListeningAssociations=" + mListeningAssociations);
            if (mAssociationStates.isEmpty()) {
                pw.println("No association states.");
            } else {
                pw.println("mAssociationStates:");
                pw.increaseIndent();
                for (AssociationState associationState : mAssociationStates.values()) {
                    associationState.dump(pw);
                }
                pw.decreaseIndent();
            }
            pw.decreaseIndent();
        }
    }

    /** Class hosting states of an association. */
    @SuppressWarnings("EffectivelyPrivate")
    private static class AssociationState implements Dumpable {
        public final int associationId;
        public final ActionState scanningState;
        public final ActionState advertisingState;
        public final ActionState transportState;

        AssociationState(int associationId, long now) {
            this.associationId = associationId;
            this.scanningState = new ActionState(now);
            this.advertisingState = new ActionState(now);
            this.transportState = new ActionState(now);
        }

        public boolean allDeactivated() {
            return scanningState.isDeactivated()
                    && advertisingState.isDeactivated()
                    && transportState.isDeactivated();
        }

        public void destroy(long now) {
            scanningState.destroy(now);
            advertisingState.destroy(now);
            transportState.destroy(now);
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("AssociationState#" + associationId + ":");
            pw.increaseIndent();
            pw.println("scanningState:");
            pw.increaseIndent();
            scanningState.dump(pw);
            pw.decreaseIndent();
            pw.println("advertisingState:");
            pw.increaseIndent();
            advertisingState.dump(pw);
            pw.decreaseIndent();
            pw.println("transportState:");
            pw.increaseIndent();
            transportState.dump(pw);
            pw.decreaseIndent();
            pw.decreaseIndent();
        }
    }

    /** Class hosting states of an action. */
    @SuppressWarnings("EffectivelyPrivate")
    private static class ActionState implements Dumpable {
        /** Action is not activated. */
        private static final int STATE_DEACTIVATED = 0;

        /** Queued for activation in next invalidation cycle. */
        private static final int STATE_QUEUED_FOR_ACTIVATION = 1;

        /** Activation request has been sent to CDM and a client is waiting for the result. */
        private static final int STATE_WAITING_FOR_ACTIVATED_OR_TIMEOUT = 2;

        /**
         * Activation request has been sent to CDM and we are waiting for the result without a
         * client. This mostly happen after timeout or client cancellation.
         */
        private static final int STATE_WAITING_FOR_ACTIVATED = 3;

        /** Received activation confirmation from CDM. */
        private static final int STATE_ACTIVATED = 4;

        /** Queued for deactivation in next invalidation cycle. */
        private static final int STATE_QUEUED_FOR_DEACTIVATION = 5;

        /** Deactivation request has been sent to CDM and a client is waiting for the result. */
        private static final int STATE_WAITING_FOR_DEACTIVATED_OR_TIMEOUT = 6;

        /**
         * Deactivation request has been sent to CDM and we are waiting for the result without a
         * client. This mostly happen after timeout or client cancellation.
         */
        private static final int STATE_WAITING_FOR_DEACTIVATED = 7;

        private int mState;
        private long mLastChangeTimestamp;
        @Nullable private SettableFuture<Boolean> mFuture;

        ActionState(long now) {
            mState = STATE_DEACTIVATED;
            mLastChangeTimestamp = now;
        }

        public ListenableFuture<Boolean> noteRequest(boolean activate, long now) {
            if (hasRequestedActivation() == activate) {
                // Duplicate request.
                if (isRequestQueued() || isWaitingWithTimeout()) {
                    // Previous request is still waiting.
                    return requireNonNull(mFuture);
                } else if (isActivated() || isDeactivated()) {
                    // Already done.
                    return Futures.immediateFuture(true);
                } else if (setState(
                        now,
                        activate
                                ? STATE_WAITING_FOR_ACTIVATED_OR_TIMEOUT
                                : STATE_WAITING_FOR_DEACTIVATED_OR_TIMEOUT,
                        "Continue waiting")) {
                    // Previous request is cancelled or timeout before received result. Create a
                    // new future to track request.
                    mFuture = SettableFuture.create();
                    return mFuture;
                }
            }
            // New request.
            if (setState(
                    now,
                    activate ? STATE_QUEUED_FOR_ACTIVATION : STATE_QUEUED_FOR_DEACTIVATION,
                    "Request queued")) {
                mFuture = SettableFuture.create();
                return mFuture;
            } else {
                return Futures.immediateFailedFuture(new Exception("Unable to queue request"));
            }
        }

        public boolean noteRequestSent(long now) {
            if (!isRequestQueued()) {
                return false;
            }
            return setState(
                    now,
                    hasRequestedActivation()
                            ? STATE_WAITING_FOR_ACTIVATED_OR_TIMEOUT
                            : STATE_WAITING_FOR_DEACTIVATED_OR_TIMEOUT,
                    "Request sent");
        }

        public boolean noteActionResult(long now, boolean activated) {
            return setState(
                    now,
                    activated ? STATE_ACTIVATED : STATE_DEACTIVATED,
                    "Received action result: activated=" + activated);
        }

        public boolean noteCancellation(long now, String reason) {
            return noteCancellation(mFuture, now, reason);
        }

        public boolean noteCancellation(ListenableFuture<?> future, long now, String reason) {
            if (mFuture != future) {
                // Mismatched future meaning the request we try to cancel no longer exist.
                return false;
            }
            if (isRequestQueued()) {
                // Request has not been sent yet. Cancel the request and revert state.
                return setState(
                        now,
                        hasRequestedActivation() ? STATE_DEACTIVATED : STATE_ACTIVATED,
                        reason);
            }
            // Request has been sent out and is no longer cancellable. Cancel the request but
            // keep waiting for result.
            if (isWaitingWithTimeout()) {
                return setState(
                        now,
                        hasRequestedActivation()
                                ? STATE_WAITING_FOR_ACTIVATED
                                : STATE_WAITING_FOR_DEACTIVATED,
                        reason);
            }
            return false;
        }

        private boolean setState(long now, int state, String reason) {
            if (mState == state) {
                return false;
            }
            if (DEBUG) {
                Log.d(
                        TAG,
                        "setState: state=" + stateToString(state) + ", reason=\"" + reason + "\"");
            }
            boolean prevRequestedActivation = hasRequestedActivation();
            boolean prevWaitingWithTimeout = isWaitingWithTimeout();
            mState = state;
            mLastChangeTimestamp = now;
            if (mFuture == null) {
                return true;
            }
            if (prevRequestedActivation != hasRequestedActivation()) {
                // Change of direction. Fail the previous request.
                mFuture.setException(new Exception(reason));
                mFuture = null;
            } else if (isActivated() || isDeactivated()) {
                // Request is completed. Report success.
                mFuture.set(true);
                mFuture = null;
            } else if (prevWaitingWithTimeout && !isWaitingWithTimeout()) {
                // Timeout reached or future cancelled.
                mFuture.setException(new Exception(reason));
                mFuture = null;
            }
            return true;
        }

        public boolean hasRequestedActivation() {
            return mState == STATE_QUEUED_FOR_ACTIVATION
                    || mState == STATE_WAITING_FOR_ACTIVATED_OR_TIMEOUT
                    || mState == STATE_WAITING_FOR_ACTIVATED
                    || mState == STATE_ACTIVATED;
        }

        public boolean isRequestQueued() {
            return mState == STATE_QUEUED_FOR_ACTIVATION || mState == STATE_QUEUED_FOR_DEACTIVATION;
        }

        public boolean isDeactivated() {
            return mState == STATE_DEACTIVATED;
        }

        public boolean isActivated() {
            return mState == STATE_ACTIVATED;
        }

        public boolean isWaitingWithTimeout() {
            return mState == STATE_WAITING_FOR_ACTIVATED_OR_TIMEOUT
                    || mState == STATE_WAITING_FOR_DEACTIVATED_OR_TIMEOUT;
        }

        public long getFutureExpirationTimestamp() {
            return isWaitingWithTimeout()
                    ? mLastChangeTimestamp + WAITING_TIMEOUT_MS
                    : Long.MAX_VALUE;
        }

        public void destroy(long now) {
            noteCancellation(now, "destroyed");
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("state=" + stateToString(mState));
            pw.println("lastChangeTimestamp=" + mLastChangeTimestamp);
            pw.println("hasFuture=" + (mFuture != null));
        }

        private static String stateToString(int state) {
            return switch (state) {
                case STATE_DEACTIVATED -> "DEACTIVATED";
                case STATE_QUEUED_FOR_ACTIVATION -> "QUEUED_FOR_ACTIVATION";
                case STATE_WAITING_FOR_ACTIVATED_OR_TIMEOUT -> "WAITING_FOR_ACTIVATED_OR_TIMEOUT";
                case STATE_WAITING_FOR_ACTIVATED -> "WAITING_FOR_ACTIVATED";
                case STATE_ACTIVATED -> "ACTIVATED";
                case STATE_QUEUED_FOR_DEACTIVATION -> "QUEUED_FOR_DEACTIVATION";
                case STATE_WAITING_FOR_DEACTIVATED_OR_TIMEOUT ->
                        "WAITING_FOR_DEACTIVATED_OR_TIMEOUT";
                case STATE_WAITING_FOR_DEACTIVATED -> "WAITING_FOR_DEACTIVATED";
                default -> "UNKNOWN(" + state + ")";
            };
        }
    }
}
