package com.music.echo.offline.files;

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;

import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Owns the Android Keystore key used to authorize biometric app unlock. */
final class BiometricUnlockKeyStore {
    private static final String KEY_ALIAS = "toxifiles_biometric_unlock_v1";

    private BiometricUnlockKeyStore() { }

    @TargetApi(Build.VERSION_CODES.P)
    static Cipher createCipher() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            try {
                Key key = keyStore.getKey(KEY_ALIAS, null);
                if (key instanceof SecretKey) {
                    Cipher existing = Cipher.getInstance("AES/GCM/NoPadding");
                    existing.init(Cipher.ENCRYPT_MODE, (SecretKey) key);
                    return existing;
                }
                keyStore.deleteEntry(KEY_ALIAS);
            } catch (KeyPermanentlyInvalidatedException invalidated) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        }

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(builder.build());
        SecretKey key = generator.generateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher;
    }
}
