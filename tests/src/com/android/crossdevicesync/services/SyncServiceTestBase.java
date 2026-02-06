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

import com.android.crossdevicesync.ApplicationTestBase;
import com.android.crossdevicesync.data.StringConverter;
import com.android.crossdevicesync.data.fake.FakeSharedDataStoreHandleFactory;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.feature.FeatureManager;
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager;
import com.android.crossdevicesync.feature.mode.ContextualModeSyncController;
import com.android.crossdevicesync.feature.mode.ContextualModeSyncManager;
import com.android.crossdevicesync.metadata.MetadataPublisher;
import com.android.crossdevicesync.metadata.MetadataPublisherImpl;
import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.network.NetworkManagerImpl;
import com.android.crossdevicesync.network.advertiser.AdvertiserImpl;
import com.android.crossdevicesync.network.companion.CompanionActionControllerImpl;
import com.android.crossdevicesync.network.fake.FakeNetworkManager;
import com.android.crossdevicesync.network.messenger.MessengerImpl;
import com.android.crossdevicesync.network.scanner.ScannerImpl;
import com.android.crossdevicesync.notification.NotificationConfig;
import com.android.crossdevicesync.notification.NotificationHelper;
import com.android.crossdevicesync.notification.NotificationHelperImpl;

import java.util.Map;
import java.util.function.Supplier;

/** Base class for tests. */
public abstract class SyncServiceTestBase extends ApplicationTestBase {
    protected final AirplaneModeSyncManager mAirplaneModeSyncManager;
    protected final StringConverter mStringConverter;
    protected final NetworkManagerImpl mNetworkManagerImpl;
    protected final MessengerImpl mMessengerImpl;
    protected final CompanionActionControllerImpl mCompanionActionController;
    protected final AdvertiserImpl mAdvertiser;
    protected final ScannerImpl mScanner;
    protected final MetadataPublisherImpl mMetadataPublisher;
    protected final ContextualModeSyncController.Factory mContextualModeSyncControllerFactory;
    protected final NotificationHelperImpl mNotificationHelperImpl;
    protected final SyncServiceShellCommand mSyncServiceShellCommand;
    protected final FakeSharedDataStoreHandleFactory mSharedDataStoreHandleFactory;

    protected SyncServiceTestBase() {
        mStringConverter = new StringConverter();
        Object networkLock = new Object();

        mNetworkManagerImpl =
                new NetworkManagerImpl(
                        networkLock,
                        mMainExecutor,
                        mFakeCompanionDeviceManagerProxy,
                        mFakeCompanionActionController,
                        mFakeMessenger,
                        mFakeAdvertiser,
                        mFakeScanner,
                        mFakeClock,
                        mContext,
                        mFakeFrameworkStatsLogProxy);

        mSharedDataStoreHandleFactory =
                new FakeSharedDataStoreHandleFactory(
                        mFakeDeviceNodeIdProvider,
                        mFakeStorage,
                        mFakeFrameworkStatsLogProxy,
                        mTimestampProvider,
                        this::useRealSharedDataStoreImpl,
                        this::useRealSharedDataStoreHandleImpl);

        mContextualModeSyncControllerFactory =
                user ->
                        new ContextualModeSyncController(
                                mFakeNetworkManager,
                                mSharedDataStoreHandleFactory,
                                mMainExecutor,
                                mStringConverter,
                                mFakeContextualModeManager,
                                mFakeMetadataPublisher,
                                mFakeNotificationHelper,
                                user);

        mSyncServiceShellCommand = new SyncServiceShellCommand(mFakeNotificationHelper);

        mAirplaneModeSyncManager =
                new AirplaneModeSyncManager(
                        mApplication,
                        mFakeNetworkManager,
                        mFakeAirplaneModeController,
                        mFakeMetadataPublisher,
                        mFakeNotificationHelper,
                        mFakeDeviceUtil,
                        mMainExecutor,
                        mStringConverter,
                        mSharedDataStoreHandleFactory);

        mFakeNotificationHelper.init();

        mCompanionActionController =
                new CompanionActionControllerImpl(
                        networkLock,
                        mContext,
                        mFakeCompanionDeviceManagerProxy,
                        mDelayedExecutor,
                        mFakeClock);
        mMessengerImpl =
                new MessengerImpl(
                        networkLock,
                        mFakeCompanionDeviceManagerProxy,
                        mDelayedExecutor,
                        mFakeClock,
                        mFakeCompanionActionController);
        mAdvertiser =
                new AdvertiserImpl(networkLock, mFakeCompanionActionController, mMainExecutor);
        mScanner = new ScannerImpl(networkLock, mFakeCompanionActionController, mMainExecutor);

        mMetadataPublisher =
                new MetadataPublisherImpl(
                        mSharedPreferences,
                        mFakeNetworkManager,
                        mFakeCompanionDeviceManagerProxy,
                        mFakeClock,
                        mMainExecutor,
                        mFakeUserHelper);

        mNotificationHelperImpl =
                new NotificationHelperImpl(
                        mSharedPreferences,
                        mFakeClock,
                        mFakeNotificationManagerProxy,
                        () -> new NotificationConfig(mContext, mFakeDeviceUtil),
                        mMainExecutor,
                        mFakeUserHelper);
    }

    protected boolean useRealSharedDataStoreImpl() {
        return false;
    }

    protected boolean useRealSharedDataStoreHandleImpl() {
        return useRealSharedDataStoreImpl();
    }

    public FakeNetworkManager getNetworkManager() {
        return mFakeNetworkManager;
    }

    public class TestSyncServiceInjector implements SyncServiceInjector {
        @Override
        public NetworkManager getNetworkManager() {
            return mFakeNetworkManager;
        }

        @Override
        public MetadataPublisher getMetadataPublisher() {
            return mFakeMetadataPublisher;
        }

        @Override
        public NotificationHelper getNotificationHelper() {
            return mFakeNotificationHelper;
        }

        @Override
        public IStorage getGlobalStorage() {
            return mFakeStorage;
        }

        @Override
        public SyncServiceShellCommand getSyncServiceShellCommand() {
            return mSyncServiceShellCommand;
        }

        @Override
        public Map<String, Supplier<FeatureManager>> getFeatureManagers() {
            return Map.of(
                    AirplaneModeSyncManager.class.getSimpleName(),
                    () -> mAirplaneModeSyncManager,
                    ContextualModeSyncManager.class.getSimpleName(),
                    () ->
                            new ContextualModeSyncManager(
                                    mContextualModeSyncControllerFactory,
                                    mFakeUserHelper,
                                    mFakeContextualModeManager,
                                    mMainExecutor,
                                    mFakeMetadataPublisher));
        }
    }
}
