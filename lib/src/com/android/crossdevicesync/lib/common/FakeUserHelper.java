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

import android.content.Context;
import android.content.pm.UserInfo;

import com.android.crossdevicesync.user.UserHelper;

import java.util.List;
import java.util.concurrent.Executor;

/** Fake implementation of {@link UserHelper}. */
public class FakeUserHelper implements UserHelper {
    private final Context mContext;

    public FakeUserHelper(Context context) {
        mContext = context;
    }

    @Override
    public List<UserInfo> getAliveUsers() {
        return List.of(new UserInfo(mContext.getUserId(), "name", 0));
    }

    @Override
    public void registerUserListener(Executor executor, UserListener listener) {
        // Ignore
    }

    @Override
    public void unregisterUserListener(UserListener listener) {
        // Ignore
    }
}
