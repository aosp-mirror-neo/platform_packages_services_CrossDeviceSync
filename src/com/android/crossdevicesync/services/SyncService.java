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
package com.android.crossdevicesync.services;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/** The main service hosting cross device sync features. */
@SyncServiceSingleton
public class SyncService extends Service {
    private static final String TAG = "SyncService";

    /** Action for test binding. Only used by instrumentation tests. */
    @VisibleForTesting
    public static final String ACTION_BIND_FOR_TESTING =
            "com.android.crossdevicesync.action.BIND_FOR_TESTING";

    @Nullable private CountDownLatch mDestroyLatch;

    @Inject
    public SyncService() {}

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
        if (mDestroyLatch != null) {
            mDestroyLatch.countDown();
            mDestroyLatch = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (ACTION_BIND_FOR_TESTING.equals(intent.getAction())) {
            return new TestBinder();
        }
        // Binding is not supported unless in tests.
        return null;
    }

    @VisibleForTesting
    /** Stop the service and wait for onDestroy() to be called. */
    public void stopAndWait(long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        mDestroyLatch = latch;
        stopSelf();
        latch.await(timeout, unit);
    }

    /** Binder for instrumentation tests. */
    @VisibleForTesting
    public class TestBinder extends Binder {
        public SyncService getService() {
            return SyncService.this;
        }
    }
}
