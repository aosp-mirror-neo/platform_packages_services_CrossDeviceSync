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

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Interface for managing the "Do Not Disturb" (DND) state sync document during tests. */
public interface ContextualModeSyncDocument extends AutoCloseable {
    /**
     * Returns whether DND is currently enabled.
     *
     * @return true if DND is enabled, false otherwise.
     */
    boolean isDndEnabled();

    /**
     * Sets the DND enabled state.
     *
     * @param enabled true to enable DND, false to disable.
     */
    void setDndEnabled(boolean enabled);

    /**
     * Registers a listener to be notified when the DND state changes remotely.
     *
     * @param executor the executor to run the listener on.
     * @param listener the listener to be notified.
     */
    void addOnRemoteDndChangedListener(Executor executor, Consumer<Boolean> listener);

    /**
     * Removes a previously registered DND state change listener.
     *
     * @param listener the listener to be removed.
     */
    void removeOnRemoteDndChangedListener(Consumer<Boolean> listener);
}
