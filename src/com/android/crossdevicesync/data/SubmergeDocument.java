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

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.crossdevicesync.common.SmartAutoClosable;
import com.android.internal.util.FunctionalUtils.ThrowingSupplier;

import com.google.android.submerge.DocumentTransaction;
import com.google.android.submerge.SubmergeDataType;
import com.google.android.submerge.SubmergeMap;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * An implementation of the {@link SharedDataStore.Document} using submerge {@link
 * DocumentTransaction}.
 *
 * @param <T> the data type that this document manages.
 */
public class SubmergeDocument<T> implements SharedDataStore.Document<T>, AutoCloseable {
    private static final boolean DEBUG = true;
    private static final String TAG = "SubmergeDocument";

    private final String mDataStoreName;
    private final String mDocId;
    private final DocumentTransaction<T> mTransaction;
    private volatile boolean mClosed;

    SubmergeDocument(String dataStoreName, String docId, DocumentTransaction<T> transaction) {
        mDataStoreName = dataStoreName;
        mDocId = docId;
        mTransaction = transaction;
    }

    @Nullable
    @Override
    public SharedDataStore.Record<T> getRecord(String path) {
        return null;
    }

    @Override
    public boolean containsPath(String path) {
        return false;
    }

    @Override
    public void putData(String path, @Nullable T data) throws IllegalArgumentException {}

    @Override
    public void putUnmergedData(String path, @Nullable T data) throws IllegalArgumentException {}

    @Override
    public void addEmptySet(String path) throws IllegalArgumentException {}

    @Override
    public void addDataToSet(String path, T data) throws IllegalArgumentException {}

    @Override
    public void removeDataFromSet(String path, T data) throws IllegalArgumentException {}

    @Override
    public void removePath(String path) throws IllegalArgumentException {}

    @Override
    public void close() {
        if (mClosed) {
            logWarning("close: already closed.");
            return;
        }
        mClosed = true;
        mTransaction.close();
    }

    private static ArrayDeque<String> normalizedKeys(String path) {
        ArrayDeque<String> normalizedKeys = new ArrayDeque<>();
        for (String key : path.split("/")) {
            String normalizedKey = key.trim();
            if (!normalizedKey.isEmpty()) {
                normalizedKeys.add(normalizedKey);
            }
        }
        return normalizedKeys;
    }

    private static String normalizedPath(String path) {
        return "/" + String.join("/", normalizedKeys(path));
    }

    /**
     * Find a node from the given path.
     *
     * @param path the path to look up.
     * @return a node in that path, or {@code null} if the path doesn't exist.
     */
    @VisibleForTesting
    @Nullable
    SubmergeDataType<T> findSubmergeNode(String path) {
        try (SmartAutoClosable<SubmergeDataType<T>> root =
                new SmartAutoClosable<>(mTransaction.getRoot())) {
            SubmergeDataType<T> result =
                    findSubmergeNodeRecursive(root.unwrap(), normalizedKeys(path));
            if (result == root.unwrap()) {
                // Do not close root if it's the target node.
                root.setkeepOpen(true);
            }
            return result;
        }
    }

    private SubmergeDataType<T> findSubmergeNodeRecursive(
            @Nullable SubmergeDataType<T> root, Queue<String> path) {
        if (root == null) {
            // Stop searching if root node is null.
            return null;
        }
        if (path.isEmpty()) {
            // Reached the end of the path and there is no more children to traverse. Return current
            // root as the result.
            return root;
        }
        if (!(root instanceof SubmergeMap)) {
            // Stop searching if we reached the leaf and can no longer traverse further.
            return null;
        }
        // Get the next child.
        SubmergeDataType<T> child = ((SubmergeMap<T>) root).getChild(path.poll());
        try (SmartAutoClosable<?> closeable = new SmartAutoClosable<>(child)) {
            // Recursively search from the child.
            SubmergeDataType<T> result = findSubmergeNodeRecursive(child, path);
            if (result == child) {
                // Do not close child if it's the target node.
                closeable.setkeepOpen(true);
            }
            return result;
        }
    }

