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

import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.android.submerge.TimestampProvider;

import javax.inject.Inject;

/** Fake implementation of {@link TimestampProvider}. */
@SyncServiceSingleton
public class FakeTimestampProvider implements TimestampProvider {
    private long mTimestamp;
    private boolean mAutoAdvance;

    @Inject
    FakeTimestampProvider() {
        setAutoAdvance(true);
    }

    @Override
    public long now() {
        long now = mTimestamp;
        if (mAutoAdvance) {
            increment();
        }
        return now;
    }

    public long peekNow() {
        return mTimestamp;
    }

    public void setTimeStamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public void increment() {
        advanceBy(1);
    }

    public void advanceBy(long time) {
        mTimestamp += time;
    }

    public void setAutoAdvance(boolean enabled) {
        mAutoAdvance = enabled;
    }
}
