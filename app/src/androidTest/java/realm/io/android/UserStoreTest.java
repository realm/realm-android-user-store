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

package realm.io.android;

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

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.User;
import io.realm.UserStore;
import realm.io.TestHelper;
import realm.io.rule.TestRealmConfigurationFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class UserStoreTest {
    @Rule
    public final TestRealmConfigurationFactory configFactory = new TestRealmConfigurationFactory();

    private Realm realm;

    @Before
    public void setUp() {
        RealmConfiguration realmConfig = configFactory.createConfiguration();
        realm = Realm.getInstance(realmConfig);
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
    public void encrypt_decrypt_UsingAndroidKeyStoreUserStore() throws KeyStoreException {
        User user = TestHelper.createTestUser();
        UserStore userStore = new SecureUserStore(InstrumentationRegistry.getTargetContext());
        User savedUser = userStore.put("crypted_entry", user);
        assertNull(savedUser);
        User decrypted_entry = userStore.get("crypted_entry");
        assertEquals(user, decrypted_entry);
     }
}
