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

package com.android.crossdevicesync.data;

import android.annotation.NonNull;

import com.android.crossdevicesync.data.dagger.SharedDataStoreComponent;
import com.android.crossdevicesync.data.dagger.qualifier.GlobalStorage;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.data.storage.StorageNamespaceDecorator;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import com.google.android.submerge.Converter;

import javax.inject.Inject;

/** A factory to create a {@link SharedDataStoreHandle}. */
@SyncServiceSingleton
public class SharedDataStoreHandleFactory {
    private final SharedDataStoreComponent.Builder mComponentBuilder;
    private final IStorage mGlobalStorage;

    @Inject
    public SharedDataStoreHandleFactory(
            SharedDataStoreComponent.Builder componentBuilder,
            @GlobalStorage IStorage globalStorage) {
        mComponentBuilder = componentBuilder;
        mGlobalStorage = globalStorage;
    }

    /**
     * Creates a {@link SharedDataStoreHandle}
     *
     * @param name The name of the data store
     * @param converter used to serialize/deserialize the data
     * @param schemaProvider The {@link SchemaProvider} for the data store
     * @param <T> The type of the data stored in the data store.
     * @return A {@link SharedDataStoreHandle} for the given data store.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T> SharedDataStoreHandle<T> create(
            @NonNull String name,
            @NonNull Converter<T> converter,
            @NonNull SchemaProvider<T> schemaProvider) {
        return (SharedDataStoreHandle<T>)
                mComponentBuilder
                        .name(name)
                        .converter(converter)
                        .schemaProvider(schemaProvider)
                        // Use the global storage with the namespace decorator to reuse the same
                        // database
                        // connection and reduce memory cost for multiple databases.
                        .storage(new StorageNamespaceDecorator(name, mGlobalStorage))
                        .build()
                        .getSharedDataStoreHandle();
    }
}
