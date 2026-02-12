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
package com.android.crossdevicesync.common;

import android.annotation.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Utils class hosting common static methods. */
public class Utils {

    /** Convert an integer collection to an integer array. */
    public static int[] toIntArray(Collection<Integer> collection) {
        int[] res = new int[collection.size()];
        Iterator<Integer> iterator = collection.iterator();
        for (int i = 0; i < res.length; i++) {
            res[i] = iterator.next();
        }
        return res;
    }

    /** Get exception from a future if it failed. */
    @Nullable
    public static Throwable getException(Future<?> future) {
        try {
            future.get();
            return null;
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return cause == null ? e : cause;
        }
    }

    /** Check if a future has succeeded. */
    public static boolean isFutureSucceeded(Future<?> future) {
        if (!future.isDone()) {
            return false;
        }
        return getException(future) == null;
    }

    /** Check if a future has failed. */
    public static boolean isFutureFailed(Future<?> future) {
        if (!future.isDone()) {
            return false;
        }
        return getException(future) != null;
    }
}
