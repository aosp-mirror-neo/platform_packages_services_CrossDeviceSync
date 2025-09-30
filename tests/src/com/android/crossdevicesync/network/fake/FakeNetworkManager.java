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
package com.android.crossdevicesync.network.fake;

import android.util.IndentingPrintWriter;

import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/** Fake implementation of {@link NetworkManager}. */
@SyncServiceSingleton
public class FakeNetworkManager implements NetworkManager {

    @Inject
    FakeNetworkManager() {}

    @Override
    public void init() {}

    @Override
    public void destroy() {}

    @Override
    public Map<Integer, RemoteDevice> getRemoteDevices() {
        return Collections.emptyMap();
    }

    @Override
    public void dump(IndentingPrintWriter pw) {}
}
