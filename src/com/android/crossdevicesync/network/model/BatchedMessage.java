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

package com.android.crossdevicesync.network.model;

import android.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A batched message format that groups multiple messages and multiple ACKs together. */
public record BatchedMessage(
        @NonNull List<Message> messages, @NonNull List<Long> ackIds, long senderInstanceId) {
    private static final String TAG = "BatchedMessage";

    /** Serializes this {@link BatchedMessage} into a byte array. */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(messages.size());
            for (Message m : messages) {
                m.writeTo(out);
            }
            out.writeInt(ackIds.size());
            for (long ackId : ackIds) {
                out.writeLong(ackId);
            }
            out.writeLong(senderInstanceId);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("BatchedMessage serialization failed", e);
        }
    }

    /** Deserializes a {@link BatchedMessage} record from a byte array. */
    public static BatchedMessage parseFrom(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream in = new DataInputStream(bais)) {
            int messagesSize = in.readInt();
            List<Message> messages = new ArrayList<>(messagesSize);
            for (int i = 0; i < messagesSize; i++) {
                messages.add(Message.readFrom(in));
            }
            int acksSize = in.readInt();
            List<Long> ackIds = new ArrayList<>(acksSize);
            for (int i = 0; i < acksSize; i++) {
                ackIds.add(in.readLong());
            }
            long senderInstanceId = in.readLong();
            return new BatchedMessage(messages, ackIds, senderInstanceId);
        }
    }
}
