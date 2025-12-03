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

import android.util.Log
import androidx.annotation.GuardedBy
import com.android.crossdevicesync.data.storage.IStorage
import com.android.crossdevicesync.network.NetworkManager.Network
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import javax.inject.Provider

/** A [SharedDataStoreHandle] that represents a [SharedDataStore]. */
class DefaultSharedDataStoreHandle<T>(
    override val name: String,
    private val mLock: Object,
    private val mNodeIdProvider: DeviceNodeIdProvider,
    private val mStorage: IStorage,
    private val mIoExecutor: Provider<ListeningExecutorService>,
    private val mSharedDataStoreFactory: SharedDataStoreFactory<T>,
) : SharedDataStoreHandle<T> {
    @GuardedBy("mLock") private var mLockedDataStore: SharedDataStore<T>? = null
    @GuardedBy("mLock") private var mState = STATE_OPEN

    override fun isLocked(): Boolean = synchronized(mLock) { mLockedDataStore != null }

    override fun lock(dataStore: SharedDataStore<T>): Boolean {
        synchronized(mLock) {
            requireOpenLocked()
            if (mLockedDataStore == null) {
                mLockedDataStore = dataStore
                return true
            } else {
                return false
            }
        }
    }

    override fun unlock(dataStore: SharedDataStore<T>) {
        synchronized(mLock) {
            requireNotDestroyedLocked()
            if (mLockedDataStore == dataStore) {
                mLockedDataStore = null
            }
        }
    }

    override fun getLockedDataStore(): SharedDataStore<T>? =
        synchronized(mLock) { mLockedDataStore }

    override fun openDataStore(network: Network): ListenableFuture<SharedDataStore<T>> {
        synchronized(mLock) {
            try {
                requireOpenLocked()
                if (mLockedDataStore != null) {
                    return Futures.immediateFuture(mLockedDataStore)
                }
                return initializeDataStore(
                    mSharedDataStoreFactory.create(),
                    network,
                    /* shouldRetry= */ true,
                )
            } catch (e: IllegalStateException) {
                return Futures.immediateFailedFuture(e)
            }
        }
    }

    @GuardedBy("mLock")
    private fun requireOpenLocked() {
        check(mState == STATE_OPEN) { "Illegal state ${stateToString(mState)} in handle $name" }
    }

    @GuardedBy("mLock")
    private fun requireNotDestroyedLocked() {
        check(mState != STATE_DESTROYED) {
            "Illegal state ${stateToString(mState)} in handle $name"
        }
    }

    private fun initializeDataStore(
        dataStoreToInit: SharedDataStore<T>,
        network: Network,
        shouldRetry: Boolean,
    ): ListenableFuture<SharedDataStore<T>> {
        synchronized(mLock) {
            requireOpenLocked()
            return FluentFuture.from(dataStoreToInit.init(network))
                .transform({ _ -> dataStoreToInit }, directExecutor())
                .catchingAsync(
                    Exception::class.java,
                    { e ->
                        if (
                            shouldRetry &&
                                (e is IllegalSchemaChangeException ||
                                    e is SchemaValidationException)
                        ) {
                            Log.w(
                                TAG,
                                "Schema error occurred, deleting the data store $dataStoreToInit",
                                e,
                            )
                            FluentFuture.from(delete())
                                .transformAsync(
                                    { _ ->
                                        initializeDataStore(
                                            mSharedDataStoreFactory.create(),
                                            network,
                                            /* shouldRetry= */ false,
                                        )
                                    },
                                    directExecutor(),
                                )
                        } else {
                            Futures.immediateFailedFuture(e)
                        }
                    },
                    directExecutor(),
                )
        }
    }

    private fun doDelete(forDestroying: Boolean): Boolean {
        synchronized(mLock) {
            if (forDestroying) {
                requireNotDestroyedLocked()
            } else {
                requireOpenLocked()
            }
            check(!isLocked()) { "Cannot delete locked data store $name" }
            if (mStorage.deleteDatabase()) {
                mNodeIdProvider.noteDataStoreDeletion(name)
                Log.w(TAG, "Deleted data store \"$name\".")
                return true
            } else {
                return false
            }
        }
    }

    override fun delete(): ListenableFuture<Boolean> = delete(forDestroying = false)

    private fun delete(forDestroying: Boolean): ListenableFuture<Boolean> {
        synchronized(mLock) {
            try {
                if (forDestroying) {
                    requireNotDestroyedLocked()
                } else {
                    requireOpenLocked()
                }
                check(!isLocked()) { "Cannot delete locked data store $name" }
                Log.w(TAG, "Deleting data store \"$name\".")
                return mIoExecutor.get().submit<Boolean> { doDelete(forDestroying) }
            } catch (e: IllegalStateException) {
                return Futures.immediateFailedFuture(e)
            }
        }
    }

    override fun destroy(deleteDataStore: Boolean) {
        synchronized(mLock) {
            if (mState != STATE_OPEN) {
                // Duplicate call
                return
            }
            mState = STATE_DESTROYING
            Log.i(TAG, "Destroying handle $name")
            var future = mLockedDataStore?.close() ?: Futures.immediateVoidFuture()
            if (deleteDataStore) {
                future =
                    Futures.transformAsync(
                        future,
                        { _ -> delete(forDestroying = true) },
                        directExecutor(),
                    )
            }
            future.addListener(
                {
                    noteDestroyed()
                    mIoExecutor.get().shutdown()
                },
                directExecutor(),
            )
        }
    }

    private fun noteDestroyed() {
        synchronized(mLock) {
            if (mState == STATE_DESTROYED) {
                return
            }
            mState = STATE_DESTROYED
            Log.i(TAG, "Handle $name is destroyed")
        }
    }

    override fun toString(): String = "$TAG[$name]" + (if (isLocked()) " (locked)" else "")

    companion object {
        private const val TAG = "SharedDataStoreHandle"
        private const val STATE_OPEN = 0
        private const val STATE_DESTROYING = 1
        private const val STATE_DESTROYED = 2

        private fun stateToString(state: Int): String =
            when (state) {
                STATE_OPEN -> "OPEN"
                STATE_DESTROYING -> "DESTROYING"
                STATE_DESTROYED -> "DESTROYED"
                else -> "UNKNOWN($state)"
            }
    }
}
