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

import static android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC;
import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_METADATA_UPDATE;
import static android.companion.cts.common.CdmMessage.MESSAGE_RESPONSE_FAILURE;
import static android.companion.cts.common.CdmMessage.MESSAGE_RESPONSE_SUCCESS;

import android.annotation.UserIdInt;
import android.companion.ActionRequest;
import android.companion.ActionResult;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager.OnAssociationsChangedListener;
import android.companion.DevicePresenceEvent;
import android.companion.cts.common.CdmMessage;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Log;

import com.android.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.crossdevicesync.lib.CdmPipeImpl;
import com.android.internal.util.ArrayUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** A fake implementation of {@link CompanionDeviceManagerProxy} for testing. */
public class FakeCompanionDeviceManagerProxy implements CompanionDeviceManagerProxy {
    private static final String TAG = "FakeCdmProxy";
    private static final int DEFAULT_ASSOCIATION_ID = 1;

    private final CdmPipeImpl mCdmPipe;
    private AssociationInfo mDefaultAssociation;

    private record MessageListener(Executor executor, BiConsumer<Integer, byte[]> listener) {}

    private record ActionResultListener(
            String serviceName, Executor executor, BiConsumer<Integer, ActionResult> listener) {}

    private final List<ActionResultListener> mActionResultListeners = new ArrayList<>();
    private final Map<Integer, List<MessageListener>> mMessageListeners = new HashMap<>();
    private final Map<Integer, PersistableBundle> mLocalMetadata = new HashMap<>();

    /**
     * Constructs a new {@link FakeCompanionDeviceManagerProxy}.
     *
     * @param context the application context.
     * @param cdmPipe the CDM pipe for transport simulation.
     */
    public FakeCompanionDeviceManagerProxy(Context context, CdmPipeImpl cdmPipe) {
        mDefaultAssociation =
                new AssociationInfo.Builder(
                                DEFAULT_ASSOCIATION_ID,
                                context.getUser().getIdentifier(),
                                context.getPackageName())
                        .setSelfManaged(true)
                        .setDisplayName("Test Device")
                        .build();
        mCdmPipe = cdmPipe;
        mCdmPipe.setInboundMessageListener(
                msg -> handleInboundMessage(DEFAULT_ASSOCIATION_ID, msg));
        Log.d(TAG, "FakeCompanionDeviceManagerProxy initialized.");
    }

    @Override
    public List<AssociationInfo> getAllAssociations(@UserIdInt int userId) {
        if (userId == UserHandle.USER_ALL || userId == mDefaultAssociation.getUserId()) {
            return List.of(mDefaultAssociation);
        }
        return Collections.emptyList();
    }

    @Override
    public List<AssociationInfo> getAllAssociationsWithTransports() {
        return List.of(mDefaultAssociation);
    }

    @Override
    public void addOnAssociationsChangedListener(
            Executor executor, OnAssociationsChangedListener listener, @UserIdInt int userId) {
        executor.execute(() -> listener.onAssociationsChanged(getAllAssociations(userId)));
    }

    @Override
    public void removeOnAssociationsChangedListener(OnAssociationsChangedListener listener) {
        // Do nothing
    }

    @Override
    public void addOnTransportsChangedListener(
            Executor executor, Consumer<List<AssociationInfo>> listener) {
        executor.execute(() -> listener.accept(getAllAssociationsWithTransports()));
    }

    @Override
    public void removeOnTransportsChangedListener(Consumer<List<AssociationInfo>> listener) {
        // Do nothing
    }

