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
package com.android.crossdevicesync.data.storage;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.android.internal.util.FunctionalUtils;

import java.util.List;

/** Room Dao for accessing {@link Document}. */
@Dao
abstract class DocumentDao {
    @Query("SELECT * from Document where docId=:docId LIMIT 1")
    abstract Document get(String docId);

    @Insert
    abstract void insert(Document document);

    @Update
    abstract void update(Document document);

    @Query(("SELECT docId from Document"))
    abstract List<String> getAllDocIds();

    @Query("DELETE FROM Document WHERE docId IN (:docIds)")
    abstract void deleteByDocIds(List<String> docIds);

    @Transaction
    void createOrUpdateContent(String docId, @Nullable byte[] content) {
        Document doc = get(docId);
        if (doc == null) {
            doc = new Document();
            doc.docId = docId;
            doc.content = content;
            insert(doc);
        } else {
            doc.content = content;
            update(doc);
        }
    }

    @Transaction
    void createOrUpdateMetadata(String docId, @Nullable byte[] metadata) {
        Document doc = get(docId);
        if (doc == null) {
            doc = new Document();
            doc.docId = docId;
            doc.metadata = metadata;
            insert(doc);
        } else {
            doc.metadata = metadata;
            update(doc);
        }
    }

    @Transaction
    void createOrUpdateSchema(String docId, @Nullable byte[] schema) {
        Document doc = get(docId);
        if (doc == null) {
            doc = new Document();
            doc.docId = docId;
            doc.schema = schema;
            insert(doc);
        } else {
            doc.schema = schema;
            update(doc);
        }
    }

    @Transaction
    void transact(FunctionalUtils.ThrowingRunnable runnable) {
        runnable.run();
    }
}
