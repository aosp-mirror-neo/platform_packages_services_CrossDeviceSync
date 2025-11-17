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

import com.android.crossdevicesync.network.NetworkManager.Network
import com.google.android.submerge.Converter

/** A factory to create a [SharedDataStore]. */
interface SharedDataStoreFactory {
    /**
     * Creates a [SharedDataStore]
     *
     * @param name The name of the data store
     * @param converter used to serialize/deserialize the data
     * @param schemaProvider The [SchemaProvider] for the data store
     * @param network The [Network] to sync the data store with.
     */
    fun <T> create(
        name: String,
        converter: Converter<T>,
        schemaProvider: SchemaProvider<T>,
        network: Network,
    ): SharedDataStore<T>
}
