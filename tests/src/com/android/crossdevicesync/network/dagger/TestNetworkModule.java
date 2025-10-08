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
package com.android.crossdevicesync.network.dagger;

import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.network.NetworkManagerImpl;
import com.android.crossdevicesync.network.advertiser.Advertiser;
import com.android.crossdevicesync.network.advertiser.fake.FakeAdvertiser;
import com.android.crossdevicesync.network.companion.CompanionActionController;
import com.android.crossdevicesync.network.companion.fake.FakeCompanionActionController;
import com.android.crossdevicesync.network.messenger.Messenger;
import com.android.crossdevicesync.network.messenger.fake.FakeMessenger;
import com.android.crossdevicesync.network.scanner.Scanner;
import com.android.crossdevicesync.network.scanner.fake.FakeScanner;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import dagger.Binds;
import dagger.Module;

@Module(includes = {NetworkBaseModule.class})
public abstract class TestNetworkModule {
    @Binds
    @SyncServiceSingleton
    abstract NetworkManager bindNetworkManager(NetworkManagerImpl impl);

    @Binds
    @SyncServiceSingleton
    abstract Messenger bindMessenger(FakeMessenger impl);

    @Binds
    @SyncServiceSingleton
    abstract Advertiser bindAdvertiser(FakeAdvertiser impl);

    @Binds
    @SyncServiceSingleton
    abstract Scanner bindScanner(FakeScanner impl);

    @Binds
    @SyncServiceSingleton
    abstract CompanionActionController bindCompanionActionController(
            FakeCompanionActionController impl);
}
