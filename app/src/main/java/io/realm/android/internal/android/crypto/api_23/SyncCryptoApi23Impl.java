package io.realm.android.internal.android.crypto.api_23;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Calendar;
import javax.security.auth.x500.X500Principal;

import io.realm.android.internal.android.crypto.SyncCrypto;
import io.realm.android.internal.android.crypto.api_18.SyncCryptoApi18Impl;

/**
 * Implements {@link SyncCrypto} methods for API 23 (after the Android KeyStore public API), using
 * the introduced {@link android.security.keystore.KeyGenParameterSpec}.
 */
public class SyncCryptoApi23Impl extends SyncCryptoApi18Impl {
    public SyncCryptoApi23Impl(Context context) throws KeyStoreException {
        super(context);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void create_key_if_not_available() throws KeyStoreException {
        if (!keyStore.containsAlias(alias)) {
            try {
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 10);

                KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setCertificateSubject(new X500Principal(X500_PRINCIPAL))
                        .setCertificateSerialNumber(BigInteger.ONE)
                        .setCertificateNotBefore(start.getTime())
                        .setCertificateNotAfter(end.getTime())
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                        .setRandomizedEncryptionRequired(false)
                        .build();
                keyGenerator.initialize(spec);
                keyGenerator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new KeyStoreException(e);
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
                throw new KeyStoreException(e);
            } catch (NoSuchProviderException e) {
                e.printStackTrace();
                throw new KeyStoreException(e);
            }
        }
    }
}
