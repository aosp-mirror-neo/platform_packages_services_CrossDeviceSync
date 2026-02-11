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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.feature.FeatureManager;
import com.android.crossdevicesync.metadata.MetadataPublisher;
import com.android.crossdevicesync.network.NetworkManager;
import com.android.crossdevicesync.notification.NotificationHelper;
import com.android.internal.util.FunctionalUtils.ThrowingRunnable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
public final class SyncServiceTest {

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private final NetworkManager mNetworkManager = mock(NetworkManager.class);
    private final MetadataPublisher mMetadataPublisher = mock(MetadataPublisher.class);
    private final NotificationHelper mNotificationHelper = mock(NotificationHelper.class);
    private final IStorage mStorage = mock(IStorage.class);
    private final FeatureManager mFeatureManager = mock(FeatureManager.class);

    @Before
    public void setUp() {
        SyncService.sInjector =
                new SyncServiceInjector() {
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
                        return mStorage;
                    }

                    @Override
                    public SyncServiceShellCommand getSyncServiceShellCommand() {
                        return new SyncServiceShellCommand(mock(NotificationHelper.class));
                    }

                    @Override
                    public Map<String, Supplier<FeatureManager>> getFeatureManagers() {
                        return Map.of(
                                mFeatureManager.getClass().getSimpleName(), () -> mFeatureManager);
                    }
                };
    }

    @After
    public void tearDown() {
        SyncService.sInjector = null;
    }

    @Test
    public void testStartService_serviceStarted() throws TimeoutException {
        mServiceRule.startService(
                new Intent(ApplicationProvider.getApplicationContext(), SyncService.class)
                        .setAction(SyncService.ACTION_BIND_FOR_TESTING));
    }

    @Test
    public void testFeatureManagerLifeCycle() {
        SyncService service = new SyncService();
        InOrder inOrder =
                inOrder(
                        mNetworkManager,
                        mMetadataPublisher,
                        mNotificationHelper,
                        mFeatureManager,
                        mStorage);

        // Service created.
        service.onCreate();

        inOrder.verify(mNetworkManager).init();
        inOrder.verify(mMetadataPublisher).init();
        inOrder.verify(mNotificationHelper).init();
        inOrder.verify(mFeatureManager).init();

        // Service destroyed.
        service.onDestroy();

        // Verify destroying each feature before destroying the metadata publisher and the
        // networkManager.
        inOrder.verify(mFeatureManager).destroy();
        inOrder.verify(mNotificationHelper).destroy();
        inOrder.verify(mMetadataPublisher).destroy();
        inOrder.verify(mNetworkManager).destroy();
        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        inOrder.verify(mStorage).runInIoThread(captor.capture());
        inOrder.verify(mStorage).shutdownIoThread();

        captor.getValue().run();
        verify(mStorage).close();
    }
}
