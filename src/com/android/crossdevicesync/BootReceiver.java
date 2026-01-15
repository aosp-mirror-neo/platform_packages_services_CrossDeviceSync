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
package com.android.crossdevicesync;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;

import com.android.crossdevicesync.flags.Flags;
import com.android.crossdevicesync.services.SyncService;

import javax.inject.Inject;

/** Broadcast receiver that reacts to boot signals. */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Inject
    BootReceiver() {}

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: " + intent);
        if (Flags.startSyncServiceOnBoot()
                && Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            if (context.getUser().equals(UserHandle.SYSTEM)) {
                Log.d(TAG, "Starting SyncService.");
                context.startService(new Intent(context, SyncService.class));
            } else {
                Log.d(
                        TAG,
                        "Disabling boot receiver for non-system user "
                                + context.getUser().getIdentifier());
                context.getPackageManager()
                        .setComponentEnabledSetting(
                                new ComponentName(context, BootReceiver.class),
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                0);
            }
        }
    }
}
