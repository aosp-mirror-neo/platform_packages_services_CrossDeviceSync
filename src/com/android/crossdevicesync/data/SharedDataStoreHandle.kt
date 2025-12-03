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
import com.google.common.util.concurrent.ListenableFuture

/**
 * A handle to a [SharedDataStore]. Used to perform operations without opening a [SharedDataStore].
 */
interface SharedDataStoreHandle<T> {

    /** Get the data store name. */
    val name: String

    /**
     * Check if the data store is locked. A data store is locked when it's open or in the middle of
     * close. While it's locked, it can't be deleted or re-initialized.
     */
    fun isLocked(): Boolean

    /** Lock this handle. */
    fun lock(dataStore: SharedDataStore<T>): Boolean

    /** Unlock this handle. */
    fun unlock(dataStore: SharedDataStore<T>)

    /** If the data store is locked, get the locked data store. */
    fun getLockedDataStore(): SharedDataStore<T>?

    /**
     * Open and initialize a data store. If the data store is already locked, return the locked data
     * store instead. If a schema error occurs when opening the data store, the data store will be
     * deleted and open will be retried.
     *
     * @param network the network to be used for syncing
     * @return a future containing the initialized data store, or an exception
     */
    fun openDataStore(network: Network): ListenableFuture<out SharedDataStore<T>>

    /**
     * Delete the shared data store. This operation is non-recoverable.
     *
     * @return a [ListenableFuture] that completes with a boolean indicating if the data store is
     *   successfully deleted.
     * @throws IllegalStateException if the data store is locked.
     */
    fun delete(): ListenableFuture<Boolean>

    /** Orderly close the data store if it's open, and release all resources. */
    fun destroy() = destroy(false)

    /**
     * Orderly close the data store if it's open, and release all resources. Can optionally delete
     * the data store as part of the clean-up.
     */
    fun destroy(deleteDataStore: Boolean)
}
