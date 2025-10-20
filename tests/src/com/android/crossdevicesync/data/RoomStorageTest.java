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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.submerge.StorageInterface.StorageException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class RoomStorageTest extends SharedDataStoreComponentTestBase {

    @Before
    public void setUp() {
        super.setUp();
        mRoomStorage.init();
    }

    @After
    public void tearDown() {
        mRoomStorage.deleteDatabase();
    }

    @Test
    public void persistDocument_docInserted() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};

        mRoomStorage.persistDocument(id, doc);

        assertThat(Arrays.equals(doc, mRoomStorage.getDocument(id))).isTrue();
    }

    @Test
    public void persistDocument_docContentUpdated() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistDocument(id, doc);

        doc = new byte[] {5, 6, 7, 8};
        mRoomStorage.persistDocument(id, doc);

        assertThat(Arrays.equals(doc, mRoomStorage.getDocument(id))).isTrue();
    }

    @Test
    public void persistDocument_dataPersistedAcrossClose() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistDocument(id, doc);

        mRoomStorage.close();
        mRoomStorage.init();

        assertThat(Arrays.equals(doc, mRoomStorage.getDocument(id))).isTrue();
    }

    @Test
    public void persistMetadata_docInserted() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};

        mRoomStorage.persistMetadata(id, metadata);

        assertThat(Arrays.equals(metadata, mRoomStorage.getMetadata(id))).isTrue();
    }

    @Test
    public void persistMetadata_docMetadataUpdated() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistMetadata(id, metadata);

        metadata = new byte[] {5, 6, 7, 8};
        mRoomStorage.persistMetadata(id, metadata);

        assertThat(Arrays.equals(metadata, mRoomStorage.getMetadata(id))).isTrue();
    }

    @Test
    public void persistMetadata_dataPersistedAcrossClose() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistMetadata(id, metadata);

        mRoomStorage.close();
        mRoomStorage.init();

        assertThat(Arrays.equals(metadata, mRoomStorage.getMetadata(id))).isTrue();
    }

    @Test
    public void persistSchema_docInserted() throws StorageException {
        String id = "id";
        byte[] schema = new byte[] {1, 2, 3, 4};

        mRoomStorage.persistDocumentSchema(id, schema);

        assertThat(Arrays.equals(schema, mRoomStorage.getDocumentSchema(id))).isTrue();
    }

    @Test
    public void persistSchema_schemaUpdated() throws StorageException {
        String id = "id";
        byte[] schema = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistDocumentSchema(id, schema);

        schema = new byte[] {5, 6, 7, 8};
        mRoomStorage.persistDocumentSchema(id, schema);

        assertThat(Arrays.equals(schema, mRoomStorage.getDocumentSchema(id))).isTrue();
    }

    @Test
    public void persistSchema_dataPersistedAcrossClose() throws StorageException {
        String id = "id";
        byte[] schema = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistDocumentSchema(id, schema);

        mRoomStorage.close();
        mRoomStorage.init();

        assertThat(Arrays.equals(schema, mRoomStorage.getDocumentSchema(id))).isTrue();
    }

    @Test
    public void persistDocument_metadataAndSchemaUnchanged() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        byte[] metadata = new byte[] {5, 6, 7, 8};
        byte[] schema = new byte[] {9, 10, 11, 12};
        mRoomStorage.persistMetadata(id, metadata);
        mRoomStorage.persistDocumentSchema(id, schema);

        mRoomStorage.persistDocument(id, doc);

        assertThat(Arrays.equals(metadata, mRoomStorage.getMetadata(id))).isTrue();
        assertThat(Arrays.equals(schema, mRoomStorage.getDocumentSchema(id))).isTrue();
    }

    @Test
    public void persistMetadata_docContentAndSchemaUnchanged() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        byte[] metadata = new byte[] {5, 6, 7, 8};
        byte[] schema = new byte[] {9, 10, 11, 12};
        mRoomStorage.persistDocument(id, doc);
        mRoomStorage.persistDocumentSchema(id, schema);

        mRoomStorage.persistMetadata(id, metadata);

        assertThat(Arrays.equals(doc, mRoomStorage.getDocument(id))).isTrue();
        assertThat(Arrays.equals(schema, mRoomStorage.getDocumentSchema(id))).isTrue();
    }

    @Test
    public void persistSchema_docContentAndMetadataUnchanged() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        byte[] metadata = new byte[] {5, 6, 7, 8};
        byte[] schema = new byte[] {9, 10, 11, 12};
        mRoomStorage.persistDocument(id, doc);
        mRoomStorage.persistMetadata(id, metadata);

        mRoomStorage.persistDocumentSchema(id, schema);

        assertThat(Arrays.equals(doc, mRoomStorage.getDocument(id))).isTrue();
        assertThat(Arrays.equals(metadata, mRoomStorage.getMetadata(id))).isTrue();
    }

    @Test
    public void getDocument_multipleDocs_rightDocReturned() throws StorageException {
        String id1 = "id1";
        byte[] doc1 = new byte[] {1, 2, 3, 4};
        String id2 = "id2";
        byte[] doc2 = new byte[] {5, 6, 7, 8};
        mRoomStorage.persistDocument(id1, doc1);
        mRoomStorage.persistDocument(id2, doc2);

        assertThat(Arrays.equals(doc1, mRoomStorage.getDocument(id1))).isTrue();
        assertThat(Arrays.equals(doc2, mRoomStorage.getDocument(id2))).isTrue();
    }

    @Test
    public void getMetadata_multipleDocs_rightMetadataReturned() throws StorageException {
        String id1 = "id1";
        byte[] metadata1 = new byte[] {1, 2, 3, 4};
        String id2 = "id2";
        byte[] metadata2 = new byte[] {5, 6, 7, 8};
        mRoomStorage.persistMetadata(id1, metadata1);
        mRoomStorage.persistMetadata(id2, metadata2);

        assertThat(Arrays.equals(metadata1, mRoomStorage.getMetadata(id1))).isTrue();
        assertThat(Arrays.equals(metadata2, mRoomStorage.getMetadata(id2))).isTrue();
    }

    @Test
    public void getSchema_multipleDocs_rightMetadataReturned() throws StorageException {
        String id1 = "id1";
        byte[] schema1 = new byte[] {1, 2, 3, 4};
        String id2 = "id2";
        byte[] schema2 = new byte[] {5, 6, 7, 8};
        mRoomStorage.persistDocumentSchema(id1, schema1);
        mRoomStorage.persistDocumentSchema(id2, schema2);

        assertThat(Arrays.equals(schema1, mRoomStorage.getDocumentSchema(id1))).isTrue();
        assertThat(Arrays.equals(schema2, mRoomStorage.getDocumentSchema(id2))).isTrue();
    }

    @Test
    public void deleteDatabase_persistedDataDeleted() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};
        byte[] doc = new byte[] {1, 2, 3, 4};
        byte[] schema = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistMetadata(id, metadata);
        mRoomStorage.persistDocument(id, doc);
        mRoomStorage.persistDocumentSchema(id, schema);

        mRoomStorage.deleteDatabase();
        // Re-initialize the db.
        mRoomStorage.init();

        assertThat(mRoomStorage.getDocument("id")).isNull();
        assertThat(mRoomStorage.getMetadata("id")).isNull();
        assertThat(mRoomStorage.getDocumentSchema("id")).isNull();
    }

    @Test
    public void close_notAccessibleAfterwards() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistMetadata(id, metadata);

        mRoomStorage.close();

        assertThrows(StorageException.class, () -> mRoomStorage.getDocument("id"));
        assertThrows(StorageException.class, () -> mRoomStorage.getMetadata("id"));
        assertThrows(StorageException.class, () -> mRoomStorage.getDocumentSchema("id"));
        assertThrows(StorageException.class, () -> mRoomStorage.persistDocument("id", new byte[0]));
        assertThrows(StorageException.class, () -> mRoomStorage.persistMetadata("id", new byte[0]));
        assertThrows(
                StorageException.class,
                () -> mRoomStorage.persistDocumentSchema("id", new byte[0]));
    }

    @Test
    public void transact_metadataRolledBackOnFailure() throws Exception {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};

        try {
            mRoomStorage.transact(
                    storage -> {
                        mRoomStorage.persistMetadata(id, metadata);
                        throw new RuntimeException();
                    });
        } catch (StorageException e) {
            // Do nothing.
        }

        assertThat(mRoomStorage.getMetadata(id)).isNull();
    }

    @Test
    public void transact_contentRolledBackOnFailure() throws Exception {
        String id = "id";
        byte[] content = new byte[] {1, 2, 3, 4};

        try {
            mRoomStorage.transact(
                    storage -> {
                        mRoomStorage.persistDocument(id, content);
                        throw new RuntimeException();
                    });
        } catch (StorageException e) {
            // Do nothing.
        }

        assertThat(mRoomStorage.getDocument(id)).isNull();
    }

    @Test
    public void transact_schemaRolledBackOnFailure() throws Exception {
        String id = "id";
        byte[] schema = new byte[] {1, 2, 3, 4};

        try {
            mRoomStorage.transact(
                    storage -> {
                        mRoomStorage.persistDocumentSchema(id, schema);
                        throw new RuntimeException();
                    });
        } catch (StorageException e) {
            // Do nothing.
        }

        assertThat(mRoomStorage.getDocumentSchema(id)).isNull();
    }
}
