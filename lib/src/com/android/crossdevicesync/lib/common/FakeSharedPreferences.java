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

package com.android.crossdevicesync.lib.common;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** A fake implementation of {@link SharedPreferences} for testing. */
public class FakeSharedPreferences implements SharedPreferences {
    private final Map<String, Object> mValues = new HashMap<>();
    private final List<OnSharedPreferenceChangeListener> mListeners = new CopyOnWriteArrayList<>();

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>(mValues);
    }

    @Override
    public String getString(String key, String defValue) {
        Object v = mValues.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = mValues.get(key);
        return v instanceof Set ? (Set<String>) v : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object v = mValues.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object v = mValues.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object v = mValues.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object v = mValues.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public boolean contains(String key) {
        return mValues.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.remove(listener);
    }

    private class FakeEditor implements Editor {
        private final Map<String, Object> mPendingValues = new HashMap<>(mValues);

        @Override
        public Editor putString(String key, String value) {
            mPendingValues.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            mPendingValues.put(key, values == null ? null : new HashSet<>(values));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            mPendingValues.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            mPendingValues.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            mPendingValues.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            mPendingValues.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            mPendingValues.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            mPendingValues.clear();
            return this;
        }

        @Override
        public boolean commit() {
            mValues.clear();
            mValues.putAll(mPendingValues);
            for (OnSharedPreferenceChangeListener listener : mListeners) {
                for (String key : mPendingValues.keySet()) {
                    listener.onSharedPreferenceChanged(FakeSharedPreferences.this, key);
                }
            }
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
