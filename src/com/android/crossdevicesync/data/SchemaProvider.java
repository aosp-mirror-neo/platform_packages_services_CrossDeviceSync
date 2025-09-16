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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.crossdevicesync.data.SharedDataStore.Document;
import com.android.crossdevicesync.data.SharedDataStore.MutableDocument;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /** Get document schema from doc id. */
    @Nullable
    default DocumentSchemaInfo findSchema(String docId) {
        for (DocumentSchemaInfo schema : getAllDocumentSchema()) {
            if (docId.equals(schema.getDocId())) {
                return schema;
            }
        }
        return null;
    }

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
     * @throws SchemaValidationException if validation fails.
     */
    default void validateDocument(Document<T> doc) throws SchemaValidationException {
        DocumentSchemaInfo schema = findSchema(doc.getDocId());
        if (schema == null) {
            throw new SchemaValidationException(
                    "validateSchema: schema not found for doc " + doc.getDocId() + "!");
        }
        if (doc.getSchemaVersion() != schema.getVersion()) {
            throw new SchemaValidationException(
                    "validateSchema: schema version mismatch! Expects "
                            + schema.getVersion()
                            + " but got "
                            + doc.getSchemaVersion()
                            + ".");
        }
        for (PathSchemaInfo pathSchema : schema.getPathSchema()) {
            String path = pathSchema.path();
            int type = pathSchema.type();
            SharedDataStore.Record<T> r = doc.getRecord(path);
            switch (r) {
                case null ->
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " not found!");
                case SharedDataStore.SetRecord<T> setRecord -> {
                    if (type != SchemaProvider.TYPE_SET) {
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " is not a set!");
                    }
                }
                case SharedDataStore.UnmergedRecord<T> unmergedRecord -> {
                    if (type != SchemaProvider.TYPE_UNMERGED) {
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " is not an unmerged record!");
                    }
                }
                default -> {
                    if (type != SchemaProvider.TYPE_REGISTER) {
                        throw new SchemaValidationException(
                                "validateSchema: path "
                                        + doc.getDebugStringForPath(path)
                                        + " is not a register record!");
                    }
                }
            }
        }
    }

    private static void validateRecordType(@RecordType int type) {
        switch (type) {
            case TYPE_REGISTER, TYPE_SET, TYPE_UNMERGED -> {}
            default -> throw new RuntimeException("Unknown record type: " + type);
        }
    }

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
        private final List<PathSchemaInfo> mPathSchema;

        private DocumentSchemaInfo(String docId, int version, List<PathSchemaInfo> pathSchema) {
            mDocId = docId;
            mVersion = version;
            mPathSchema = pathSchema;
        }

        public String getDocId() {
            return mDocId;
        }

        public int getVersion() {
            return mVersion;
        }

        public List<PathSchemaInfo> getPathSchema() {
            return mPathSchema;
        }

        public static Builder builder() {
            return new Builder();
        }

        /** Builder for {@link DocumentSchemaInfo}. */
        public static final class Builder {
            private String mDocId;
            private int mVersion;
            private final List<PathSchemaInfo> mPathSchema = new ArrayList<>();

            private Builder() {}

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
            public Builder putPathSchema(PathSchemaInfo schema) {
                mPathSchema.add(schema);
                return this;
            }

            /** Builds the {@link DocumentSchemaInfo} object. */
            public DocumentSchemaInfo build() {
                // Validate schema.
                Set<String> pathSet = new HashSet<>(mPathSchema.size());
                for (PathSchemaInfo schemaInfo : mPathSchema) {
                    if (!pathSet.add(schemaInfo.path())) {
                        throw new RuntimeException(
                                "Can't create schema: duplicate path - " + schemaInfo.path());
                    }
                    if (schemaInfo.version() > mVersion) {
                        throw new RuntimeException(
                                "Can't create schema: schema version "
                                        + schemaInfo.version()
                                        + " of path "
                                        + schemaInfo.path()
                                        + " is greater than document schema version "
                                        + mVersion);
                    }
                    validateRecordType(schemaInfo.type());
                }
                return new DocumentSchemaInfo(requireNonNull(mDocId), mVersion, mPathSchema);
            }
        }
    }

    /**
     * Data record for a path schema.
     *
     * @param path the data path.
     * @param type the type of the record. Must be one of {@code RecordType}.
     * @param version the schema version of the document when this path is firstly introduced. Must
     *     not change once introduced.
     */
    record PathSchemaInfo(String path, @RecordType int type, int version) {}
}
