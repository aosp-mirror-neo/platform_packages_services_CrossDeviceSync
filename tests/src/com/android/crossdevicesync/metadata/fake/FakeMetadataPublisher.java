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
package com.android.crossdevicesync.metadata.fake;

import android.util.IndentingPrintWriter;

import com.android.crossdevicesync.metadata.MetadataPublisher;
import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import javax.inject.Inject;

/** Fake implementation of {@link MetadataPublisher}. */
@SyncServiceSingleton
public class FakeMetadataPublisher implements MetadataPublisher {
    @Inject
    FakeMetadataPublisher() {}

    @Override
    public void init() {}

    @Override
    public void destroy() {}

    @Override
    public void putBooleanMetaData(int userId, String key, boolean val) {}

    @Override
    public void putIntMetaData(int userId, String key, int val) {}

    @Override
    public void putStringMetaData(int userId, String key, String val) {}

    @Override
    public void dump(IndentingPrintWriter pw) {}
}
