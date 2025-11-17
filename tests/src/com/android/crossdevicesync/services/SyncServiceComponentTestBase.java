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

import com.android.crossdevicesync.ApplicationComponentTestBase;
import com.android.crossdevicesync.data.StringConverter;
import com.android.crossdevicesync.data.fake.FakeDeviceNodeIdProvider;
import com.android.crossdevicesync.data.fake.FakeTimestampProvider;
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager;
import com.android.crossdevicesync.feature.airplanemode.fake.FakeAirplaneModeController;
import com.android.crossdevicesync.metadata.MetadataPublisherImpl;
import com.android.crossdevicesync.network.NetworkManagerImpl;
import com.android.crossdevicesync.network.advertiser.AdvertiserImpl;
import com.android.crossdevicesync.network.advertiser.fake.FakeAdvertiser;
import com.android.crossdevicesync.network.companion.CompanionActionControllerImpl;
import com.android.crossdevicesync.network.companion.fake.FakeCompanionActionController;
import com.android.crossdevicesync.network.fake.FakeNetworkManager;
import com.android.crossdevicesync.network.messenger.MessengerImpl;
import com.android.crossdevicesync.network.messenger.fake.FakeMessenger;
import com.android.crossdevicesync.network.scanner.ScannerImpl;
import com.android.crossdevicesync.network.scanner.fake.FakeScanner;
import com.android.crossdevicesync.services.dagger.TestSyncServiceComponent;

import javax.inject.Inject;

/** Base class for tests that depend on the {@link TestSyncServiceComponent} */
public abstract class SyncServiceComponentTestBase extends ApplicationComponentTestBase {
    protected TestSyncServiceComponent mSyncServiceComponent;
    @Inject protected SyncService mSyncService;
    @Inject protected StringConverter mStringConverter;
    @Inject protected FakeDeviceNodeIdProvider mDeviceNodeIdProvider;
    @Inject protected FakeTimestampProvider mTimestampProvider;
    @Inject protected NetworkManagerImpl mNetworkManagerImpl;
    @Inject protected FakeMessenger mFakeMessenger;
    @Inject protected FakeScanner mFakeScanner;
    @Inject protected FakeAdvertiser mFakeAdvertiser;
    @Inject protected MessengerImpl mMessengerImpl;
    @Inject protected FakeCompanionActionController mFakeCompanionActionController;
    @Inject protected FakeNetworkManager mFakeNetworkManager;
    @Inject protected CompanionActionControllerImpl mCompanionActionController;
    @Inject protected AdvertiserImpl mAdvertiser;
    @Inject protected ScannerImpl mScanner;
    @Inject protected AirplaneModeSyncManager mAirplaneModeSyncManager;
    @Inject protected FakeAirplaneModeController mFakeAirplaneModeController;
    @Inject protected MetadataPublisherImpl mMetadataPublisher;

    @Override
    public void setUp() {
        super.setUp();
        mSyncServiceComponent = newSyncServiceComponent();
        mSyncServiceComponent.inject(this);
    }

    protected TestSyncServiceComponent newSyncServiceComponent() {
        return mApplicationComponent.getTestSyncServiceComponentBuilder().build();
    }

    public FakeNetworkManager getNetworkManager() {
        return mFakeNetworkManager;
    }
}
