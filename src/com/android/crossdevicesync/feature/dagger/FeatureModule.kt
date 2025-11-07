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
package com.android.crossdevicesync.feature.dagger

import com.android.crossdevicesync.data.SchemaProvider
import com.android.crossdevicesync.data.SharedDataStore
import com.android.crossdevicesync.data.SharedDataStoreFactory
import com.android.crossdevicesync.data.dagger.SharedDataStoreComponent
import com.android.crossdevicesync.feature.airplanemode.dagger.AirplaneModeSyncModule
import com.android.crossdevicesync.feature.mode.dagger.ModeSyncModule
import com.android.crossdevicesync.network.NetworkManager
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton
import com.google.android.submerge.Converter
import dagger.Module
import dagger.Provides

/** Dagger module for feature code. */
@Module(includes = [ModeSyncModule::class, AirplaneModeSyncModule::class])
object FeatureModule {
    @Provides
    @SyncServiceSingleton
    fun providesSharedDataStoreFactory(componentBuilder: SharedDataStoreComponent.Builder) =
        object : SharedDataStoreFactory {
            @Suppress("UNCHECKED_CAST") // Component uses generics due to limitations.
            override fun <T> create(
                name: String,
                converter: Converter<T>,
                schemaProvider: SchemaProvider<T>,
                network: NetworkManager.Network,
            ): SharedDataStore<T> =
                componentBuilder
                    .name(name)
                    .converter(converter)
                    .schemaProvider(schemaProvider)
                    .network(network)
                    .build()
                    .getSharedDataStore() as SharedDataStore<T>
        }
}
