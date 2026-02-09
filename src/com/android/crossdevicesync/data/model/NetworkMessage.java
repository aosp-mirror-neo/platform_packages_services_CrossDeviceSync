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

package com.android.crossdevicesync.data.model;

import android.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** A network message used for syncing data between devices. */
@SuppressWarnings("ArrayRecordComponent")
public record NetworkMessage(
        @NonNull String docId,
        @NonNull String senderNodeId,
        @NonNull String receiverNodeId,
        @NonNull byte[] docVersion,
        @NonNull byte[] docUpdate) {
    private static final String TAG = "NetworkMessage";

    /** Serializes this {@link NetworkMessage} into a byte array. */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos)) {
            out.writeUTF(docId);
            out.writeUTF(senderNodeId);
            out.writeUTF(receiverNodeId);
            out.writeInt(docVersion.length);
            out.write(docVersion);
            out.writeInt(docUpdate.length);
            out.write(docUpdate);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("NetworkMessage serialization failed", e);
        }
    }

    /** Deserializes a {@link NetworkMessage} record from a byte array. */
    public static NetworkMessage parseFrom(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream in = new DataInputStream(bais)) {
            String docId = in.readUTF();
            String senderNodeId = in.readUTF();
            String receiverNodeId = in.readUTF();
            byte[] docVersion = new byte[in.readInt()];
            in.readFully(docVersion);
            byte[] docUpdate = new byte[in.readInt()];
            in.readFully(docUpdate);
            return new NetworkMessage(docId, senderNodeId, receiverNodeId, docVersion, docUpdate);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkMessage that)) return false;
        return Objects.equals(docId, that.docId)
                && Objects.equals(senderNodeId, that.senderNodeId)
                && Objects.equals(receiverNodeId, that.receiverNodeId)
                && Arrays.equals(docVersion, that.docVersion)
                && Arrays.equals(docUpdate, that.docUpdate);
    }
}
