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

package com.android.crossdevicesync.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.android.crossdevicesync.common.Clock;
import com.android.crossdevicesync.common.ClockImpl;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.common.CompanionDeviceManagerProxyImpl;
import com.android.crossdevicesync.common.ContextualModeManagerProxy;
import com.android.crossdevicesync.common.ContextualModeManagerProxyImpl;
import com.android.crossdevicesync.common.DelayedExecutor;
import com.android.crossdevicesync.common.DeviceUtils;
import com.android.crossdevicesync.common.DeviceUtilsImpl;
import com.android.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.crossdevicesync.common.FrameworkStatsLogProxyImpl;
import com.android.crossdevicesync.common.HandlerDelayedExecutorImpl;
import com.android.crossdevicesync.data.DefaultTimestampProvider;
import com.android.crossdevicesync.data.DeviceNodeIdProviderImpl;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.SharedDataStoreHandleFactory;
import com.android.crossdevicesync.data.StringConverter;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.data.storage.SqliteStorage;
import com.android.crossdevicesync.feature.FeatureManager;
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeController;
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeControllerImpl;
import com.android.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager;
import com.android.crossdevicesync.feature.mode.ContextualModeSyncController;
import com.android.crossdevicesync.feature.mode.ContextualModeSyncManager;
import com.android.crossdevicesync.metadata.MetadataPublisher;
import com.android.crossdevicesync.metadata.MetadataPublisherImpl;
import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.network.NetworkManagerImpl;
import com.android.crossdevicesync.network.advertiser.Advertiser;
import com.android.crossdevicesync.network.advertiser.AdvertiserImpl;
import com.android.crossdevicesync.network.companion.CompanionActionController;
import com.android.crossdevicesync.network.companion.CompanionActionControllerImpl;
import com.android.crossdevicesync.network.messenger.Messenger;
import com.android.crossdevicesync.network.messenger.MessengerImpl;
import com.android.crossdevicesync.network.scanner.Scanner;
import com.android.crossdevicesync.network.scanner.ScannerImpl;
import com.android.crossdevicesync.notification.NotificationConfig;
import com.android.crossdevicesync.notification.NotificationHelper;
import com.android.crossdevicesync.notification.NotificationHelperImpl;
import com.android.crossdevicesync.notification.NotificationManagerProxyImpl;
import com.android.crossdevicesync.user.UserHelper;
import com.android.crossdevicesync.user.UserHelperImpl;

import com.google.android.submerge.TimestampProvider;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Manual dependency injector for the CrossDeviceSync application. */
public class SyncServiceInjectorImpl implements SyncServiceInjector {
    private static final String DEFAULT_SHARED_PREFS_FILE_NAME = "cross_device_sync_prefs";
    private static final String GLOBAL_DATABASE_NAME = "cross_device_sync_global_db";

    private final IStorage mGlobalStorage;
    private final NetworkManager mNetworkManager;
    private final MetadataPublisher mMetadataPublisher;
    private final NotificationHelper mNotificationHelper;
    private final SyncServiceShellCommand mSyncServiceShellCommand;
    private final Map<String, Supplier<FeatureManager>> mFeatureManagers;

