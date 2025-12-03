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
package com.android.crossdevicesync.data

import com.android.crossdevicesync.data.dagger.SharedDataStoreComponent
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton
import com.google.android.submerge.Converter
import javax.inject.Inject

/** A factory to create a [SharedDataStoreHandle]. */
@SyncServiceSingleton
class SharedDataStoreHandleFactory
@Inject
constructor(private val mComponentBuilder: SharedDataStoreComponent.Builder) {
    /**
     * Creates a [SharedDataStoreHandle]
     *
     * @param name The name of the data store
     * @param converter used to serialize/deserialize the data
     * @param schemaProvider The [SchemaProvider] for the data store
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> create(
        name: String,
        converter: Converter<T>,
        schemaProvider: SchemaProvider<T>,
    ): SharedDataStoreHandle<T> =
        mComponentBuilder
            .name(name)
            .converter(converter)
            .schemaProvider(schemaProvider)
            .build()
            .sharedDataStoreHandle as SharedDataStoreHandle<T>
}
