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

package com.android.crossdevicesync.lib.api;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface for interacting with the Companion Device Manager (CDM) transport during tests.
 *
 * <p>This allows tests to intercept and inject messages exchanged between the device and the CDM
 * transport. It provides access to the underlying streams that should be attached to the system
 * transport.
 */
public interface CdmPipe {
    /**
     * Returns an {@link InputStream} that can be passed to {@link
     * android.companion.CompanionDeviceManager#attachSystemDataTransport(int, InputStream,
     * OutputStream)}.
     *
     * <p>This stream serves as the source of data coming from the remote device to the local
     * system.
     *
     * @return the input stream for the CDM transport.
     */
    InputStream getInputStreamForCdmTransport();

    /**
     * Returns an {@link OutputStream} that can be passed to {@link
     * android.companion.CompanionDeviceManager#attachSystemDataTransport(int, InputStream,
     * OutputStream)}.
     *
     * <p>This stream serves as the destination for data sent from the local system to the remote
     * device.
     *
     * @return the output stream for the CDM transport.
     */
    OutputStream getOutputStreamForCdmTransport();
}
