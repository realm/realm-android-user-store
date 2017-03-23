/*
 * Copyright 2016 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.android;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.SyncManager;
import io.realm.SyncUser;
import io.realm.TestHelper;
import io.realm.rule.TestRealmConfigurationFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SecureUserStoreTests {
    @Rule
    public final TestRealmConfigurationFactory configFactory = new TestRealmConfigurationFactory();

    private Realm realm;
    private SecureUserStore userStore;

    @Before
    public void setUp() throws KeyStoreException {
        Realm.init(InstrumentationRegistry.getTargetContext());

        // This will set the 'm_metadata_manager' in 'sync_manager.cpp' to be 'null'
        // causing the SyncUser to remain in memory.
        // They're actually not persisted into disk.
        // move this call to `tearDown` to clean in-memory & on-disk users
        // once https://github.com/realm/realm-object-store/issues/207 is resolved
        TestHelper.resetSyncMetadata();

        RealmConfiguration realmConfig = configFactory.createConfiguration();
        realm = Realm.getInstance(realmConfig);

        userStore = new SecureUserStore(InstrumentationRegistry.getTargetContext());
        assertTrue("KeyStore Should be Unlocked before running tests on device!", userStore.isKeystoreUnlocked());
        SyncManager.setUserStore(userStore);
    }

    @After
    public void tearDown() {
        if (realm != null) {
            realm.close();
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    @Test
    public void keystore_unlocked() throws KeyStoreException {
        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            fail("Test not supported under this Android API level");
        } else {
            // check if the keystore is unlocked before proceeding
            KeyguardManager kgm = (KeyguardManager) InstrumentationRegistry.getTargetContext().getSystemService(Context.KEYGUARD_SERVICE);
            // is KeyguardSecure excludes the slide lock case.
            boolean isKeyguardSecure = (kgm != null) && kgm.isKeyguardSecure();

            CipherClient client = new CipherClient(InstrumentationRegistry.getTargetContext());

            assertEquals(isKeyguardSecure, client.isKeystoreUnlocked());
            assertTrue(isKeyguardSecure);
            assertTrue(client.isKeystoreUnlocked());
        }
    }

    @Test
    public void get() throws KeyStoreException {
        SyncUser user = TestHelper.createTestUser();

        userStore.put(user);
        SyncUser decrypted_entry = userStore.get(user.getIdentity());
        assertEquals(user, decrypted_entry);
    }

    @Test
    public void get_shouldNotCreateDuplicateKeyStore() throws KeyStoreException {
        SyncUser user = TestHelper.createTestUser();
        userStore.put(user);

        // Using a new instance, should not cause a creation of a new RSA key (SyncCrypto#create_key_if_not_available)
        userStore = new SecureUserStore(InstrumentationRegistry.getTargetContext());

        SyncUser decrypted_entry = userStore.get(user.getIdentity());
        assertEquals(user, decrypted_entry);

        user.logout();
        assertNull(userStore.get(user.getIdentity()));
    }

    @Test
    public void getCurrent() throws KeyStoreException {
        SyncUser user = TestHelper.createTestUser();

        userStore.put(user);
        SyncUser decrypted_entry = userStore.getCurrent();
        assertEquals(user, decrypted_entry);
    }

    @Test
    public void getCurrent_ShouldThrowIfMultiple() throws KeyStoreException {
        SyncUser user1 = TestHelper.createTestUser(Long.MAX_VALUE, "user1");
        SyncUser user2 = TestHelper.createTestUser(Long.MAX_VALUE, "user2");

        assertNotEquals(user1, user2);

        userStore.put(user1);
        userStore.put(user2);

        SyncUser decrypted_entry = null;
        try {
            decrypted_entry = userStore.getCurrent();
            fail();
        } catch (IllegalStateException expected) {
            assertEquals("Current user is not valid if more that one valid, logged-in user exists.", expected.getMessage());
        }
        assertNull(decrypted_entry);
    }

    @Test
    public void remove() throws KeyStoreException {
        SyncUser user = TestHelper.createTestUser();

        userStore.put(user);
        SyncUser decrypted_entry = userStore.getCurrent();
        assertEquals(user, decrypted_entry);

        userStore.remove(user.getIdentity());

        assertNull(userStore.getCurrent());
    }

    @Test
    public void allUsers() throws KeyStoreException {
        SyncUser user1 = TestHelper.createTestUser(Long.MAX_VALUE, "user1");
        SyncUser user2 = TestHelper.createTestUser(Long.MAX_VALUE, "user2");

        assertNotEquals(user1, user2);

        userStore.put(user1);
        userStore.put(user2);

        user2.logout();
        SyncUser currentUser = userStore.getCurrent();
        assertEquals(user1, currentUser);

        SyncUser user3 = TestHelper.createTestUser(Long.MAX_VALUE, "user3");
        userStore.put(user3);

        List<SyncUser> syncUsers = new ArrayList<SyncUser>(userStore.allUsers());
        Collections.sort(syncUsers, new Comparator<SyncUser>() {
            @Override
            public int compare(SyncUser o1, SyncUser o2) {
                return o1.getIdentity().compareTo(o2.getIdentity());
            }
        });
        assertEquals(2, syncUsers.size());
        assertEquals(user1, syncUsers.get(0));
        assertEquals(user3, syncUsers.get(1));
    }
}
