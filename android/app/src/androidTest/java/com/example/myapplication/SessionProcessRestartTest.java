package com.example.myapplication;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import backend.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run the two methods in separate instrumentation processes with a force-stop between them. */
@RunWith(AndroidJUnit4.class)
public class SessionProcessRestartTest {
    private static final String ID="11111111-1111-1111-1111-111111111111";
    private BackendConfig config() {
        return new BackendConfig(new KeyValueStore() {
            final java.util.Map<String,String> values=new java.util.HashMap<>();
            public String get(String key,String fallback){return values.getOrDefault(key,fallback);}
            public void put(String key,String value){values.put(key,value);}
            public void remove(String key){values.remove(key);}
        });
    }
    private EncryptedSessionStore store() { return new EncryptedSessionStore(InstrumentationRegistry.getInstrumentation().getTargetContext(),"process-test"); }
    private BackendUserSession.Transport transport() {
        return (o,m,p,t,b)-> {
            if (p.equals("/api/auth/login"))
                return BackendUserSession.body("accessToken","from-first-process","refreshToken","rotate-me","userId",ID,"username","fixture").toString();
            if (p.equals("/api/users/me"))
                return BackendUserSession.body("id",ID,"username","fixture","displayName","Saved name","role","MEMBER","status","ACTIVE",
                    "languageTag","zh-CN","theme","dark","avatarColor",5).toString();
            throw new AssertionError("Restore should not re-send credentials.");
        };
    }
    @Test public void writeBeforeProcessDeath() {
        var s=new BackendUserSession(config(),transport(),store()); s.login("fixture","never-save-this-password");
        assertTrue(s.hasSession());
    }
    @Test public void restoreInNewProcess() {
        var s=new BackendUserSession(config(),transport(),store());
        try {
            assertTrue(s.hasSession());assertEquals(ID,s.userId());assertEquals("from-first-process",s.accessToken());
            assertEquals("Saved name",s.displayName());assertEquals("zh-CN",s.profile().optString("languageTag"));
            assertEquals("dark",s.profile().optString("theme"));assertEquals(5,s.profile().optInt("avatarColor"));
            s.clear();assertFalse(new BackendUserSession(config(),transport(),store()).hasSession());
        } finally { store().clear(); }
    }
}
