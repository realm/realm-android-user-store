package io.realm.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import io.realm.ObjectStoreUserStore;
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
public class SecureUserStore extends ObjectStoreUserStore {
    private final CipherClient cipherClient;

    public SecureUserStore(final Context context) throws KeyStoreException {
        super(context.getFilesDir().getPath());
        cipherClient = new CipherClient(context);
    }

    /**
     * Store user as serialised and encrypted (Json), inside the private {@link SharedPreferences}.
     * @param user we want to save.
     * @return The previous user saved with this key or {@code null} if no user was replaced.
     */
    @Override
    public void put(SyncUser user) {
        try {
            String userSerialisedAndEncrypted = cipherClient.encrypt(user.toJson());
            updateOrCreateUser(user.getIdentity(), userSerialisedAndEncrypted, "");
        } catch (KeyStoreException e) {
            RealmLog.error(e);
        }
    }

    /**
     * Retrieves the {@link SyncUser} by decrypting first the serialised Json.
     * @return the {@link SyncUser} with the given key.
     */
    @Override
    public SyncUser get() {
        String userJson = getCurrentUser();
        if (userJson != null) {
            try {
                String userSerialisedAndDecrypted = cipherClient.decrypt(userJson);
                return SyncUser.fromJson(userSerialisedAndDecrypted);
            } catch (KeyStoreException e) {
                RealmLog.error(e);
                return null;
            }
        }
        return null;
    }

    /**
     * Remove current logged-in user.
     */
    @Override
    public void remove() {
        super.remove();
    }

    /**
     * Retries all {@link SyncUser}.
     * @return Active (logged-in) users.
     */
    @Override
    public Collection<SyncUser> allUsers() {
        String[] allUsers = getAllUsers();
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
}
