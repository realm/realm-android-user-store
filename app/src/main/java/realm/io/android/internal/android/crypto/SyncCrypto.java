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

package realm.io.android.internal.android.crypto;

import android.app.Activity;

import java.security.KeyStoreException;

/**
 * Define methods that Android API should expose regardless of the API version.
 */
public interface SyncCrypto {
    /**
     * Encrypt the plain text using an {@code AES} key.
     * The {@code AES} key is encrypted using an {@code RSA} (stored in the Android KeyStore) key then saved
     * along the encrypted text using a delimiter.
     *
     * All operations require the KeyStore to be unlocked (authorized by the user authenticating with fingerprint/PIN/Pattern).
     *
     * @param plainText text to encrypt.
     * @return concatenated {@link String} of the encrypted text plus the encrypted {@code AES} used
     * to encrypt the text.
     * @throws KeyStoreException for any encryption error.
     */
    String encrypt(String plainText) throws KeyStoreException;

    /**
     * Decrypt the text previously encrypted with {@link #encrypt(String)}.
     *
     * This will first extract the encrypted {@code AES} key, then decrypt it using
     * the previously generated {@code RSA} private key. The decrypted {@code AES} key will
     * be used tp decrypt the text.
     *
     * All operations require the KeyStore to be unlocked (authorized by the user authenticating with fingerprint/PIN/Pattern).
     *
     * @param cipherText encrypted text.
     * @return decrypted text.
     * @throws KeyStoreException for any decryption error.
     */
    String decrypt(String cipherText) throws KeyStoreException;

    /**
     * Generates an asymmetric key pair ({@code RSA}) in the Android Keystore.
     *
     *  All operations require the KeyStore to be unlocked (authorized by the user authenticating with fingerprint/PIN/Pattern).
     * @throws KeyStoreException for any KeyStore error.
     */
    void create_key() throws KeyStoreException;

    /**
     * Check if the Android KeyStore is unlocked.
     * @return {@code true} if the KeyStore is unlocked, {@code false} otherwise.
     * @throws KeyStoreException for any KeyStore error.
     */
    boolean is_keystore_unlocked() throws KeyStoreException;

    /**
     * Launch the Android {@link android.content.Intent} that will help define or unlock the KeyStore.
     *
     * Application are encouraged to check if the KeyStore is unlocked by overriding {@link Activity#onResume()}
     * when the user finishes defining unlocking the KeyStore or cancel and go bak to the application.
     * <pre>
     *  protected void onResume() {
     *   super.onResume();
     *   try {
     *     // We return to the app after the KeyStore is unlocked or not.
     *       if (cryptoClient.isKeystoreUnlocked()) {
     *       // Encrypt/Decrypt
     *       } else {
     *       // Invite the user to unlock the KeyStore to continue
     *       }
     *   } catch (KeyStoreException e) {
     *       e.printStackTrace();
     *     }
     *   }
     * </pre>
     * @throws KeyStoreException for any KeyStore error.
     */
    void unlock_keystore() throws KeyStoreException;
}
