package backend;

import org.junit.Test;
import static org.junit.Assert.*;

public class PersistentSessionTest {
    static final class Store implements SessionStore {
        String value;
        public String read() { return value; }
        public void write(String value) { this.value=value; }
        public void clear() { value=null; }
    }
    @Test public void processRestartRestoresAccountAndRotatedTokensWithoutPassword() {
        Store store=new Store(); var config=BackendAdminSessionTest.config();
        var transport=new BackendUserSessionTest.MemberTransport();
        var first=new BackendUserSession(config,transport,store); first.login("member","secret password");
        assertFalse(store.value.contains("secret password"));
        var restarted=new BackendUserSession(config,transport,store);
        assertTrue(restarted.hasSession()); assertEquals(first.userId(),restarted.userId());
        assertEquals("Campus Buddy",restarted.displayName());
        assertEquals("new1",restarted.withClient((client,token)-> {if(token.equals("old"))throw new BackendException(401,"expired");return token;}));
        var again=new BackendUserSession(config,transport,store); assertEquals("new1",again.accessToken());
        again.clear(); assertFalse(new BackendUserSession(config,transport,store).hasSession());
    }
    @Test public void differentOriginCorruptFileAndWrongRoleCannotRestore() {
        Store store=new Store(); var config=BackendAdminSessionTest.config(); var t=new BackendUserSessionTest.MemberTransport();
        new BackendUserSession(config,t,store).login("member","password");
        config.setBaseUrl("https://different.test"); assertFalse(new BackendUserSession(config,t,store).hasSession()); assertNull(store.value);
        store.value="broken"; assertFalse(new BackendUserSession(config,t,store).hasSession()); assertNull(store.value);
        new BackendUserSession(config,t,store).login("member","password");
        store.value=store.value.replace("MEMBER","ADMIN"); assertFalse(new BackendUserSession(config,t,store).hasSession());
    }
    @Test public void revokedRefreshDeletesPersistentSnapshot() {
        Store store=new Store(); var config=BackendAdminSessionTest.config();
        var transport=new BackendUserSessionTest.MemberTransport(){
            @Override public String call(String o,String m,String p,String token,org.json.JSONObject body){
                if(p.equals("/api/auth/refresh"))throw new BackendException(401,"revoked");return super.call(o,m,p,token,body);
            }
        };
        var session=new BackendUserSession(config,transport,store);session.login("member","password");
        assertThrows(BackendException.class,()->session.withClient((c,t)->{throw new BackendException(401,"expired");}));
        assertNull(store.value); assertFalse(session.hasSession());
    }
    @Test public void onlyTransientReadFailuresQualifyForRetry() {
        assertTrue(BackendClient.retryableRead(0));assertTrue(BackendClient.retryableRead(503));
        assertFalse(BackendClient.retryableRead(401));assertFalse(BackendClient.retryableRead(403));assertFalse(BackendClient.retryableRead(429));
    }
}
