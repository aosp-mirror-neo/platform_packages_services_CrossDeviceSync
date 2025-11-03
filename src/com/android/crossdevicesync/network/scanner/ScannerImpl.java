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
package com.android.crossdevicesync.network.scanner;

import android.util.IndentingPrintWriter;

import com.android.crossdevicesync.services.dagger.SyncServiceSingleton;

import javax.inject.Inject;

/** Default implementation of {@link Scanner}. */
@SyncServiceSingleton
public class ScannerImpl implements Scanner {
    @Inject
    ScannerImpl() {}

    @Override
    public ScanningSession startScanning(int associationId, String requestName) {
        return null;
    }

    @Override
    public void closeAllScanningSessions() {}

    @Override
    public void dump(IndentingPrintWriter pw) {}
}
