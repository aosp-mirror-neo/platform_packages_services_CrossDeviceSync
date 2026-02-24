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
package com.android.crossdevicesync.lib.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.android.crossdevicesync.common.Clock;
import com.android.crossdevicesync.common.ClockImpl;
import com.android.crossdevicesync.common.DelayedExecutor;
import com.android.crossdevicesync.common.HandlerDelayedExecutorImpl;
import com.android.crossdevicesync.data.DefaultTimestampProvider;
import com.android.crossdevicesync.data.DeviceNodeIdProvider;
import com.android.crossdevicesync.data.DeviceNodeIdProviderImpl;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.SharedDataStoreHandleFactory;
import com.android.crossdevicesync.data.StringConverter;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.lib.AirplaneModeSyncDocumentImpl;
import com.android.crossdevicesync.lib.CdmPipeImpl;
import com.android.crossdevicesync.lib.ContextualModeSyncDocumentImpl;
import com.android.crossdevicesync.lib.common.FakeCompanionDeviceManagerProxy;
import com.android.crossdevicesync.lib.common.FakeFrameworkStatsLogProxy;
import com.android.crossdevicesync.lib.common.FakeSharedPreferences;
import com.android.crossdevicesync.lib.common.FakeUserHelper;
import com.android.crossdevicesync.lib.common.InMemoryStorage;
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

import com.google.android.submerge.TimestampProvider;

import java.util.concurrent.Executor;

/**
 * Entry point for the CrossDeviceSync test library.
 *
 * <p>This class provides access to simulated system components and sync documents for testing.
 */
public class CrossDeviceSyncTestLib implements AutoCloseable {
    private final CdmPipeImpl mCdmPipe;
    private final ContextualModeSyncDocumentImpl mContextualModeSyncDocument;
    private final AirplaneModeSyncDocumentImpl mAirplaneModeSyncDocument;
    private final IStorage mStorage;
    private final NetworkManager mNetworkManager;
    private final MetadataPublisher mMetadataPublisher;

    private CrossDeviceSyncTestLib(Context context) {
        mCdmPipe = new CdmPipeImpl();
        FakeCompanionDeviceManagerProxy companionDeviceManagerProxy =
                new FakeCompanionDeviceManagerProxy(context, mCdmPipe);
        FakeFrameworkStatsLogProxy frameworkStatsLogProxy = new FakeFrameworkStatsLogProxy();

        Executor mainExecutor = context.getMainExecutor();
        Clock clock = new ClockImpl();

        SharedPreferences sharedPrefs = new FakeSharedPreferences();
        DeviceNodeIdProvider nodeIdProvider = new DeviceNodeIdProviderImpl(sharedPrefs);

        mStorage = new InMemoryStorage();
        TimestampProvider timestampProvider = new DefaultTimestampProvider();
        SharedDataStoreHandle.Factory sharedDataStoreHandleFactory =
                new SharedDataStoreHandleFactory(
                        nodeIdProvider, mStorage, frameworkStatsLogProxy, timestampProvider);

        Object networkLock = new Object();
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
        mNetworkManager.init();

        mMetadataPublisher =
                new MetadataPublisherImpl(
                        sharedPrefs,
                        mNetworkManager,
                        companionDeviceManagerProxy,
                        clock,
                        mainExecutor,
                        new FakeUserHelper(context));
        mMetadataPublisher.init();

        mContextualModeSyncDocument =
                new ContextualModeSyncDocumentImpl(
                        context,
                        sharedDataStoreHandleFactory,
                        mNetworkManager,
                        new StringConverter(),
                        mMetadataPublisher);

        mAirplaneModeSyncDocument =
                new AirplaneModeSyncDocumentImpl(
                        context,
                        sharedDataStoreHandleFactory,
                        mNetworkManager,
                        new StringConverter(),
                        mMetadataPublisher);
    }

    /**
     * Creates a new instance of {@link CrossDeviceSyncTestLib}.
     *
     * @param context the application context.
     * @return a new instance of the test library.
     */
    public static CrossDeviceSyncTestLib newInstance(Context context) {
        return new CrossDeviceSyncTestLib(context);
    }

    /**
     * Returns the {@link ContextualModeSyncDocument} for managing DND state sync.
     *
     * @return the DND sync document.
     */
    public ContextualModeSyncDocument getModeSyncDocument() {
        return mContextualModeSyncDocument;
    }

    /**
     * Returns the {@link AirplaneModeSyncDocument} for managing Airplane Mode state sync.
     *
     * @return the Airplane Mode sync document.
     */
    public AirplaneModeSyncDocument getAirplaneModeSyncDocument() {
        return mAirplaneModeSyncDocument;
    }

    /**
     * Returns the {@link CdmPipe} for simulating CDM transport.
     *
     * @return the simulated CDM transport pipe.
     */
    public CdmPipe getCdmPipe() {
        return mCdmPipe;
    }

    @Override
    public void close() {
        mContextualModeSyncDocument.close();
        mAirplaneModeSyncDocument.close();
        mMetadataPublisher.destroy();
        mNetworkManager.destroy();
        mStorage.runInIoThread(mStorage::close);
        mStorage.shutdownIoThread();
    }
}
