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
package com.android.crossdevicesync.data.fake;

import android.annotation.Nullable;

import com.android.crossdevicesync.data.IStorage;
import com.android.crossdevicesync.data.dagger.SharedDataStoreSingleton;

import com.google.android.submerge.StorageInterface;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/** Fake implementation of {@link StorageInterface}. */
@SharedDataStoreSingleton
public class FakeStorage implements IStorage {

    private final Map<String, byte[]> mDocuments = new HashMap<>();
    private final Map<String, byte[]> mMetadata = new HashMap<>();

    @Inject
    FakeStorage() {}

    @Override
    public void onNewUpdate(String docId, byte[] serializedDoc) throws StorageException {
        mDocuments.put(docId, serializedDoc);
    }

    @Override
    @Nullable
    public byte[] readFromStorage(String docId) throws StorageException {
        return mDocuments.get(docId);
    }

    @Override
    @Nullable
    public byte[] getMetadata(String docId) throws StorageException {
        return mMetadata.get(docId);
    }

    @Override
    public void persistMetadata(String docId, byte[] metadata) throws StorageException {
        mMetadata.put(docId, metadata);
    }
}
