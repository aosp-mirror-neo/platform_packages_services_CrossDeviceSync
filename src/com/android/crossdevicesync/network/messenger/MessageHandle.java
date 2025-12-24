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

import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.android.crossdevicesync.common.Clock;
import com.android.crossdevicesync.common.Dumpable;
import com.android.crossdevicesync.network.proto.CdmMessage.Message;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A handle containing a message and its control state. */
class MessageHandle implements ListenableFuture<Boolean>, Dumpable {
    private static final String TAG = "MessageHandle";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final int STATE_WAITING_FOR_TRANSPORT = 0;
    public static final int STATE_SENDING = 1;
    public static final int STATE_PENDING_RETRY = 2;
    public static final int STATE_DELIVERED = 3;
    public static final int STATE_FAILED = 4;
    public static final int STATE_CANCELLED = 5;

    private final Object mLock;
    private final Clock mClock;
    private final int mMaxAttempts;
    private final long mHandleId;
    private final String mName;
    @Nullable private final Message mMessage;

    @GuardedBy("mLock")
    private final List<Pair<Runnable, Executor>> mListeners = new ArrayList<>();

    @GuardedBy("mLock")
    private int mState = STATE_WAITING_FOR_TRANSPORT;

    @GuardedBy("mLock")
    private long mLastChangeTimestamp;

    @GuardedBy("mLock")
    private int mFailedAttempts;

    @GuardedBy("mLock")
    @Nullable
    private Throwable mError;

    MessageHandle(
            Object lock,
            Clock clock,
            long handleId,
            int maxAttempts,
            String name,
            @Nullable Message message) {
        if (message != null && message.getHandleId() != handleId) {
            throw new IllegalArgumentException(
                    "Mismatched handle id! handleId="
                            + handleId
                            + ", message.getHandleId()="
                            + message.getHandleId());
        }
        mLock = lock;
        mClock = clock;
        mHandleId = handleId;
        mMaxAttempts = maxAttempts;
        mLastChangeTimestamp = clock.elapsedRealtime();
        mName = name;
        mMessage = message;
    }

    public long getHandleId() {
        return mHandleId;
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        synchronized (mLock) {
            if (!isDone()) {
                mListeners.add(Pair.create(listener, executor));
                return;
            }
            executor.execute(listener);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (mLock) {
            if (isDone()) {
                return false;
            }
            setStateLocked(STATE_CANCELLED, mClock.elapsedRealtime());
            mLock.notifyAll();
            notifyListenersLocked();
        }
        return true;
    }

    public void noteSendingFailure(long now, @Nullable Throwable error) {
        synchronized (mLock) {
            if (mState != STATE_SENDING) {
                return;
            }
            mFailedAttempts++;
            if (mFailedAttempts >= mMaxAttempts) {
                mError = error;
                setStateLocked(STATE_FAILED, now);
                mLock.notifyAll();
                notifyListenersLocked();
            } else {
                setStateLocked(STATE_PENDING_RETRY, now);
            }
        }
    }

    public void noteTransportFailure(long now, @Nullable Throwable error) {
        synchronized (mLock) {
            if (isDone()) {
                return;
            }
            if (mState == STATE_PENDING_RETRY) {
                // Transport failure immediately after a sending failure shouldn't be considered
                // an extra failure attempt. So we don't increment failed attempts.
                setStateLocked(STATE_WAITING_FOR_TRANSPORT, now);
                return;
            }
            mFailedAttempts++;
            if (mFailedAttempts >= mMaxAttempts) {
                mError = error;
                setStateLocked(STATE_FAILED, now);
                mLock.notifyAll();
                notifyListenersLocked();
            } else {
                setStateLocked(STATE_WAITING_FOR_TRANSPORT, now);
            }
        }
    }

    public void noteSending(long now) {
        synchronized (mLock) {
            if (isDone()) {
                return;
            }
            setStateLocked(STATE_SENDING, now);
        }
    }

    public void noteSuccess(long now) {
        synchronized (mLock) {
            if (isDone()) {
                return;
            }
            setStateLocked(STATE_DELIVERED, now);
            mLock.notifyAll();
            notifyListenersLocked();
        }
    }

    @GuardedBy("mLock")
    private void setStateLocked(int state, long now) {
        if (mState == state) {
            return;
        }
        if (DEBUG) {
            Log.d(
                    TAG,
                    "Message handle \""
                            + mName
                            + "\" has changed its state: "
                            + stateToString(mState)
                            + " -> "
                            + stateToString(state));
        }
        mState = state;
        mLastChangeTimestamp = now;
    }

    @GuardedBy("mLock")
    private void notifyListenersLocked() {
        mListeners.forEach(pair -> pair.second.execute(pair.first));
    }

    public int getState() {
        synchronized (mLock) {
            return mState;
        }
    }

    public long getLastChangeTime() {
        synchronized (mLock) {
            return mLastChangeTimestamp;
        }
    }

    @Override
    public boolean isCancelled() {
        synchronized (mLock) {
            return mState == STATE_CANCELLED;
        }
    }

    @Override
    public boolean isDone() {
        synchronized (mLock) {
            return mState == STATE_DELIVERED || mState == STATE_FAILED || mState == STATE_CANCELLED;
        }
    }

    @Override
    public Boolean get() throws InterruptedException, ExecutionException {
        try {
            return get(0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // This is not possible.
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (mLock) {
            long start = mClock.elapsedRealtime();
            long timeoutMillis = unit.toMillis(timeout);
            while (!isDone()) {
                if (timeoutMillis <= 0) {
                    mLock.wait();
                } else {
                    mLock.wait(timeoutMillis);
                    if (mClock.elapsedRealtime() - start >= timeoutMillis) {
                        throw new TimeoutException();
                    }
                }
            }
            if (mState == STATE_FAILED) {
                if (mError != null) {
                    throw new ExecutionException(mError);
                }
                throw new ExecutionException(new Exception("Delivery failed"));
            }
            if (mState == STATE_CANCELLED) {
                throw new CancellationException();
            }
            return mState == STATE_DELIVERED;
        }
    }

    @Nullable
    public Message getMessage() {
        return mMessage;
    }

    private static String stateToString(int state) {
        return switch (state) {
            case STATE_WAITING_FOR_TRANSPORT -> "WAITING_FOR_TRANSPORT";
            case STATE_SENDING -> "SENDING";
            case STATE_PENDING_RETRY -> "PENDING_RETRY";
            case STATE_DELIVERED -> "DELIVERED";
            case STATE_FAILED -> "FAILED";
            case STATE_CANCELLED -> "CANCELLED";
            default -> "UNKNOWN(" + state + ")";
        };
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("MessageHandle:");
            pw.increaseIndent();
            pw.println("id=" + mHandleId);
            pw.println("name=" + mName);
            pw.println("state=" + stateToString(mState));
            pw.println("lastChangeTimestamp=" + mLastChangeTimestamp);
            pw.println("failedAttempts=" + mFailedAttempts);
            pw.println("error=" + mError);
            pw.println("messageSize=" + mMessage.getPayload().size());
            pw.decreaseIndent();
        }
    }
}
