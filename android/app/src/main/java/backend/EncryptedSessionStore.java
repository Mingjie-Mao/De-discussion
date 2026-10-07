package backend;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM in Android Keystore. noBackupFilesDir excludes device/cloud backups. */
public final class EncryptedSessionStore implements SessionStore {
    private final AtomicFile file;
    private final String alias;
    public EncryptedSessionStore(Context context, String role) {
        alias = "de-discussion-session-" + role;
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), alias + ".bin"));
    }
    private SecretKey key() throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (keys.containsAlias(alias)) return (SecretKey) keys.getKey(alias, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public synchronized String read() {
        if (!file.getBaseFile().exists()) return null;
        try {
            if (file.getBaseFile().length() > 65536) throw new IllegalStateException("Invalid session size");
            byte[] bytes = file.readFully();
            if (bytes.length < 29 || bytes.length > 65536) throw new IllegalStateException("Invalid session size");
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte version = buffer.get();
            if (version != 1) throw new IllegalStateException("Unknown session format");
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] data = new byte[buffer.remaining()]; buffer.get(data);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(alias.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception unavailable) { clear(); return null; }
    }
    public synchronized void write(String snapshot) {
        FileOutputStream output = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(alias.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(snapshot.getBytes(StandardCharsets.UTF_8));
            byte[] data = ByteBuffer.allocate(1 + cipher.getIV().length + encrypted.length)
                    .put((byte)1).put(cipher.getIV()).put(encrypted).array();
            output = file.startWrite(); output.write(data); file.finishWrite(output);
        } catch (Exception failure) {
            if (output != null) file.failWrite(output);
            clear();
            throw new BackendException(0, "Could not securely save the login session.");
        }
    }
    public synchronized void clear() { file.delete(); }
}
