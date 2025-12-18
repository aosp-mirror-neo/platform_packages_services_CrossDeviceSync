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
import android.os.Build;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.IndentingPrintWriter;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.crossdevicesync.data.dagger.qualifier.GlobalStorage;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.feature.FeatureManager;
import com.android.crossdevicesync.metadata.MetadataPublisher;
import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;

/** The main service hosting cross device sync features. */
@SyncServiceSingleton
public class SyncService extends Service {
    private static final String TAG = "SyncService";

    /** Action for test binding. Only used by instrumentation tests. */
    @VisibleForTesting
    public static final String ACTION_BIND_FOR_TESTING =
            "com.android.crossdevicesync.action.BIND_FOR_TESTING";

    private final NetworkManager mNetworkManager;
    private final MetadataPublisher mMetadataPublisher;
    private final IStorage mGlobalStorage;
    private final SyncServiceShellCommand mSyncServiceShellCommand;
    private final Map<Class<?>, Provider<FeatureManager>> mFeatureManagerProviders;
    private final Set<FeatureManager> mFeatureManagers = new HashSet<>();

    @Nullable private CountDownLatch mDestroyLatch;

    @Inject
    public SyncService(
            NetworkManager networkManager,
            MetadataPublisher metadataPublisher,
            @GlobalStorage IStorage globalStorage,
            SyncServiceShellCommand syncServiceShellCommand,
            Map<Class<?>, Provider<FeatureManager>> featureManagerProviders) {
        mNetworkManager = networkManager;
        mMetadataPublisher = metadataPublisher;
        mGlobalStorage = globalStorage;
        mSyncServiceShellCommand = syncServiceShellCommand;
        mFeatureManagerProviders = featureManagerProviders;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");
        mNetworkManager.init();
        mMetadataPublisher.init();
        for (var entry : mFeatureManagerProviders.entrySet()) {
            try {
                Log.i(TAG, "Initializing " + entry.getKey().getSimpleName() + "...");
                FeatureManager featureManager = entry.getValue().get();
                featureManager.init();
                mFeatureManagers.add(featureManager);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to initialize " + entry.getKey().getSimpleName(), t);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
        mFeatureManagers.forEach(FeatureManager::destroy);
        mFeatureManagers.clear();
        mMetadataPublisher.destroy();
        mNetworkManager.destroy();
        mGlobalStorage.runInIoThread(mGlobalStorage::close);
        mGlobalStorage.shutdownIoThread();
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

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (Build.IS_DEBUGGABLE
                && mSyncServiceShellCommand.exec(
                                new Binder(), null, fd, null, args, null, new ResultReceiver(null))
                        == 0) {
            return;
        }
        IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println("SyncService state:");
        pw.increaseIndent();
        mNetworkManager.dump(pw);
        mMetadataPublisher.dump(pw);
        mFeatureManagers.forEach(f -> f.dump(pw));
        pw.decreaseIndent();
    }

    /** Stop the service and wait for onDestroy() to be called. */
    @VisibleForTesting
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
