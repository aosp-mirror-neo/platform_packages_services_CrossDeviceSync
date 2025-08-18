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
package com.android.crossdevicesync.data;

import static java.util.Objects.requireNonNull;

import android.util.ArrayMap;

import androidx.annotation.IntDef;

import com.android.crossdevicesync.data.SharedDataStore.Document;
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;

/**
 * An interface responsible for providing schema related information
 *
 * @param <T> the type of data managed by data store.
 */
public interface SchemaProvider<T> {

    /** Denotes the type of a record, which can be a register, a set, or an unmerged entry. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_REGISTER, TYPE_SET, TYPE_UNMERGED})
    @interface RecordType {}

    /**
     * A record type that represents a single, last-write-wins value. This type corresponds to
     * {@link com.android.crossdevicesync.data.SharedDataStore.Record}.
     */
    int TYPE_REGISTER = 0;

    /**
     * A record type that represents a collection of unique values. This type corresponds to {@link
     * com.android.crossdevicesync.data.SharedDataStore.SetRecord}.
     */
    int TYPE_SET = 1;

    /**
     * A record type that represents a value that will not merge. Because the data is unmerged, each
     * device writes into its own copy. As a result, the record maintains a map between device node
     * id and the last known value. This type corresponds to {@link
     * com.android.crossdevicesync.data.SharedDataStore.UnmergedRecord}.
     */
    int TYPE_UNMERGED = 2;

    /** Gets all document schemas. */
    List<DocumentSchemaInfo> getAllDocumentSchema();

    /**
     * Perform migration of a document after a schema version change. This will be called on IO
     * thread after schema change has been applied. For example, this can be used to copy or
     * transfer data from an older path to new path.
     *
     * <p>Warning: implementation should not assume the new path is empty. It's possible that the
     * new path already contains a data synced from a remote device. Whatever change made in this
     * method will be considered a new change and synced to remote devices. You may risk overriding
     * remote device's current value unexpectedly if you update a path without caution.
     */
    void migrateDocument(MutableDocument<T> document);

    /**
     * Perform a schema validation on a document. This will be called on IO thread before a
     * transaction is committed.
     *
     * @throws Exception if validation fails.
     */
    void validateDocument(Document<T> document) throws Exception;

    /**
     * Data object representing a document schema.
     *
     * <p>Warning: schema change must be backwards compatible. If an existing schema is modified in
     * an incompatible way (e.g. a path is removed or its data type is changed), the schema change
     * itself will be synced to older devices and may break them.
     */
    class DocumentSchemaInfo {
        private final String mDocId;
        private final int mVersion;
        private final Map<String, Integer> mPathSchema;

        private DocumentSchemaInfo(String docId, int version, Map<String, Integer> pathDataType) {
            mDocId = docId;
            mVersion = version;
            mPathSchema = pathDataType;
        }

        public String getDocId() {
            return mDocId;
        }

        public int getVersion() {
            return mVersion;
        }

        public Map<String, Integer> getPathSchema() {
            return mPathSchema;
        }

        /** Builder for {@link DocumentSchemaInfo}. */
        public static final class Builder {
            private String mDocId;
            private int mVersion;
            private final Map<String, Integer> mPathSchema = new ArrayMap<>();

            public Builder() {}

            /** Sets the document ID. */
            public Builder setDocId(String docId) {
                mDocId = docId;
                return this;
            }

            /** Sets the schema version. */
            public Builder setVersion(int version) {
                mVersion = version;
                return this;
            }

            /** Adds or updates a path schema. */
            public Builder putPathSchema(String path, @RecordType int type) {
                mPathSchema.put(path, type);
                return this;
            }

            /** Builds the {@link DocumentSchemaInfo} object. */
            public DocumentSchemaInfo build() {
                return new DocumentSchemaInfo(requireNonNull(mDocId), mVersion, mPathSchema);
            }
        }
    }
}