    public SyncServiceInjectorImpl(Context context) {
        Executor mainExecutor = context.getMainExecutor();
        UserHelper userHelper = new UserHelperImpl(context);
        ContextualModeManagerProxy contextualModeManagerProxy =
                new ContextualModeManagerProxyImpl(context);

        SharedPreferences sharedPreferences =
                context.createDeviceProtectedStorageContext()
                        .getSharedPreferences(DEFAULT_SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE);
        DeviceUtils deviceUtils = new DeviceUtilsImpl(context, mainExecutor);

        Clock clock = new ClockImpl();
        FrameworkStatsLogProxy frameworkStatsLogProxy = new FrameworkStatsLogProxyImpl();
        TimestampProvider timestampProvider = new DefaultTimestampProvider();
        mGlobalStorage = new SqliteStorage(context, GLOBAL_DATABASE_NAME);
        StringConverter stringConverter = new StringConverter();

        SharedDataStoreHandle.Factory sharedDataStoreHandleFactory =
                new SharedDataStoreHandleFactory(
                        new DeviceNodeIdProviderImpl(sharedPreferences),
                        mGlobalStorage,
                        frameworkStatsLogProxy,
                        timestampProvider);

        Object networkLock = new Object();
        CompanionDeviceManagerProxy companionDeviceManagerProxy =
                new CompanionDeviceManagerProxyImpl(context);

        DelayedExecutor mainDelayedExecutor =
                new HandlerDelayedExecutorImpl(new Handler(Looper.getMainLooper()));

        CompanionActionController companionActionController =
                new CompanionActionControllerImpl(
                        networkLock,
                        context,
                        companionDeviceManagerProxy,
                        mainDelayedExecutor,
                        clock);
        Messenger messenger =
                new MessengerImpl(
                        networkLock,
                        companionDeviceManagerProxy,
                        mainDelayedExecutor,
                        clock,
                        companionActionController);
        Advertiser advertiser =
                new AdvertiserImpl(networkLock, companionActionController, mainExecutor);
        Scanner scanner = new ScannerImpl(networkLock, companionActionController, mainExecutor);

        mNetworkManager =
                new NetworkManagerImpl(
                        networkLock,
                        mainExecutor,
                        companionDeviceManagerProxy,
                        companionActionController,
                        messenger,
                        advertiser,
                        scanner,
                        clock,
                        context,
                        frameworkStatsLogProxy);

        mMetadataPublisher =
                new MetadataPublisherImpl(
                        sharedPreferences,
                        mNetworkManager,
                        companionDeviceManagerProxy,
                        clock,
                        mainExecutor,
                        userHelper);

        mNotificationHelper =
                new NotificationHelperImpl(
                        sharedPreferences,
                        clock,
                        new NotificationManagerProxyImpl(context),
                        () -> new NotificationConfig(context, deviceUtils),
                        mainExecutor,
                        userHelper);

        mSyncServiceShellCommand = new SyncServiceShellCommand(mNotificationHelper);

        final AirplaneModeController apmController =
                new AirplaneModeControllerImpl(context, mainExecutor);

        final ContextualModeSyncController.Factory contextualModeSyncControllerFactory =
                user ->
                        new ContextualModeSyncController(
                                mNetworkManager,
                                sharedDataStoreHandleFactory,
                                mainExecutor,
                                stringConverter,
                                contextualModeManagerProxy,
                                mMetadataPublisher,
                                mNotificationHelper,
                                user);

        mFeatureManagers =
                Map.of(
                        AirplaneModeSyncManager.class.getSimpleName(),
                        () ->
                                new AirplaneModeSyncManager(
                                        context,
                                        mNetworkManager,
                                        apmController,
                                        mMetadataPublisher,
                                        mNotificationHelper,
                                        deviceUtils,
                                        mainExecutor,
                                        stringConverter,
                                        sharedDataStoreHandleFactory),
                        ContextualModeSyncManager.class.getSimpleName(),
                        () ->
                                new ContextualModeSyncManager(
                                        contextualModeSyncControllerFactory,
                                        userHelper,
                                        contextualModeManagerProxy,
                                        mainExecutor,
                                        mMetadataPublisher));
    }

    @Override
    public NetworkManager getNetworkManager() {
        return mNetworkManager;
    }

    @Override
    public MetadataPublisher getMetadataPublisher() {
        return mMetadataPublisher;
    }

    @Override
    public NotificationHelper getNotificationHelper() {
        return mNotificationHelper;
    }

    @Override
    public IStorage getGlobalStorage() {
        return mGlobalStorage;
    }

    @Override
    public SyncServiceShellCommand getSyncServiceShellCommand() {
        return mSyncServiceShellCommand;
    }

    @Override
    public Map<String, Supplier<FeatureManager>> getFeatureManagers() {
        return mFeatureManagers;
    }
}
