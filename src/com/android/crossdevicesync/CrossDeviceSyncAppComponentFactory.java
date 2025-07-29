/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.crossdevicesync;

import android.annotation.Nullable;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.util.Log;

import com.android.crossdevicesync.dagger.ApplicationComponent;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Implementation of AppComponentFactory that injects into constructors.
 *
 * <p>This class sets up dependency injection when creating our application.
 */
public class CrossDeviceSyncAppComponentFactory extends AppComponentFactory {
    private static final String TAG = "XDeviceSyncApp";

    @Nullable private CrossDeviceSyncApplication mApplication;
    @Nullable @Inject Map<Class<?>, Provider<Service>> mServiceProviders;
    @Nullable @Inject Map<Class<?>, Provider<BroadcastReceiver>> mReceiverProviders;

    @Override
    public Application instantiateApplication(ClassLoader cl, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Application app = super.instantiateApplication(cl, className);
        mApplication = (CrossDeviceSyncApplication) app;
        return app;
    }

    @Override
    public Service instantiateService(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Service service = resolveService(className);
        if (service != null) {
            return service;
        }
        return super.instantiateService(cl, className, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        BroadcastReceiver receiver = resolveReceiver(className);
        if (receiver != null) {
            return receiver;
        }
        return super.instantiateReceiver(cl, className, intent);
    }

    private void injectBindings() {
        if (mApplication == null) {
            Log.e(TAG, "injectBindings: no application!");
            return;
        }
        ApplicationComponent component = mApplication.getApplicationComponent();
        if (component == null) {
            Log.e(TAG, "injectBindings: unable to get dagger application component!");
            return;
        }
        component.inject(this);
    }

    @Nullable
    private Map<Class<?>, Provider<Service>> getServiceProviders() {
        if (mServiceProviders != null) {
            return mServiceProviders;
        }
        injectBindings();
        return mServiceProviders;
    }

    @Nullable
    private Map<Class<?>, Provider<BroadcastReceiver>> getReceiverProviders() {
        if (mReceiverProviders != null) {
            return mReceiverProviders;
        }
        injectBindings();
        return mReceiverProviders;
    }

    @Nullable
    private Service resolveService(String className) {
        Map<Class<?>, Provider<Service>> serviceProviders = getServiceProviders();
        if (serviceProviders == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className);
            Provider<Service> provider = serviceProviders.get(clazz);
            return provider == null ? null : provider.get();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Nullable
    private BroadcastReceiver resolveReceiver(String className) {
        Map<Class<?>, Provider<BroadcastReceiver>> receiverProviders = getReceiverProviders();
        if (receiverProviders == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className);
            Provider<BroadcastReceiver> provider = receiverProviders.get(clazz);
            return provider == null ? null : provider.get();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
