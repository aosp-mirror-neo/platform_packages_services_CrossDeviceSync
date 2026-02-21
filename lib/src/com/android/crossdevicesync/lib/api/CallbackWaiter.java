/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.crossdevicesync.lib.api;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Implementation of {@link Consumer} that supports waiting for a value.
 *
 * @param <T> the type of callback value
 */
public class CallbackWaiter<T> implements Consumer<T> {
    private final LinkedBlockingQueue<T> mQueue = new LinkedBlockingQueue<>();

    @Override
    public void accept(T t) {
        mQueue.add(t);
    }

    /** Get the next received callback value. Blocking until timeout. */
    @Nullable
    public T getNext(long timeout, TimeUnit unit) {
        try {
            return mQueue.poll(timeout, unit);
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * Wait for a callback value predicate to be true. Blocking until timeout.
     *
     * @return true if the predicate is satisfied by a callback value.
     */
    public boolean waitFor(Predicate<T> filter, long timeout, TimeUnit unit) {
        long timeoutMs = unit.toMillis(timeout);
        long start = SystemClock.elapsedRealtime();
        while (true) {
            T next = getNext(timeoutMs, TimeUnit.MILLISECONDS);
            if (next == null) {
                // Timeout.
                return false;
            }
            if (filter.test(next)) {
                return true;
            }
            timeoutMs -= (SystemClock.elapsedRealtime() - start);
            if (timeoutMs <= 0) {
                // Timeout.
                return false;
            }
        }
    }

    /**
     * Wait for a callback value to be received. Blocking until timeout.
     *
     * @return true if the expected callback value is received.
     */
    public boolean waitFor(T expected, long timeout, TimeUnit unit) {
        return waitFor(t -> Objects.equals(t, expected), timeout, unit);
    }
}
