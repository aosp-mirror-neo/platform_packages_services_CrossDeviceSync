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
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.data.SharedDataStore.Record;
import com.android.crossdevicesync.data.SharedDataStore.SetRecord;
import com.android.crossdevicesync.data.SharedDataStore.UnmergedRecord;
import com.android.crossdevicesync.proto.DocMetadata;
import com.android.crossdevicesync.proto.DocMetadata.RecordMetadata;
import com.android.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.internal.util.FunctionalUtils.ThrowingSupplier;

import com.google.android.submerge.DocumentTransaction;
import com.google.android.submerge.SubmergeDataType;
import com.google.android.submerge.SubmergeMap;
import com.google.android.submerge.SubmergeRegister;
import com.google.android.submerge.SubmergeSet;
import com.google.android.submerge.SubmergeVectorData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SubmergeDocumentTest extends SharedDataStoreComponentTestBase {
    private static final String DOC_ID = "test_doc";
    private SubmergeDocument<String> mDocument;
    private DocumentTransaction<String> mTransaction;
    private String mDeviceNodeId;

    @SuppressWarnings("MustBeClosedChecker")
    @Before
    public void setUp() {
        super.setUp();
        mFakeStorage.init();
        mTransaction =
                (DocumentTransaction<String>) mSubmergeDataStore.newDocumentTransaction(DOC_ID);
        mDeviceNodeId = mDeviceNodeIdProvider.getLocalDeviceNodeId();
        mDocument =
                new SubmergeDocument<>(
                        mDataStoreName,
                        DOC_ID,
                        mTransaction,
                        mDeviceNodeId,
                        DocMetadata.DocumentMetadata.newBuilder().build());
    }

    @After
    public void tearDown() {
        mDocument.close();
    }

    @Test
    public void testRawGetOrInsert_createsAndGetsRegisterLeaf() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertRegisterNode("/foo/bar0");
        validateRawGetOrInsertRegisterNode("/foo/bar1");
        validateRawGetOrInsertRegisterNode("/foo/bar2");
        validateRawGetOrInsertRegisterNode("/bar1");
        validateRawGetOrInsertRegisterNode("/bar2");

        // Valid paths - get without insertion.
        validateRawGetOrInsertRegisterNode("/foo/bar0");
        validateRawGetOrInsertRegisterNode("/foo/bar1");
        validateRawGetOrInsertRegisterNode("/foo/bar2");
        validateRawGetOrInsertRegisterNode("/bar1");
        validateRawGetOrInsertRegisterNode("/bar2");

        // Invalid paths - path travels through leaf node.
        assertThrows(
                IllegalArgumentException.class,
                () -> validateRawGetOrInsertRegisterNode("foo/bar0/1"));
        // Invalid paths - path points to intermediate node.
        assertThrows(
                IllegalArgumentException.class, () -> validateRawGetOrInsertRegisterNode("/foo"));
        // Invalid path - root is not empty
        assertThrows(IllegalArgumentException.class, () -> validateRawGetOrInsertRegisterNode("/"));
    }

    @Test
    public void testRawGetOrInsert_createsAndGetsSetLeaf() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertSetNode("/foo/bar0");
        validateRawGetOrInsertSetNode("/foo/bar1");
        validateRawGetOrInsertSetNode("/foo/bar2");
        validateRawGetOrInsertSetNode("/bar1");
        validateRawGetOrInsertSetNode("/bar2");

        // Valid paths - get without insertion.
        validateRawGetOrInsertSetNode("/foo/bar0");
        validateRawGetOrInsertSetNode("/foo/bar1");
        validateRawGetOrInsertSetNode("/foo/bar2");
        validateRawGetOrInsertSetNode("/bar1");
        validateRawGetOrInsertSetNode("/bar2");

        // Invalid paths - path travels through leaf node.
        assertThrows(
                IllegalArgumentException.class, () -> validateRawGetOrInsertSetNode("foo/bar0/1"));
        // Invalid paths - path points to intermediate node.
        assertThrows(IllegalArgumentException.class, () -> validateRawGetOrInsertSetNode("/foo"));
        // Invalid path - root is not empty
        assertThrows(IllegalArgumentException.class, () -> validateRawGetOrInsertSetNode("/"));
    }

    @Test
    public void testRawGetOrInsert_createsAndGetsVectorLeaf() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertVectorNode("/foo/bar0");
        validateRawGetOrInsertVectorNode("/foo/bar1");
        validateRawGetOrInsertVectorNode("/foo/bar2");
        validateRawGetOrInsertVectorNode("/bar1");
        validateRawGetOrInsertVectorNode("/bar2");

        // Valid paths - get without insertion.
        validateRawGetOrInsertVectorNode("/foo/bar0");
        validateRawGetOrInsertVectorNode("/foo/bar1");
        validateRawGetOrInsertVectorNode("/foo/bar2");
        validateRawGetOrInsertVectorNode("/bar1");
        validateRawGetOrInsertVectorNode("/bar2");

        // Invalid paths - path travels through leaf node.
        assertThrows(
                IllegalArgumentException.class,
                () -> validateRawGetOrInsertVectorNode("foo/bar0/1"));
        // Invalid paths - path points to intermediate node.
        assertThrows(
                IllegalArgumentException.class, () -> validateRawGetOrInsertVectorNode("/foo"));
        // Invalid path - root is not empty
        assertThrows(IllegalArgumentException.class, () -> validateRawGetOrInsertVectorNode("/"));
    }

    @Test
    public void testRawGetOrInsert_createsAndGetsRegisterRoot() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertRegisterNode("/");

        // Valid paths - get without insertion.
        validateRawGetOrInsertRegisterNode("/");

        // Invalid paths - root is not empty.
        assertThrows(
                IllegalArgumentException.class, () -> validateRawGetOrInsertRegisterNode("/foo"));
    }

    @Test
    public void testRawGetOrInsert_createsAndGetsSetRoot() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertSetNode("/");

        // Valid paths - get without insertion.
        validateRawGetOrInsertSetNode("/");

        // Invalid paths - root is not empty.
        assertThrows(IllegalArgumentException.class, () -> validateRawGetOrInsertSetNode("/foo"));
    }

    @Test
    public void testRawGetOrInsert_createsAndGetsVectorRoot() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertVectorNode("/");

        // Valid paths - get without insertion.
        validateRawGetOrInsertVectorNode("/");

        // Invalid paths - root is not empty.
        assertThrows(
                IllegalArgumentException.class, () -> validateRawGetOrInsertVectorNode("/foo"));
    }

    @Test
    public void testRawGetOrInsert_createsAndGetDifferentNodeTypes_atLeaf() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertRegisterNode("/foo/register");
        validateRawGetOrInsertSetNode("/foo/set");
        validateRawGetOrInsertVectorNode("/foo/vector");

        // Valid paths - get without insertion.
        validateRawGetOrInsertSetNode("/foo/register");
        validateRawGetOrInsertVectorNode("/foo/set");
        validateRawGetOrInsertRegisterNode("/foo/vector");
    }

    @Test
    public void testRawGetOrInsert_createsAndGetDifferentNodeTypes_atRoot() throws Exception {
        // Valid paths - insertion.
        validateRawGetOrInsertRegisterNode("/");

        // Valid paths - get without insertion.
        validateRawGetOrInsertSetNode("/");
        validateRawGetOrInsertVectorNode("/");
        validateRawGetOrInsertRegisterNode("/");
    }

    @Test
    public void testRawGet_emptyDoc_returnsNull() throws Exception {
        // Empty document.
        validateRawGetReturnsNull("/");
        validateRawGetReturnsNull("/foo/bar");
    }

    @Test
    public void testRawGet_invalidPath_returnsNull() throws Exception {
        // Insert a node at /foo/bar
        validateRawGetOrInsertRegisterNode("/foo/bar");

        // Invalid path.
        validateRawGetReturnsNull("/bar");
        validateRawGetReturnsNull("/foo/bar/baz");
        validateRawGetReturnsNull("/foo/baz");
    }

    @Test
    public void testRawGet_validPath_returnRightType() throws Exception {
        validateRawGetOrInsertRegisterNode("/foo/register");
        validateRawGetOrInsertSetNode("/foo/set");
        validateRawGetOrInsertVectorNode("/foo/vector");

        // Valid path.
        validateRawGetReturnsType("/foo/register", SubmergeRegister.class);
        validateRawGetReturnsType("/foo/set", SubmergeSet.class);
        validateRawGetReturnsType("/foo/vector", SubmergeVectorData.class);
        validateRawGetReturnsType("/", SubmergeMap.class);
        validateRawGetReturnsType("/foo", SubmergeMap.class);
    }

    @Test
    public void testPutAndGet() throws Exception {
        // New record.
        validatePutAndGet("/foo/bar", "abc");
        // Update the value.
        validatePutAndGet("/foo/bar", "def");
        // Another new record.
        validatePutAndGet("/bar/foo", null);
        // Update the value.
        validatePutAndGet("/bar/foo", "");

        // Illegal path.
        assertThrows(
                IllegalArgumentException.class, () -> mDocument.putData("/foo/bar/bla", "abc"));
        assertThrows(IllegalArgumentException.class, () -> mDocument.putData("/", "abc"));

        // Illegal type.
        mDocument.putUnmergedData("/unmerged", "abc");
        assertThrows(IllegalArgumentException.class, () -> mDocument.putData("/unmerged", "abc"));
    }

    @Test
    public void testPutAndGetUnmerged() throws Exception {
        // New record.
        validatePutUnmergedAndGet("/foo/bar", "abc");
        // Update the value.
        validatePutUnmergedAndGet("/foo/bar", "def");
        // Another new record.
        validatePutUnmergedAndGet("/bar/foo", null);
        // Update the value.
        validatePutUnmergedAndGet("/bar/foo", "");

        // Illegal path.
        assertThrows(
                IllegalArgumentException.class,
                () -> mDocument.putUnmergedData("/foo/bar/bla", "abc"));
        assertThrows(IllegalArgumentException.class, () -> mDocument.putUnmergedData("/", "abc"));

        // Illegal type.
        mDocument.putData("/merged", "abc");
        assertThrows(
                IllegalArgumentException.class, () -> mDocument.putUnmergedData("/merged", "abc"));
    }

    @Test
    public void testSetApis() throws Exception {
        // Add a new empty set.
        validateAddEmptySetAndGet("/foo/bar");

        // Add a value to the set.
        validateAddToSetAndGet("/foo/bar", "abc");

        // Add another value.
        validateAddToSetAndGet("/foo/bar", "def");

        // Remove a value.
        validateRemoveFromSetAndGet("/foo/bar", "abc");

        // Remove a non-existing value.
        validateRemoveFromSetAndGet("/foo/bar", "abc");

        // Add to another set.
        validateAddToSetAndGet("/bar/foo", "hij");

        // Add a null.
        validateAddToSetAndGet("/foo/bar", null);

        // Remove a null.
        validateRemoveFromSetAndGet("/foo/bar", null);

        // Illegal path.
        assertThrows(
                IllegalArgumentException.class,
                () -> mDocument.addDataToSet("/foo/bar/bla", "abc"));
        assertThrows(IllegalArgumentException.class, () -> mDocument.addDataToSet("/", "abc"));

        // Illegal type.
        mDocument.putData("/merged", "abc");
        assertThrows(IllegalArgumentException.class, () -> mDocument.addEmptySet("/merged"));
        assertThrows(
                IllegalArgumentException.class, () -> mDocument.addDataToSet("/merged", "abc"));
        assertThrows(
                IllegalArgumentException.class,
                () -> mDocument.removeDataFromSet("/merged", "abc"));

        // Illegal set operation - add empty set to an existing set.
        assertThrows(IllegalArgumentException.class, () -> mDocument.addEmptySet("/foo/bar"));

        // Illegal set operation - remove from a non-existing set.
        assertThrows(
                IllegalArgumentException.class, () -> mDocument.removeDataFromSet("/foo", "abc"));
    }

    @Test
    public void testContains() throws Exception {
        mDocument.putData("/foo/bar", "abc");

        assertThat(mDocument.containsPath("/foo/bar")).isTrue();
        assertThat(mDocument.containsPath("/foo/baz")).isFalse();
        assertThat(mDocument.containsPath("/bar/foo")).isFalse();
        assertThat(mDocument.containsPath("/")).isFalse();
        assertThat(mDocument.containsPath("/foo")).isFalse();
        assertThat(mDocument.containsPath("/foo/bar/baz")).isFalse();
    }

    @Test
    public void testRemovePath() throws Exception {
        // Build a balanced binary tree
        mDocument.putData("/root/1/1/1", "abc");
        mDocument.putData("/root/1/1/2", "abc");
        mDocument.putData("/root/1/2/3", "abc");
        mDocument.putData("/root/1/2/4", "abc");
        mDocument.putData("/root/2/3/5", "abc");
        mDocument.putData("/root/2/3/6", "abc");
        mDocument.putData("/root/2/4/7", "abc");
        mDocument.putData("/root/2/4/8", "abc");

        // Remove a leaf and verify it can be reused by another type.
        mDocument.removePath("/root/1/1/1");
        assertThat(mDocument.containsPath("/root/1/1/1")).isFalse();
        validatePutUnmergedAndGet("/root/1/1/1", "abc");

        // Remove another leaf and verify it can be reused as intermediate node.
        mDocument.removePath("/root/1/1/2");
        assertThat(mDocument.containsPath("/root/1/1/2")).isFalse();
        validateAddEmptySetAndGet("/root/1/1/2/1");

        // Remove intermediate node and verify all children are gone and closed.
        Record<String> r1 = mDocument.getRecord("/root/1/2/3");
        Record<String> r2 = mDocument.getRecord("/root/1/2/4");
        mDocument.removePath("/root/1/2");
        assertThat(mDocument.containsPath("/root/1/2/3")).isFalse();
        assertThat(mDocument.containsPath("/root/1/2/4")).isFalse();
        assertThrows(IllegalStateException.class, () -> r1.get());
        assertThrows(IllegalStateException.class, () -> r2.getMetadata());

        // Remove root node and verify all children are gone.
        mDocument.removePath("/");
        assertThat(mDocument.containsPath("/root/2/3/5")).isFalse();
        assertThat(mDocument.containsPath("/root/2/3/6")).isFalse();
        assertThat(mDocument.containsPath("/root/2/4/7")).isFalse();
        assertThat(mDocument.containsPath("/root/2/4/8")).isFalse();
    }

    @Test
    public void testClose() throws Exception {
        // Add some data
        mDocument.putData("/register", "data");
        mDocument.putUnmergedData("/vector", "data");
        mDocument.addDataToSet("/set", "data");

        // Get record references
        Record<String> registerRecord = mDocument.getRecord("/register");
        UnmergedRecord<String> vectorRecord =
                (UnmergedRecord<String>) mDocument.getRecord("/vector");
        SetRecord<String> setRecord = (SetRecord<String>) mDocument.getRecord("/set");
        assertThat(registerRecord).isNotNull();
        assertThat(vectorRecord).isNotNull();
        assertThat(setRecord).isNotNull();

        // Close the document
        mDocument.close();

        // Verify records are invalidated
        assertThrows(IllegalStateException.class, registerRecord::get);
        assertThrows(IllegalStateException.class, registerRecord::getMetadata);
        assertThrows(IllegalStateException.class, vectorRecord::get);
        assertThrows(IllegalStateException.class, vectorRecord::getMetadata);
        assertThrows(IllegalStateException.class, setRecord::entries);
        assertThrows(IllegalStateException.class, setRecord::getMetadata);

        // Verify document APIs throw
        assertThrows(IllegalStateException.class, () -> mDocument.putData("/new", "data"));
        assertThrows(IllegalStateException.class, () -> mDocument.getRecord("/register"));
        assertThrows(IllegalStateException.class, () -> mDocument.containsPath("/register"));
        assertThrows(IllegalStateException.class, () -> mDocument.removePath("/register"));
        assertThrows(
                IllegalStateException.class, () -> mDocument.putUnmergedData("/unmerged", "data"));
        assertThrows(IllegalStateException.class, () -> mDocument.addEmptySet("/set"));
        assertThrows(IllegalStateException.class, () -> mDocument.addDataToSet("/set", "data"));
        assertThrows(
                IllegalStateException.class, () -> mDocument.removeDataFromSet("/set", "data"));
    }

    @Test
    public void testCommit() throws Exception {
        // Add some data
        mDocument.putData("/register", "data");
        mDocument.putUnmergedData("/vector", "data");
        mDocument.addDataToSet("/set", "data");

        // Get record references
        Record<String> registerRecord = mDocument.getRecord("/register");
        UnmergedRecord<String> vectorRecord =
                (UnmergedRecord<String>) mDocument.getRecord("/vector");
        SetRecord<String> setRecord = (SetRecord<String>) mDocument.getRecord("/set");
        assertThat(registerRecord).isNotNull();
        assertThat(vectorRecord).isNotNull();
        assertThat(setRecord).isNotNull();

        // Commit the transaction
        mDocument.commitTransaction();

        // Verify records are invalidated
        assertThrows(IllegalStateException.class, registerRecord::get);
        assertThrows(IllegalStateException.class, registerRecord::getMetadata);
        assertThrows(IllegalStateException.class, vectorRecord::get);
        assertThrows(IllegalStateException.class, vectorRecord::getMetadata);
        assertThrows(IllegalStateException.class, setRecord::entries);
        assertThrows(IllegalStateException.class, setRecord::getMetadata);

        // Verify document APIs throw
        assertThrows(IllegalStateException.class, () -> mDocument.putData("/new", "data"));
        assertThrows(IllegalStateException.class, () -> mDocument.getRecord("/register"));
        assertThrows(IllegalStateException.class, () -> mDocument.containsPath("/register"));
        assertThrows(IllegalStateException.class, () -> mDocument.removePath("/register"));
        assertThrows(
                IllegalStateException.class, () -> mDocument.putUnmergedData("/unmerged", "data"));
        assertThrows(IllegalStateException.class, () -> mDocument.addEmptySet("/set"));
        assertThrows(IllegalStateException.class, () -> mDocument.addDataToSet("/set", "data"));
        assertThrows(
                IllegalStateException.class, () -> mDocument.removeDataFromSet("/set", "data"));
    }

    @Test
    public void testGetMetaData_notModifiedByLocalDevice() throws Exception {
        // Create a new node via internal helper method, this will directly modify the submerge
        // transaction without updating the metadata.
        mDocument.findOrCreateLeaf("/node", mTransaction::newRegister);

        // Read the record. Since the record is not created by API, it's assumed to be created by
        // remote device.
        Record<String> r = mDocument.getRecord("/node");

        // Verify the metadata.
        assertThat(r.getMetadata().isLastModifiedByLocalDevice()).isFalse();
    }

    @Test
    public void testGetMetaData_metadataUpdatedCorrectly() throws Exception {
        mDocument.putData("/register", null);
        mDocument.putUnmergedData("/vector", null);
        mDocument.addDataToSet("/set", null);

        assertThat(mDocument.getMetaData().getRecordMetadataCount()).isEqualTo(3);
        for (RecordMetadata recordMetadata : mDocument.getMetaData().getRecordMetadataList()) {
            assertThat(recordMetadata.getLastModifiedByLocalDevice()).isTrue();
        }

        mDocument.removePath("/set");

        assertThat(mDocument.getMetaData().getRecordMetadataCount()).isEqualTo(2);

        mDocument.removePath("/");

        // Assert that metadata is cleared.
        assertThat(mDocument.getMetaData().getRecordMetadataCount()).isEqualTo(0);
    }

    @Test
    public void testPathNormalization() throws Exception {
        mDocument.putData(" foo //bar/", "abc");
        mDocument.putUnmergedData(" foo / baz / bla ", "abc");
        mDocument.addEmptySet(" set");

        Record<String> r1 = mDocument.getRecord("/foo/bar");
        Record<String> r2 = mDocument.getRecord("/foo/baz/bla");
        Record<String> r3 = mDocument.getRecord("/set");

        assertThat(r1).isNotNull();
        assertThat(r1.get()).isEqualTo("abc");
        assertThat(r2).isNotNull();
        assertThat(r2.get()).isEqualTo("abc");
        assertThat(r3).isNotNull();
        assertThat(((SetRecord<String>) r3).entries().isEmpty()).isTrue();

        // This should remove the root
        mDocument.removePath("");

        assertThat(mDocument.containsPath("/foo/bar")).isFalse();
        assertThat(mDocument.containsPath("/foo/baz/bla")).isFalse();
        assertThat(mDocument.containsPath("/set")).isFalse();

        // Adds a record at root.
        mDocument.putData("/////  //  //  ", "abc");

        Record<String> root = mDocument.getRecord("/");

        assertThat(root).isNotNull();
        assertThat(root.get()).isEqualTo("abc");
    }

    private void validateRawGetOrInsertRegisterNode(String path) throws Exception {
        validateRawGetOrInsert(path, mTransaction::newRegister, new GenericValidator());
    }

    private void validateRawGetOrInsertSetNode(String path) throws Exception {
        validateRawGetOrInsert(path, mTransaction::newSet, new GenericValidator());
    }

    private void validateRawGetOrInsertVectorNode(String path) throws Exception {
        validateRawGetOrInsert(path, mTransaction::newVectorData, new GenericValidator());
    }

    private void validateRawGetOrInsert(
            String path,
            ThrowingSupplier<SubmergeDataType<String>> nodeCreator,
            TestValidator validator)
            throws Exception {
        try (SubmergeDataType<String> inserted = mDocument.findOrCreateLeaf(path, nodeCreator);
                SubmergeDataType<String> queried = mDocument.findSubmergeNode(path)) {
            assertThat(inserted).isNotNull();
            assertThat(queried).isNotNull();
            validator.validate(inserted, queried);
        }
    }

    private void validateRawGetReturnsType(String path, Class<?> type) throws Exception {
        validateRawGet(path, queried -> assertThat(queried).isInstanceOf(type));
    }

    private void validateRawGetReturnsNull(String path) throws Exception {
        validateRawGet(path, queried -> assertThat(queried).isNull());
    }

    private void validateRawGet(String path, ThrowingConsumer<SubmergeDataType<String>> validator)
            throws Exception {
        try (SubmergeDataType<String> queried = mDocument.findSubmergeNode(path)) {
            validator.acceptOrThrow(queried);
        }
    }

    private void validatePutAndGet(String path, String data) throws Exception {
        mDocument.putData(path, data);

        Record<String> record = mDocument.getRecord(path);

        assertThat(record).isNotNull();
        assertThat(record.get()).isEqualTo(data);
        assertThat(record.getMetadata().isLastModifiedByLocalDevice()).isTrue();
        assertThat(mDocument.containsPath(path)).isTrue();
    }

    private void validatePutUnmergedAndGet(String path, String data) throws Exception {
        mDocument.putUnmergedData(path, data);

        UnmergedRecord<String> record = (UnmergedRecord<String>) mDocument.getRecord(path);

        assertThat(record).isNotNull();
        assertThat(record.get()).isEqualTo(data);
        assertThat(record.entries().get(mDeviceNodeId)).isEqualTo(data);
        assertThat(record.getMetadata().isLastModifiedByLocalDevice()).isTrue();
        assertThat(mDocument.containsPath(path)).isTrue();
    }

    private void validateAddToSetAndGet(String path, String data) throws Exception {
        mDocument.addDataToSet(path, data);

        SetRecord<String> record = (SetRecord<String>) mDocument.getRecord(path);

        assertThat(record).isNotNull();
        assertThat(record.entries().contains(data)).isTrue();
        assertThat(record.getMetadata().isLastModifiedByLocalDevice()).isTrue();
        assertThat(mDocument.containsPath(path)).isTrue();
    }

    private void validateRemoveFromSetAndGet(String path, String data) throws Exception {
        mDocument.removeDataFromSet(path, data);

        SetRecord<String> record = (SetRecord<String>) mDocument.getRecord(path);

        assertThat(record).isNotNull();
        assertThat(record.entries().contains(data)).isFalse();
        assertThat(record.getMetadata().isLastModifiedByLocalDevice()).isTrue();
        assertThat(mDocument.containsPath(path)).isTrue();
    }

    private void validateAddEmptySetAndGet(String path) throws Exception {
        mDocument.addEmptySet(path);

        SetRecord<String> record = (SetRecord<String>) mDocument.getRecord(path);
        assertThat(record).isNotNull();
        assertThat(record.entries().isEmpty()).isTrue();
        assertThat(record.getMetadata().isLastModifiedByLocalDevice()).isTrue();
        assertThat(mDocument.containsPath(path)).isTrue();
    }

    /** Helper interface for testing assertion. */
    private interface TestValidator {
        void validate(SubmergeDataType<String> inserted, SubmergeDataType<String> queried)
                throws Exception;
    }

    private class RegisterValidator implements TestValidator {
        @Override
        public void validate(SubmergeDataType<String> i, SubmergeDataType<String> q)
                throws Exception {
            SubmergeRegister<String> inserted = (SubmergeRegister<String>) i;
            SubmergeRegister<String> queried = (SubmergeRegister<String>) q;
            inserted.set("abc");
            assertThat(queried.get()).isEqualTo("abc");
            queried.set("def");
            assertThat(inserted.get()).isEqualTo("def");
        }
    }

    private class SetValidator implements TestValidator {
        @Override
        public void validate(SubmergeDataType<String> i, SubmergeDataType<String> q)
                throws Exception {
            SubmergeSet<String> inserted = (SubmergeSet<String>) i;
            SubmergeSet<String> queried = (SubmergeSet<String>) q;
            assertThat(inserted.add("abc")).isTrue();
            assertThat(queried.contains("abc")).isTrue();
            assertThat(queried.remove("abc")).isTrue();
            assertThat(inserted.contains("abc")).isFalse();
        }
    }

    private class VectorValidator implements TestValidator {
        @Override
        public void validate(SubmergeDataType<String> i, SubmergeDataType<String> q)
                throws Exception {
            SubmergeVectorData<String> inserted = (SubmergeVectorData<String>) i;
            SubmergeVectorData<String> queried = (SubmergeVectorData<String>) q;
            assertThat(inserted.set("abc")).isTrue();
            assertThat(queried.entries().get(mDeviceNodeId)).isEqualTo("abc");
            assertThat(queried.set("def")).isTrue();
            assertThat(inserted.entries().get(mDeviceNodeId)).isEqualTo("def");
        }
    }

    private class GenericValidator implements TestValidator {
        private final TestValidator mRegisterValidator = new RegisterValidator();
        private final TestValidator mSetValidator = new SetValidator();
        private final TestValidator mVectorValidator = new VectorValidator();

        @Override
        public void validate(SubmergeDataType<String> inserted, SubmergeDataType<String> queried)
                throws Exception {
            assertThat(inserted.getClass()).isEqualTo(queried.getClass());
            if (inserted instanceof SubmergeRegister<String>) {
                mRegisterValidator.validate(inserted, queried);
            } else if (inserted instanceof SubmergeSet<String>) {
                mSetValidator.validate(inserted, queried);
            } else if (inserted instanceof SubmergeVectorData<String>) {
                mVectorValidator.validate(inserted, queried);
            } else {
                fail("Invalid node type");
            }
        }
    }
}
