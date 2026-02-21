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

package com.android.crossdevicesync.lib;

import android.companion.cts.common.CdmMessage;
import android.companion.cts.common.MessageDemuxer;
import android.companion.cts.common.MessageFeeder;
import android.util.Log;

import com.android.crossdevicesync.lib.api.CdmPipe;

import kotlin.Unit;

import java.util.function.Consumer;

/**
 * Implementation of {@link CdmPipe}.
 *
 * <p>This class uses {@link MessageFeeder} and {@link MessageDemuxer} directly to simulate CDM
 * transport. It provides covariant return types for the transport streams to allow the CDM proxy
 * and tests to use advanced message-based APIs.
 */
public class CdmPipeImpl implements CdmPipe {
    private static final String TAG = "CdmPipeImpl";

    private final MessageFeeder mOutboundFeeder = new MessageFeeder();
    private Consumer<CdmMessage> mInboundListener;
    private final MessageDemuxer mInboundDemuxer =
            new MessageDemuxer(
                    message -> {
                        if (mInboundListener != null) {
                            Log.d(TAG, "Inbound message received from transport: " + message);
                            mInboundListener.accept(message);
                        }
                        return Unit.INSTANCE;
                    });

    /** Constructs a new {@link CdmPipeImpl}. */
    public CdmPipeImpl() {
        Log.d(TAG, "CdmPipeImpl initialized.");
    }

    /**
     * Sets a listener to be notified when a message is received via the inbound transport stream.
     *
     * @param listener the listener to be notified.
     */
    public void setInboundMessageListener(Consumer<CdmMessage> listener) {
        mInboundListener = listener;
    }

    @Override
    public MessageFeeder getInputStreamForCdmTransport() {
        return mOutboundFeeder;
    }

    @Override
    public MessageDemuxer getOutputStreamForCdmTransport() {
        return mInboundDemuxer;
    }
}
