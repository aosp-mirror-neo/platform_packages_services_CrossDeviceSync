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

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import com.android.crossdevicesync.feature.FeatureManager;
import com.android.crossdevicesync.network.NetworkManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.util.Map;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public final class SyncServiceTest {

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Test
    public void testStartService_serviceStarted() throws TimeoutException {
        mServiceRule.startService(
                new Intent(ApplicationProvider.getApplicationContext(), SyncService.class)
                        .setAction(SyncService.ACTION_BIND_FOR_TESTING));
    }

    @Test
    public void testFeatureManagerLifeCycle() {
        // Create a new service with mock feature manager.
        NetworkManager networkManager = mock(NetworkManager.class);
        FeatureManager featureManager = mock(FeatureManager.class);
        SyncService service =
                new SyncService(networkManager, Map.of(FeatureManager.class, () -> featureManager));
        InOrder inOrder = inOrder(networkManager, featureManager);

        // Service created.
        service.onCreate();

        inOrder.verify(networkManager).init();
        inOrder.verify(featureManager).init();

        // Service destroyed.
        service.onDestroy();

        // Verify destroying each feature before destroying the networkManager.
        inOrder.verify(featureManager).destroy();
        inOrder.verify(networkManager).destroy();
    }
}
