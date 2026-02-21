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
package com.android.crossdevicesync.lib;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.lib.api.CallbackWaiter;
import com.android.crossdevicesync.lib.api.CdmPipe;
import com.android.crossdevicesync.lib.api.ContextualModeSyncDocument;
import com.android.crossdevicesync.lib.api.CrossDeviceSyncTestLib;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CrossDeviceSyncTestLibTest {
    private static final String TAG = "CrossDeviceSyncTestLibTest";
    private static final long SYNC_TIMEOUT_SEC = 7;

    private CrossDeviceSyncTestLib mTestLib;
    private CrossDeviceSyncTestLib mRemoteTestLib;
    private BridgeThread mBridgeThread1;
    private BridgeThread mBridgeThread2;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mTestLib = CrossDeviceSyncTestLib.newInstance(context);
        mRemoteTestLib = CrossDeviceSyncTestLib.newInstance(context);
        mBridgeThread1 =
                new BridgeThread(
                        mTestLib.getCdmPipe().getInputStreamForCdmTransport(),
                        mRemoteTestLib.getCdmPipe().getOutputStreamForCdmTransport());
        mBridgeThread2 =
                new BridgeThread(
                        mRemoteTestLib.getCdmPipe().getInputStreamForCdmTransport(),
                        mTestLib.getCdmPipe().getOutputStreamForCdmTransport());
        mBridgeThread1.start();
        mBridgeThread2.start();
    }

    @After
    public void tearDown() throws InterruptedException {
        mBridgeThread1.shutDownAndWait(1, TimeUnit.SECONDS);
        mBridgeThread2.shutDownAndWait(1, TimeUnit.SECONDS);
        mTestLib.close();
        mRemoteTestLib.close();
    }

    @Test
    public void testGetCdmPipe() {
        CdmPipe pipe = mTestLib.getCdmPipe();
        assertThat(pipe).isNotNull();
    }

    @Test
    public void testGetModeSyncDocument() {
        ContextualModeSyncDocument doc = mTestLib.getModeSyncDocument();
        assertThat(doc).isNotNull();
    }

    @Test
    public void testModeSyncDocument() {
        ContextualModeSyncDocument localDoc = mTestLib.getModeSyncDocument();
        ContextualModeSyncDocument remoteDoc = mRemoteTestLib.getModeSyncDocument();
        CallbackWaiter<Boolean> localDocWaiter = new CallbackWaiter<>();
        CallbackWaiter<Boolean> remoteDocWaiter = new CallbackWaiter<>();
        localDoc.addOnRemoteDndChangedListener(Runnable::run, localDocWaiter);
        remoteDoc.addOnRemoteDndChangedListener(Runnable::run, remoteDocWaiter);

        // Turn on DND on local doc
        localDoc.setDndEnabled(true);

        // Assert the change is synced to remote doc
        assertThat(remoteDocWaiter.waitFor(true, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
        assertThat(remoteDoc.isDndEnabled()).isTrue();

        // Turn off DND on remote doc.
        remoteDoc.setDndEnabled(false);

        // Assert the change is synced to local doc
        assertThat(localDocWaiter.waitFor(false, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
        assertThat(localDoc.isDndEnabled()).isFalse();

        // Turn on DND on remote doc.
        remoteDoc.setDndEnabled(true);

        // Assert the change is synced to local doc.
        assertThat(localDocWaiter.waitFor(true, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
        assertThat(localDoc.isDndEnabled()).isTrue();

        // Turn off DND on local doc.
        localDoc.setDndEnabled(false);

        // Assert the change is synced to remote doc.
        assertThat(remoteDocWaiter.waitFor(false, SYNC_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
        assertThat(remoteDoc.isDndEnabled()).isFalse();
    }

    private static class BridgeThread extends Thread {
        private final InputStream mIs;
        private final OutputStream mOs;
        private final CountDownLatch mStoppedLatch = new CountDownLatch(1);

        BridgeThread(InputStream is, OutputStream os) {
            mIs = is;
            mOs = os;
        }

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    // The read() has 100ms internal timeout and will return -1 if no message is
                    // available.
                    int val = mIs.read();
                    if (val == -1) {
                        continue;
                    }
                    mOs.write(val);
                    mOs.flush();
                }
            } catch (Exception e) {
            }
            mStoppedLatch.countDown();
            Log.i(TAG, "Bridge thread exited");
        }

        boolean shutDownAndWait(long timeout, TimeUnit unit) throws InterruptedException {
            interrupt();
            return mStoppedLatch.await(timeout, unit);
        }
    }
}
