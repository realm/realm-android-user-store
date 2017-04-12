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

import android.content.Context;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import io.realm.RealmFileUserStore;
import io.realm.SyncUser;
import io.realm.log.RealmLog;

/**
 * Encrypt and decrypt the token ({@link SyncUser}) using Android built in KeyStore capabilities.
 * According to the Android API this picks the right algorithm to perform the operations.
 * Prior to API 18 there were no AndroidKeyStore API, but the Linux daemon existed, so it's possible
 * with the help of this code: https://github.com/nelenkov/android-keystore to work with.
 *
 * On API &gt; = 18, we generate an AES key to encrypt we then generate and uses the RSA key inside the KeyStore
 * to encrypt the AES key that we store along the encrypted data inside the Realm Object Store.
 *
 * This throws a {@link KeyStoreException} in case of an error or KeyStore being unavailable (unlocked).
 *
 * See also: io.realm.internal.android.crypto.class.CipherClient
 * @see <a href="https://developer.android.com/training/articles/keystore.html">Android KeyStore</a>
 */
public class SecureUserStore extends RealmFileUserStore {
    private final CipherClient cipherClient;

    public SecureUserStore(final Context context) throws KeyStoreException {
        cipherClient = new CipherClient(context);
    }

    /**
     * Encrypt then save a {@link SyncUser} object. If another user already exists, it will be replaced.
     *  {@link SyncUser#getIdentity()} is used as a unique identifier of a given {@link SyncUser}.
     *
     * @param user {@link SyncUser} object to store.
     */
    @Override
    public void put(SyncUser user) {
        try {
            String userSerialisedAndEncrypted = cipherClient.encrypt(user.toJson());
            nativeUpdateOrCreateUser(user.getIdentity(), userSerialisedAndEncrypted, user.getAuthenticationUrl().toString());
        } catch (KeyStoreException e) {
            RealmLog.error(e);
        }
    }

    /**
     * Retrieves and decrypts the current {@link SyncUser}.
     * <p>
     * This method will throw an exception if more than one valid, logged in users exist.
     * @return {@link SyncUser} object or {@code null} if not found.
     */
    @Override
    public SyncUser getCurrent() {
        String userJson = nativeGetCurrentUser();
        return toDecryptedSyncUserOrNull(userJson);
    }

    /**
     * Retrieves and decrypts the specified {@link SyncUser}.
     * <p>
     * This method will throw an exception if more than one valid, logged in users exist.
     * @return {@link SyncUser} object or {@code null} if not found.
     */
    @Override
    public SyncUser get(String identity) {
        String userJson = nativeGetUser(identity);
        return toDecryptedSyncUserOrNull(userJson);
    }

    /**
     * Retries all {@link SyncUser}.
     * @return Active (logged-in) users.
     */
    @Override
    public Collection<SyncUser> allUsers() {
        String[] allUsers = nativeGetAllUsers();
        if (allUsers != null && allUsers.length > 0) {
            ArrayList<SyncUser> users = new ArrayList<SyncUser>(allUsers.length);
            for (String userJson : allUsers) {
                String userSerialisedAndDecrypted = null;
                try {
                    userSerialisedAndDecrypted = cipherClient.decrypt(userJson);
                } catch (KeyStoreException e) {
                    RealmLog.error(e);
                    // returning null will probably penalise the other Users
                }
                users.add(SyncUser.fromJson(userSerialisedAndDecrypted));
            }
            return users;
        }
        return Collections.emptyList();
    }

    /**
     * Checks whether the Android KeyStore is available.
     * This should be called before {@link #get(String)}, {@link #allUsers()})}, {@link #getCurrent()} or {@link #put(SyncUser)} as those need the KeyStore unlocked.
     * @return {@code true} if the Android KeyStore in unlocked.
     * @throws KeyStoreException in case of error.
     */
    public boolean isKeystoreUnlocked () throws KeyStoreException {
        return cipherClient.isKeystoreUnlocked();
    }

    /**
     * Helps unlock the KeyStore this will launch the appropriate {@link android.content.Intent}
     * to start the platform system {@link android.app.Activity} to create/unlock the KeyStore.
     *
     * @throws KeyStoreException in case of error.
     */
    public void unlockKeystore () throws KeyStoreException {
        cipherClient.unlockKeystore();
    }

    private SyncUser toDecryptedSyncUserOrNull(String userEncryptedJson) {
        if (userEncryptedJson != null) {
            try {
                String userSerialisedAndDecrypted = cipherClient.decrypt(userEncryptedJson);
                return SyncUser.fromJson(userSerialisedAndDecrypted);
            } catch (KeyStoreException e) {
                RealmLog.error(e);
                return null;
            }
        }
        return null;
    }
}
