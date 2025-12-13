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
package com.android.crossdevicesync.feature.mode;

import static com.android.crossdevicesync.feature.mode.ContextualModeSyncController.DATASTORE_NAME_PREFIX;
import static com.android.crossdevicesync.feature.mode.ContextualModeSyncController.NETWORK_ID;
import static com.android.crossdevicesync.feature.mode.ModeSyncDocs.CONTEXTUAL_MODES_DOC_ID;
import static com.android.crossdevicesync.feature.mode.ModeSyncDocs.getManualDoNotDisturbStatePath;
import static com.android.crossdevicesync.feature.mode.ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_ENABLED;
import static com.android.crossdevicesync.feature.mode.ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED;

import static com.google.common.truth.Truth.assertThat;

import android.app.modes.ContextualMode;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.os.PersistableBundle;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.crossdevicesync.data.SharedDataStore.Document;
import com.android.crossdevicesync.data.dagger.TestSharedDataStoreComponent;
import com.android.crossdevicesync.data.fake.FakeSharedDataStore;
import com.android.crossdevicesync.data.fake.TestSharedDataStoreFactory;
import com.android.crossdevicesync.data.storage.IStorage;
import com.android.crossdevicesync.network.NetworkManager.Network;
import com.android.crossdevicesync.network.fake.FakeNetworkManager.FakeRemoteDevice;
import com.android.crossdevicesync.services.SyncServiceComponentTestBase;

