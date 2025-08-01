/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.crossdevicesync;

import static com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.annotation.Nullable;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import com.android.crossdevicesync.services.SyncService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public final class BootReceiverTest extends ApplicationComponentTestBase {
    private static final long STOP_SERVICE_TIMEOUT_SEC = 5;

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();
    @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private ServiceConnection mConnection;

    @Nullable private SyncService mService;

    @Before
    public void setUp() {
        super.setUp();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mService != null) {
            mService.stopAndWait(STOP_SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS);
            mService = null;
        }
    }

    @Test
    @EnableFlags({FLAG_START_SYNC_SERVICE_ON_BOOT})
    public void testLockedBootCompleted_syncServiceStarted() throws TimeoutException {
        Intent intent = new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED);

        mBootReceiver.onReceive(mContext, intent);

        IBinder binder =
                mServiceRule.bindService(getSyncServiceIntent(), mConnection, /* flags= */ 0);
        mService = ((SyncService.TestBinder) binder).getService();
        assertThat(mService).isNotNull();
    }

    @Test
    @DisableFlags({FLAG_START_SYNC_SERVICE_ON_BOOT})
    public void testLockedBootCompleted_flagDisabled_syncServiceNotStarted() {
        Intent intent = new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED);

        mBootReceiver.onReceive(mContext, intent);

        assertThrows(
                TimeoutException.class,
                () ->
                        mServiceRule.bindService(
                                getSyncServiceIntent(), mConnection, /* flags= */ 0));
    }

    private Intent getSyncServiceIntent() {
        return new Intent(mContext, SyncService.class)
                .setAction(SyncService.ACTION_BIND_FOR_TESTING);
    }
}
