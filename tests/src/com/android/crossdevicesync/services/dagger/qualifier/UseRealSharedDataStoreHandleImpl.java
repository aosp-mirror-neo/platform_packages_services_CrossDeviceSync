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
package com.android.crossdevicesync.services.dagger.qualifier;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.android.crossdevicesync.data.SharedDataStoreHandle;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.inject.Qualifier;

/**
 * Qualifier to annotate a boolean that indicates whether to use a real {@link
 * SharedDataStoreHandle} impl.
 */
@Qualifier
@Documented
@Retention(RUNTIME)
public @interface UseRealSharedDataStoreHandleImpl {}