import com.google.android.submerge.StorageInterface.StorageException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ContextualModeSyncControllerTest extends SyncServiceComponentTestBase {
    private static final String REMOTE_NODE_ID = "remote_node";

    private UserHandle mUser;
    private ContextualModeSyncController mController;
    private ContextualMode mDoNotDisturbMode;
    private TestSharedDataStoreFactory<String> mDataStoreFactory;
    private FakeSharedDataStore<String> mSharedDataStore;
    private IStorage mStorage;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        super.setUp();
        mUser = mContext.getUser();
        mDoNotDisturbMode =
                new ContextualMode.Builder("id")
                        .setType(ContextualMode.TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(ContextualMode.STATE_INACTIVE)
                        .build();
        mFakeContextualModeManager.addOrUpdateMode(mUser, mDoNotDisturbMode);
        mFakeContextualModeManager.setModeSyncEnabled(mUser, true);
        mController = mContextualModeSyncControllerFactory.create(mUser);
        mFakeNetworkManager.init();
        mFakeMetadataPublisher.init();
        mController.start();
        TestSharedDataStoreComponent component =
                mSharedDataStoreComponentBuilderWrapper.getComponent(
                        DATASTORE_NAME_PREFIX + mUser.getIdentifier());
        mDataStoreFactory =
                (TestSharedDataStoreFactory<String>) component.getTestSharedDataStoreFactory();
        mSharedDataStore = (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        mStorage = component.getStorage();
    }

    @Test
    public void start_dataStoreAndNetworkInitialized() {
        assertThat(mSharedDataStore).isNotNull();
        assertThat(mSharedDataStore.isOpen()).isTrue();
        assertThat(isDndActiveInSharedDataStore()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNotNull();
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(mUser.getIdentifier())
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_ENABLED))
                .isTrue();
    }

    @Test
    public void testDndChange_writeToSharedDataStore() {
        // Turn on DND.
        mFakeContextualModeManager.addOrUpdateMode(mUser, activated(mDoNotDisturbMode));

        assertThat(isDndActiveInSharedDataStore()).isTrue();

        // Turn off DND.
        mFakeContextualModeManager.addOrUpdateMode(mUser, deactivated(mDoNotDisturbMode));

        assertThat(isDndActiveInSharedDataStore()).isFalse();
    }

    @Test
    public void testRemoteDndChange_updateModeState() throws Exception {
        // Remote turn on DND.
        remotePutDndActiveToSharedDataStore(true);

        assertThat(mFakeContextualModeManager.getModes(mUser))
                .contains(activated(mDoNotDisturbMode));

        // Remote turn off DND.
        remotePutDndActiveToSharedDataStore(false);

        assertThat(mFakeContextualModeManager.getModes(mUser))
                .contains(deactivated(mDoNotDisturbMode));
    }

    @Test
    public void modeSyncDisabled_resourcesAreCleanedUp() {
        mFakeContextualModeManager.setModeSyncEnabled(mUser, false);

        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNull();
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(mUser.getIdentifier())
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_ENABLED))
                .isFalse();
    }

    @Test
    public void stop_resourcesAreCleanedUp() throws StorageException {
        mController.stop();

        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNull();
        // Check that the storage still contains the document
        mStorage.open();
        assertThat(mStorage.getDocument(CONTEXTUAL_MODES_DOC_ID)).isNotNull();
    }

    @Test
    public void stop_deleteDataStore() throws StorageException {
        mController.stop(true);

        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNull();
        // Check that the document is deleted.
        mStorage.open();
        assertThat(mStorage.getDocument(CONTEXTUAL_MODES_DOC_ID)).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void modeSyncDisabledAndEnabled_dataStoreOpenAfterClose() {
        // Make close async.
        mSharedDataStore.setAsync(true);

        // Quickly toggle mode sync to off and on multiple times.
        mFakeContextualModeManager.setModeSyncEnabled(mUser, false);
        mFakeContextualModeManager.setModeSyncEnabled(mUser, true);
        mFakeContextualModeManager.setModeSyncEnabled(mUser, false);
        mFakeContextualModeManager.setModeSyncEnabled(mUser, true);

        // Verify that data store is being closed.
        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mDataStoreFactory.lastDataStore()).isSameInstanceAs(mSharedDataStore);

        // Verify that mode change during this time will be ignored.
        mFakeContextualModeManager.addOrUpdateMode(mUser, activated(mDoNotDisturbMode));
        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mDataStoreFactory.lastDataStore()).isSameInstanceAs(mSharedDataStore);

        // Finish close.
        mSharedDataStore.setAsync(false);

        // Verify that data store is re-created.
        assertThat(mDataStoreFactory.lastDataStore()).isNotSameInstanceAs(mSharedDataStore);
        mSharedDataStore = (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        assertThat(mSharedDataStore.isOpen()).isTrue();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNotNull();

        // Verify that mode sync is working.
        mFakeContextualModeManager.addOrUpdateMode(mUser, deactivated(mDoNotDisturbMode));
        mFakeContextualModeManager.addOrUpdateMode(mUser, activated(mDoNotDisturbMode));
        assertThat(isDndActiveInSharedDataStore()).isTrue();

        // Verify that metadata is correct.
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(mUser.getIdentifier())
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_ENABLED))
                .isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void modeSyncEnabledAndDisabled_dataStoreCloseAfterOpen() {
        // Turn off mode sync.
        mFakeContextualModeManager.setModeSyncEnabled(mUser, false);
        // Make init async.
        mDataStoreFactory.setDataStoresAsync(true);

        // Quickly toggle mode sync to on and off multiple times.
        mFakeContextualModeManager.setModeSyncEnabled(mUser, true);
        mFakeContextualModeManager.setModeSyncEnabled(mUser, false);
        mFakeContextualModeManager.setModeSyncEnabled(mUser, true);
        mFakeContextualModeManager.setModeSyncEnabled(mUser, false);

        // Verify that data store is being initialized.
        assertThat(mDataStoreFactory.lastDataStore()).isNotSameInstanceAs(mSharedDataStore);
        mSharedDataStore = (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();
        assertThat(mSharedDataStore.isOpen()).isTrue();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNotNull();

        // Verify that mode change during this time will be ignored.
        mFakeContextualModeManager.addOrUpdateMode(mUser, activated(mDoNotDisturbMode));
        assertThat(mSharedDataStore.isOpen()).isTrue();
        assertThat(isDndActiveInSharedDataStore()).isFalse();

        // Finish init.
        mSharedDataStore.setAsync(false);

        // Verify that data store is closed afterwards.
        assertThat(mDataStoreFactory.lastDataStore()).isSameInstanceAs(mSharedDataStore);
        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNull();

        // Verify that mode change is ignored.
        mFakeContextualModeManager.addOrUpdateMode(mUser, deactivated(mDoNotDisturbMode));
        mFakeContextualModeManager.addOrUpdateMode(mUser, activated(mDoNotDisturbMode));
        assertThat(mDataStoreFactory.lastDataStore()).isSameInstanceAs(mSharedDataStore);
        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNull();

        // Verify that metadata is correct.
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(mUser.getIdentifier())
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_ENABLED))
                .isFalse();
    }

    @Test
    public void dndRemoved_resourcesAreCleanedUp() {
        mFakeContextualModeManager.removeMode(mUser, mDoNotDisturbMode.getId());

        assertThat(mSharedDataStore.isOpen()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNull();
        // The metadata should not change.
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(mUser.getIdentifier())
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_ENABLED))
                .isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void dndAdded_dataStoreOpen() {
        mFakeContextualModeManager.removeMode(mUser, mDoNotDisturbMode.getId());

        mFakeContextualModeManager.addOrUpdateMode(mUser, mDoNotDisturbMode);
        mSharedDataStore = (FakeSharedDataStore<String>) mDataStoreFactory.lastDataStore();

        assertThat(mSharedDataStore).isNotNull();
        assertThat(mSharedDataStore.isOpen()).isTrue();
        assertThat(isDndActiveInSharedDataStore()).isFalse();
        assertThat(mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier())).isNotNull();
        assertThat(
                        mFakeMetadataPublisher
                                .getMetadata(mUser.getIdentifier())
                                .getBoolean(METADATA_CONTEXTUAL_MODE_SYNC_ENABLED))
                .isTrue();
    }

    @Test
    public void testSyncNetwork_remoteDeviceMustSupportModeSync() {
        Network network = mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier());

        addRemoteDevice(
                /* associationId= */ 1, /* syncSupported= */ false, /* syncEnabled= */ true);
        addRemoteDevice(/* associationId= */ 2, /* syncSupported= */ true, /* syncEnabled= */ true);

        assertThat(network.getRemoteDevices().containsKey(1)).isFalse();
        assertThat(network.getRemoteDevices().containsKey(2)).isTrue();
    }

    @Test
    public void testSyncNetwork_remoteDeviceMustEnabledModeSync() {
        Network network = mFakeNetworkManager.getNetwork(NETWORK_ID, mUser.getIdentifier());

        addRemoteDevice(/* associationId= */ 1, /* syncSupported= */ true, /* syncEnabled= */ true);
        addRemoteDevice(
                /* associationId= */ 2, /* syncSupported= */ true, /* syncEnabled= */ false);

        assertThat(network.getRemoteDevices().containsKey(1)).isTrue();
        assertThat(network.getRemoteDevices().containsKey(2)).isFalse();
    }

    private boolean isDndActiveInSharedDataStore() {
        Document<String> doc = mSharedDataStore.getDocumentUnchecked(CONTEXTUAL_MODES_DOC_ID);
        return ModeSyncDocs.isModeStateActive(doc, getManualDoNotDisturbStatePath());
    }

    private void remotePutDndActiveToSharedDataStore(boolean active) throws Exception {
        mSharedDataStore
                .transactAsRemote(
                        CONTEXTUAL_MODES_DOC_ID,
                        doc -> {
                            ModeSyncDocs.putModeStateActive(
                                    doc, getManualDoNotDisturbStatePath(), active);
                            return true;
                        },
                        REMOTE_NODE_ID)
                .get();
    }

    private FakeRemoteDevice addRemoteDevice(
            int associationId, boolean syncSupported, boolean syncEnabled) {
        PersistableBundle metadata = new PersistableBundle();
        metadata.putPersistableBundle(
                CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC, new PersistableBundle());
        metadata.getPersistableBundle(CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC)
                .putBoolean(METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED, syncSupported);
        metadata.getPersistableBundle(CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC)
                .putBoolean(METADATA_CONTEXTUAL_MODE_SYNC_ENABLED, syncEnabled);
        return mFakeNetworkManager.addRemoteDevice(
                new AssociationInfo.Builder(associationId, mContext.getUserId(), "package")
                        .setMetadata(metadata)
                        .setDisplayName("test")
                        .build());
    }

    private static ContextualMode activated(ContextualMode mode) {
        return new ContextualMode.Builder(mode).setState(ContextualMode.STATE_ACTIVE).build();
    }

    private static ContextualMode deactivated(ContextualMode mode) {
        return new ContextualMode.Builder(mode).setState(ContextualMode.STATE_INACTIVE).build();
    }
}
