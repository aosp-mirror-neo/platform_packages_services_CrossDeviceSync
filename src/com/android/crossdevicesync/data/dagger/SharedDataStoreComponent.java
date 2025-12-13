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
package com.android.crossdevicesync.data.dagger;

import com.android.crossdevicesync.data.SchemaProvider;
import com.android.crossdevicesync.data.SharedDataStoreHandle;
import com.android.crossdevicesync.data.dagger.qualifier.DataStoreName;
import com.android.crossdevicesync.data.storage.IStorage;

import com.google.android.submerge.Converter;

import dagger.BindsInstance;
import dagger.Subcomponent;

/**
 * Dagger subcomponent for {@link SharedDataStoreSingleton} scope. This corresponds to an individual
 * data store.
 *
 * <p>Note: sync service can have multiple data stores, each have their own {@link
 * SharedDataStoreComponent}.
 */
@SharedDataStoreSingleton
@Subcomponent(modules = {SharedDataStoreModule.class})
public interface SharedDataStoreComponent {

    /** Get the {@link SharedDataStoreHandle} for this data store. */
    SharedDataStoreHandle<?> getSharedDataStoreHandle();

    /** Builder interface for SharedDataStoreComponent. */
    @Subcomponent.Builder
    interface Builder {
        /**
         * Set the name of the data store. This will affect the database name so should not change
         * afterwards.
         */
        @BindsInstance
        Builder name(@DataStoreName String name);

        /** Binds a {@link Converter} that can be used to serialize/deserialize the data. */
        @BindsInstance
        Builder converter(Converter<?> converter);

        /** Set the schema provider for the data store. */
        @BindsInstance
        Builder schemaProvider(SchemaProvider<?> schemaProvider);

        /** Set the storage for the data store. */
        @BindsInstance
        Builder storage(IStorage storage);

        /** Build an instance of this component. */
        SharedDataStoreComponent build();
    }
}