    @Override
    public void setOnDevicePresenceEventListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            Consumer<DevicePresenceEvent> listener) {
        if (ArrayUtils.contains(associationIds, DEFAULT_ASSOCIATION_ID)) {
            executor.execute(
                    () -> {
                        listener.accept(
                                new DevicePresenceEvent.Builder()
                                        .setAssociationId(DEFAULT_ASSOCIATION_ID)
                                        .setEvent(DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED)
                                        .build());
                        listener.accept(
                                new DevicePresenceEvent.Builder()
                                        .setAssociationId(DEFAULT_ASSOCIATION_ID)
                                        .setEvent(DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY)
                                        .build());
                    });
        }
    }

    @Override
    public void removeOnDevicePresenceEventListener(String serviceName) {
        // Do nothing
    }

    @Override
    public void sendMessage(int messageType, byte[] data, int[] associationIds) {
        Log.d(TAG, "Sending CDM message type: 0x" + Integer.toHexString(messageType));
        if (ArrayUtils.contains(associationIds, mDefaultAssociation.getId())) {
            mCdmPipe.getInputStreamForCdmTransport().feedMessage(messageType, data);
        }
    }

    @Override
    public void addOnMessageReceivedListener(
            Executor executor, int messageType, BiConsumer<Integer, byte[]> listener) {
        mMessageListeners
                .computeIfAbsent(messageType, k -> new ArrayList<>())
                .add(new MessageListener(executor, listener));
    }

    @Override
    public void removeOnMessageReceivedListener(
            int messageType, BiConsumer<Integer, byte[]> listener) {
        if (mMessageListeners.containsKey(messageType)) {
            mMessageListeners.get(messageType).removeIf(l -> l.listener().equals(listener));
        }
    }

    @Override
    public void requestAction(ActionRequest request, String serviceName, int[] associationIds) {
        ActionResult result =
                new ActionResult.Builder(
                                request.getAction(),
                                request.getOperation() == ActionRequest.OP_ACTIVATE
                                        ? ActionResult.RESULT_ACTIVATED
                                        : ActionResult.RESULT_DEACTIVATED)
                        .build();
        for (ActionResultListener listener : mActionResultListeners) {
            if (listener.serviceName.equals(serviceName)) {
                for (int id : associationIds) {
                    listener.executor.execute(() -> listener.listener.accept(id, result));
                }
            }
        }
    }

    @Override
    public void setOnActionResultListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            BiConsumer<Integer, ActionResult> listener) {
        mActionResultListeners.add(new ActionResultListener(serviceName, executor, listener));
    }

    @Override
    public void clearOnActionResultListener(String serviceName) {
        mActionResultListeners.removeIf(l -> l.serviceName.equals(serviceName));
    }

    @Override
    public void setLocalMetadata(int userId, String feature, PersistableBundle value) {
        Log.d(TAG, "Setting local metadata for user " + userId + ", feature " + feature);
        PersistableBundle userMetadata =
                mLocalMetadata.computeIfAbsent(userId, k -> new PersistableBundle());
        userMetadata.putPersistableBundle(feature, value);

        // Notify remote device of metadata update.
        mCdmPipe.getInputStreamForCdmTransport()
                .feedMessage(CdmMessage.Companion.forMetadataUpdate(userMetadata));

        mDefaultAssociation =
                new AssociationInfo.Builder(mDefaultAssociation)
                        .setTimeMetadataSent(System.currentTimeMillis())
                        .build();
    }

    /** Simplified version of {@link #setLocalMetadata(int, String, PersistableBundle)} */
    public void setLocalMetadata(int userId, String key, Object val) {
        PersistableBundle metadata =
                getLocalMetadata(userId).getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC);
        if (metadata == null) {
            metadata = new PersistableBundle();
        }
        metadata.putObject(key, val);
        setLocalMetadata(userId, FEATURE_CROSS_DEVICE_SYNC, metadata);
    }

    @Override
    public PersistableBundle getLocalMetadata(int userId) {
        PersistableBundle data = mLocalMetadata.get(userId);
        return data == null ? new PersistableBundle() : data;
    }

    private void handleInboundMessage(int associationId, CdmMessage msg) {
        int messageType = msg.getType();
        if (messageType == MESSAGE_RESPONSE_SUCCESS || messageType == MESSAGE_RESPONSE_FAILURE) {
            // Ignore response messages.
            return;
        }

        if (messageType == MESSAGE_REQUEST_METADATA_UPDATE) {
            Log.d(TAG, "Inbound metadata update message received.");
            mCdmPipe.getInputStreamForCdmTransport().feedMessage(msg.success());
            handleInboundMetadataUpdate(msg.getPayload());
        }

        if (mMessageListeners.containsKey(messageType)) {
            for (MessageListener messageListener : mMessageListeners.get(messageType)) {
                messageListener.executor.execute(
                        () -> messageListener.listener.accept(associationId, msg.getPayload()));
            }
        }
    }

    private void handleInboundMetadataUpdate(byte[] payload) {
        try {
            PersistableBundle metadata =
                    PersistableBundle.readFromStream(new ByteArrayInputStream(payload));
            metadata.putLong(AssociationInfo.METADATA_TIMESTAMP, System.currentTimeMillis());
            mDefaultAssociation =
                    new AssociationInfo.Builder(mDefaultAssociation).setMetadata(metadata).build();
            Log.d(TAG, "Metadata updated for association: " + mDefaultAssociation.getId());
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse inbound metadata update payload", e);
        }
    }
}
