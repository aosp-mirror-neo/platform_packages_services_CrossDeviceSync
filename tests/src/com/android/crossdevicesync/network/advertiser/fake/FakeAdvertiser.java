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
package com.android.crossdevicesync.network.advertiser.fake;

import com.android.crossdevicesync.network.advertiser.Advertiser;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

/** A fake implementation of {@link Advertiser} for use in tests. */
@SyncServiceSingleton
public class FakeAdvertiser implements Advertiser {
    private final Set<FakeAdvertisingSession> mSessions = new HashSet<>();
    private boolean mIsDestroyed = false;

    @Inject
    public FakeAdvertiser() {}

    @Override
    public AdvertisingSession startAdvertising(int associationId, String requestName) {
        if (mIsDestroyed) {
            throw new IllegalStateException("Advertiser is destroyed.");
        }
        FakeAdvertisingSession session = new FakeAdvertisingSession(associationId, requestName);
        mSessions.add(session);
        return session;
    }

    @Override
    public void closeAllAdvertisingSessions() {
        if (mIsDestroyed) {
            return;
        }
        // Create a copy to avoid ConcurrentModificationException.
        new HashSet<>(mSessions).forEach(FakeAdvertisingSession::close);
    }

    /** Destroys the advertiser, closing all sessions and preventing new ones. */
    public void destroy() {
        closeAllAdvertisingSessions();
        mIsDestroyed = true;
    }

    private void removeSession(FakeAdvertisingSession session) {
        mSessions.remove(session);
    }

    /** Returns the set of active advertising sessions. */
    public Set<FakeAdvertisingSession> getSessions() {
        return Collections.unmodifiableSet(mSessions);
    }

    /** Returns any single advertising session, or {@code null} if no sessions are active. */
    public FakeAdvertisingSession getAnySession() {
        return mSessions.isEmpty() ? null : mSessions.iterator().next();
    }

    /** A fake implementation of {@link AdvertisingSession}. */
    public class FakeAdvertisingSession implements AdvertisingSession {
        private final int mAssociationId;
        private final String mRequestName;
        private boolean mActive = true;

        FakeAdvertisingSession(int associationId, String requestName) {
            mAssociationId = associationId;
            mRequestName = requestName;
        }

        @Override
        public int getAssociationId() {
            return mAssociationId;
        }

        @Override
        public boolean isActive() {
            return mActive;
        }

        @Override
        public void close() {
            if (mActive) {
                mActive = false;
                removeSession(this);
            }
        }

        /** Returns the request name for this session. */
        public String getRequestName() {
            return mRequestName;
        }
    }
}
