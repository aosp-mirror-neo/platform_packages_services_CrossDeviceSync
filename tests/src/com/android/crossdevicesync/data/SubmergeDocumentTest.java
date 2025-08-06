/*
 * Copyright 2025 The Android Open Source Project
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
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
        mDocument = new SubmergeDocument<>(mDataStoreName, DOC_ID, mTransaction);
        mDeviceNodeId = mDeviceNodeIdProvider.getLocalDeviceNodeId();
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
