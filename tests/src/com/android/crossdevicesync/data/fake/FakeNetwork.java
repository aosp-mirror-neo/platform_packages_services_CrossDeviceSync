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
package com.android.crossdevicesync.data.fake;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.crossdevicesync.data.dagger.SharedDataStoreSingleton;

import com.google.android.submerge.NetworkInterface;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import javax.inject.Inject;

/** Fake implementation of {@link NetworkInterface}. */
@SharedDataStoreSingleton
public class FakeNetwork implements NetworkInterface {

    private final Map<String, Queue<byte[]>> mAllUpdates = new HashMap<>();
    @Nullable private Consumer<Pair<String, byte[]>> mUpdateListener;

    @Inject
    FakeNetwork() {}

    @Override
    public void onNewUpdate(String docId, byte[] updateMessage) {
        mAllUpdates.computeIfAbsent(docId, k -> new LinkedList<>());
        mAllUpdates.get(docId).add(updateMessage);
        if (mUpdateListener != null) {
            mUpdateListener.accept(new Pair<>(docId, updateMessage));
        }
    }

    /** Sets a listener to be notified of any new updates. */
    public void setUpdateListener(@Nullable Consumer<Pair<String, byte[]>> listener) {
        mUpdateListener = listener;
    }

    /** Returns a map of all updates received, keyed by document ID. */
    public Map<String, Queue<byte[]>> getAllUpdates() {
        return mAllUpdates;
    }

    /** Returns a queue of all updates received for a given document ID. */
    public Queue<byte[]> getAllUpdates(String docId) {
        return getAllUpdates().get(docId);
    }

    /** Returns the latest update for a given document ID. */
    public byte[] getLatestUpdate(String docId) {
        return getAllUpdates(docId).peek();
    }
}