    /**
     * Find or create a leaf node in the given path.
     *
     * @param path the path to find or create leaf node.
     * @param nodeCreator a function for creating the leaf node if necessary.
     * @return the leaf node in the given path.
     * @throws Exception if we are unable to find or create the leaf node.
     */
    @VisibleForTesting
    SubmergeDataType<T> findOrCreateLeaf(
            String path, ThrowingSupplier<SubmergeDataType<T>> nodeCreator) throws Exception {
        Queue<String> keys = normalizedKeys(path);
        SubmergeDataType<T> root =
                getOrCreateRoot(
                        () -> keys.isEmpty() ? nodeCreator.getOrThrow() : mTransaction.newMap());
        try (SmartAutoClosable<?> closable = new SmartAutoClosable<>(root)) {
            SubmergeDataType<T> result = findOrCreateLeafRecursive(root, keys, nodeCreator);
            if (result == root) {
                closable.setkeepOpen(true);
            }
            return result;
        }
    }

    /**
     * Find or create a leaf node in the given path. Recursively create {@link SubmergeMap} in the
     * path if necessary.
     *
     * @param root the root node to begin traversal from.
     * @param path the relative path to traverse from the current root node.
     * @param nodeCreator a function for creating the leaf node.
     * @return the leaf node in the given path.
     * @throws Exception if unable to get or create leaf node in the given path.
     */
    private SubmergeDataType<T> findOrCreateLeafRecursive(
            SubmergeDataType<T> root,
            Queue<String> path,
            ThrowingSupplier<SubmergeDataType<T>> nodeCreator)
            throws Exception {
        // Step 1: base case - already reached the target path. Return current root if feasible.
        if (path.isEmpty()) {
            if (root instanceof SubmergeMap) {
                throw new IllegalArgumentException(
                        getDebugIdentityString()
                                + " - unable to insert leaf node. Path points to intermediate "
                                + "node");
            }
            return root;
        }
        // Step 2: recursion case.
        if (!(root instanceof SubmergeMap)) {
            // Root is not map. Abort.
            throw new IllegalArgumentException(
                    getDebugIdentityString()
                            + " - unable to insert leaf node. Intermediate path is already"
                            + " occupied.");
        }
        // Root node is a map. Recursively traverse the tree and create new node as necessary.
        SubmergeDataType<T> child =
                getOrCreateChild(
                        (SubmergeMap<T>) root,
                        path.poll(),
                        () -> {
                            if (path.isEmpty()) {
                                logDebug("insertLeafRecursive: creating leaf node");
                                return nodeCreator.getOrThrow();
                            } else {
                                logDebug("insertLeafRecursive: creating intermediate node");
                                return mTransaction.newMap();
                            }
                        });
        try (SmartAutoClosable<?> closeable = new SmartAutoClosable<>(child)) {
            SubmergeDataType<T> result = findOrCreateLeafRecursive(child, path, nodeCreator);
            if (result == child) {
                // Keep the object open if it's the target.
                closeable.setkeepOpen(true);
            }
            return result;
        }
    }

    private SubmergeDataType<T> getOrCreateChild(
            SubmergeMap<T> root, String key, ThrowingSupplier<SubmergeDataType<T>> nodeCreator)
            throws Exception {
        SubmergeDataType<T> child = root.getChild(key);
        if (child != null) {
            return child;
        }
        try (SmartAutoClosable<SubmergeDataType<T>> node =
                new SmartAutoClosable<>(nodeCreator.getOrThrow())) {
            logDebug(
                    "getOrCreateChild: inserting node "
                            + node.unwrap()
                            + " into intermediate node "
                            + root
                            + " under the key \""
                            + key
                            + "\"");
            root.setChild(key, requireNonNull(node.unwrap()));
            node.setkeepOpen(true);
            return node.unwrap();
        }
    }

    private SubmergeDataType<T> getOrCreateRoot(ThrowingSupplier<SubmergeDataType<T>> nodeCreator)
            throws Exception {
        SubmergeDataType<T> root = mTransaction.getRoot();
        if (root != null) {
            return root;
        }
        try (SmartAutoClosable<SubmergeDataType<T>> node =
                new SmartAutoClosable<>(nodeCreator.getOrThrow())) {
            logDebug("getOrCreateRoot: inserting root node " + node);
            mTransaction.setRoot(requireNonNull(node.unwrap()));
            node.setkeepOpen(true);
            return node.unwrap();
        }
    }

    private String getDebugIdentityString() {
        return mDataStoreName + "::" + mDocId;
    }

    private String getDebugStringForPath(String path) {
        return getDebugIdentityString() + ":" + path;
    }

    private void logDebug(String message) {
        if (DEBUG) {
            Log.d(TAG, getDebugIdentityString() + " - " + message);
        }
    }

    private void logInfo(String message) {
        Log.i(TAG, getDebugIdentityString() + " - " + message);
    }

    private void logWarning(String message) {
        Log.w(TAG, getDebugIdentityString() + " - " + message);
    }
}
