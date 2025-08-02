/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
    public void onNewUpdate_docInserted() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};

        mRoomStorage.onNewUpdate(id, doc);

        assertThat(Arrays.equals(doc, mRoomStorage.readFromStorage(id))).isTrue();
    }

    @Test
    public void onNewUpdate_docContentUpdated() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        mRoomStorage.onNewUpdate(id, doc);

        doc = new byte[] {5, 6, 7, 8};
        mRoomStorage.onNewUpdate(id, doc);

        assertThat(Arrays.equals(doc, mRoomStorage.readFromStorage(id))).isTrue();
    }

    @Test
    public void onNewUpdate_dataPersistedAcrossClose() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        mRoomStorage.onNewUpdate(id, doc);

        mRoomStorage.close();
        mRoomStorage.init();

        assertThat(Arrays.equals(doc, mRoomStorage.readFromStorage(id))).isTrue();
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
    public void onNewUpdate_metadataUnchanged() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        byte[] metadata = new byte[] {5, 6, 7, 8};
        mRoomStorage.persistMetadata(id, metadata);

        mRoomStorage.onNewUpdate(id, doc);

        assertThat(Arrays.equals(metadata, mRoomStorage.getMetadata(id))).isTrue();
    }

    @Test
    public void persistMetadata_docContentUnchanged() throws StorageException {
        String id = "id";
        byte[] doc = new byte[] {1, 2, 3, 4};
        byte[] metadata = new byte[] {5, 6, 7, 8};
        mRoomStorage.onNewUpdate(id, doc);

        mRoomStorage.persistMetadata(id, metadata);

        assertThat(Arrays.equals(doc, mRoomStorage.readFromStorage(id))).isTrue();
    }

    @Test
    public void readFromStorage_multipleDocs_rightDocReturned() throws StorageException {
        String id1 = "id1";
        byte[] doc1 = new byte[] {1, 2, 3, 4};
        String id2 = "id2";
        byte[] doc2 = new byte[] {5, 6, 7, 8};
        mRoomStorage.onNewUpdate(id1, doc1);
        mRoomStorage.onNewUpdate(id2, doc2);

        assertThat(Arrays.equals(doc1, mRoomStorage.readFromStorage(id1))).isTrue();
        assertThat(Arrays.equals(doc2, mRoomStorage.readFromStorage(id2))).isTrue();
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
    public void deleteDatabase_persistedDataDeleted() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistMetadata(id, metadata);

        mRoomStorage.deleteDatabase();
        // Re-initialize the db.
        mRoomStorage.init();

        assertThat(mRoomStorage.readFromStorage("id")).isNull();
        assertThat(mRoomStorage.getMetadata("id")).isNull();
    }

    @Test
    public void close_notAccessibleAfterwards() throws StorageException {
        String id = "id";
        byte[] metadata = new byte[] {1, 2, 3, 4};
        mRoomStorage.persistMetadata(id, metadata);

        mRoomStorage.close();

        assertThrows(StorageException.class, () -> mRoomStorage.readFromStorage("id"));
        assertThrows(StorageException.class, () -> mRoomStorage.getMetadata("id"));
        assertThrows(StorageException.class, () -> mRoomStorage.onNewUpdate("id", new byte[0]));
        assertThrows(StorageException.class, () -> mRoomStorage.persistMetadata("id", new byte[0]));
    }
}
