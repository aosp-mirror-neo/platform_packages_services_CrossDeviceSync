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
package com.android.crossdevicesync.data.fake;

import static java.util.Objects.requireNonNull;

import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.dagger.TestSharedDataStoreComponent;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.android.submerge.Converter;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/**
 * A wrapper of the dagger {@link TestSharedDataStoreComponent.Builder} for capturing {@link
 * TestSharedDataStoreComponent} instances and access them in tests.
 */
@SyncServiceSingleton
public class TestSharedDataStoreComponentBuilderWrapper
        implements TestSharedDataStoreComponent.Builder {
    private final TestSharedDataStoreComponent.Builder mBuilder;
    private final Map<String, TestSharedDataStoreComponent> mComponents = new HashMap<>();
    private String mName;

    @Inject
    TestSharedDataStoreComponentBuilderWrapper(TestSharedDataStoreComponent.Builder builder) {
        mBuilder = builder;
    }

    @Override
    public TestSharedDataStoreComponentBuilderWrapper name(String name) {
        mBuilder.name(name);
        mName = name;
        return this;
    }

    @Override
    public TestSharedDataStoreComponentBuilderWrapper converter(Converter<?> converter) {
        mBuilder.converter(converter);
        return this;
    }

    @Override
    public TestSharedDataStoreComponentBuilderWrapper schemaProvider(
            SchemaProvider<?> schemaProvider) {
        mBuilder.schemaProvider(schemaProvider);
        return this;
    }

    @Override
    public TestSharedDataStoreComponentBuilderWrapper storage(IStorage storage) {
        mBuilder.storage(storage);
        return this;
    }

    @Override
    public TestSharedDataStoreComponent build() {
        TestSharedDataStoreComponent component = mBuilder.build();
        mComponents.put(requireNonNull(mName), component);
        return component;
    }

    /** Get the last created {@link TestSharedDataStoreComponent} from its name. */
    public TestSharedDataStoreComponent getComponent(String name) {
        return mComponents.get(name);
    }
}
