package com.example.myapplication;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import backend.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EncryptedSessionStoreTest {
    @Test public void encryptedRestartRotationAndLogoutUseTheRealKeystore() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String role="test-"+UUID.randomUUID();
        var store=new EncryptedSessionStore(context,role);
        try {
            store.write("access-and-refresh-secret");
            var file=new File(context.getNoBackupFilesDir(),"de-discussion-session-"+role+".bin");
            assertFalse(new String(Files.readAllBytes(file.toPath()),StandardCharsets.ISO_8859_1).contains("access-and-refresh-secret"));
            assertEquals("access-and-refresh-secret",new EncryptedSessionStore(context,role).read());
            store.write("rotated-secret"); assertEquals("rotated-secret",new EncryptedSessionStore(context,role).read());
            store.clear(); assertNull(new EncryptedSessionStore(context,role).read());
            store.write("old-secret"); Files.write(file.toPath(),new byte[]{1,2,3});
            assertNull(new EncryptedSessionStore(context,role).read()); assertFalse(file.exists());
        } finally {
            store.clear(); var keys=java.security.KeyStore.getInstance("AndroidKeyStore");keys.load(null);
            keys.deleteEntry("de-discussion-session-"+role);
        }
    }

    @Test public void cameraSizedAvatarIsSampledBeforeNetworkUpload() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var file=File.createTempFile("avatar-test", ".png", context.getCacheDir());
        var bitmap=android.graphics.Bitmap.createBitmap(2048,1024,android.graphics.Bitmap.Config.ARGB_8888);
        try {
            try(var out=new java.io.FileOutputStream(file)) { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)); }
            var upload=BackendMedia.readAvatar(context,android.net.Uri.fromFile(file));
            var decoded=android.graphics.BitmapFactory.decodeByteArray(upload.bytes(),0,upload.bytes().length);
            assertNotNull(decoded);assertEquals(512,decoded.getWidth());assertEquals(256,decoded.getHeight());decoded.recycle();
        } finally { bitmap.recycle();file.delete(); }
    }
}
