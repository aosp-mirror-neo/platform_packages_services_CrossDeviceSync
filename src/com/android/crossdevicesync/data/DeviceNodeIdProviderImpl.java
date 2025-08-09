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
package com.android.crossdevicesync.data;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import java.util.UUID;

import javax.inject.Inject;

/**
 * Default implementation of {@link DeviceNodeIdProvider} that generates a random UUID and reuse the
 * same id afterwards.
 */
@SyncServiceSingleton
public class DeviceNodeIdProviderImpl implements DeviceNodeIdProvider {
    private static final String KEY_LOCAL_DEVICE_NODE_ID = "local_device_node_id";
    private final SharedPreferences mSharedPreferences;

    @Inject
    DeviceNodeIdProviderImpl(SharedPreferences sharedPreferences) {
        mSharedPreferences = sharedPreferences;
    }

    @Override
    public String getLocalDeviceNodeId() {
        String id =  mSharedPreferences.getString(KEY_LOCAL_DEVICE_NODE_ID, "");
        if (TextUtils.isEmpty(id)) {
            id = UUID.randomUUID().toString();
            mSharedPreferences.edit().putString(KEY_LOCAL_DEVICE_NODE_ID, id).apply();
        }
        return id;
    }
}
