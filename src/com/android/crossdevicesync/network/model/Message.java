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
import java.util.Arrays;
import java.util.Objects;

/** A message to send or receive from CompanionDeviceManager. */
@SuppressWarnings("ArrayRecordComponent")
public record Message(long handleId, @NonNull String networkId, @NonNull byte[] payload) {
    private static final String TAG = "Message";

    /** Serializes this {@link Message} into a byte array. */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos)) {
            writeTo(out);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Message serialization failed", e);
        }
    }

    /** Deserializes a {@link Message} record from a byte array. */
    public static Message parseFrom(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream in = new DataInputStream(bais)) {
            return readFrom(in);
        }
    }

    /** Reads a {@link Message} record from a {@link DataInputStream}. */
    public static Message readFrom(DataInputStream in) throws IOException {
        long handleId = in.readLong();
        String networkId = in.readUTF();
        int payloadLength = in.readInt();
        byte[] payload = new byte[payloadLength];
        in.readFully(payload);
        return new Message(handleId, networkId, payload);
    }

    /** Writes this {@link Message} to a {@link DataOutputStream}. */
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeLong(handleId);
        out.writeUTF(networkId);
        out.writeInt(payload.length);
        out.write(payload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message message)) return false;
        return handleId == message.handleId
                && Objects.equals(networkId, message.networkId)
                && Arrays.equals(payload, message.payload);
    }
}
