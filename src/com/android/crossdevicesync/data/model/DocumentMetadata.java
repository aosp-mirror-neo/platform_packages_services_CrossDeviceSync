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
import java.util.ArrayList;
import java.util.List;

/** Metadata of a document and its records. */
public record DocumentMetadata(
        int schemaVersion, @NonNull List<RecordMetadata> recordMetadataList) {
    private static final String TAG = "DocumentMetadata";

    /** Metadata of a data record in a document. */
    public record RecordMetadata(@NonNull String path, boolean lastModifiedByLocalDevice) {}

    /** Serializes this {@link DocumentMetadata} into a byte array. */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(schemaVersion);
            out.writeInt(recordMetadataList.size());
            for (RecordMetadata metadata : recordMetadataList) {
                out.writeUTF(metadata.path());
                out.writeBoolean(metadata.lastModifiedByLocalDevice());
            }
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("DocumentMetadata serialization failed", e);
        }
    }

    /** Deserializes a {@link DocumentMetadata} record from a byte array. */
    public static DocumentMetadata parseFrom(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream in = new DataInputStream(bais)) {
            int schemaVersion = in.readInt();
            int size = in.readInt();
            List<RecordMetadata> recordMetadataList = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String path = in.readUTF();
                boolean lastModifiedByLocalDevice = in.readBoolean();
                recordMetadataList.add(new RecordMetadata(path, lastModifiedByLocalDevice));
            }
            return new DocumentMetadata(schemaVersion, recordMetadataList);
        }
    }
}
