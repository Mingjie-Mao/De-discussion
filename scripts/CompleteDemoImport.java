import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit, transactional import for the existing interview database. Never runs at application startup. */
public final class CompleteDemoImport {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String BATCH="legacy-full-v1";
    static final BCryptPasswordEncoder HASH=new BCryptPasswordEncoder();
    static Connection db;
    static JsonNode fixture,state;
    static ObjectNode credentials;
    static Instant anchor=Instant.now();
    static final Map<String,UUID> accounts=new HashMap<>();
    static final Map<String,Integer> writes=new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        if(args.length!=4 || !Set.of("--dry-run","--apply-interview-demo").contains(args[3]))
            throw new IllegalArgumentException("fixture.json import-state.json private-credentials.json --dry-run|--apply-interview-demo");
        boolean apply=args[3].equals("--apply-interview-demo");
        fixture=JSON.readTree(Path.of(args[0]).toFile()); state=JSON.readTree(Path.of(args[1]).toFile());
        Path secrets=Path.of(args[2]); credentials=Files.exists(secrets)?(ObjectNode)JSON.readTree(secrets.toFile()):JSON.createObjectNode();
        try(var connection=DriverManager.getConnection(required("DEMO_DATABASE_URL"),required("DEMO_DATABASE_USER"),required("DEMO_DATABASE_PASSWORD"))) {
            db=connection;db.setAutoCommit(false);
            try {
                exec("SELECT pg_advisory_xact_lock(7318020606)");
                if(scalar("SELECT count(*) FROM demo_data_batches WHERE batch_key=?",BATCH)>0) {System.out.println("{\"alreadyApplied\":true}");db.rollback();return;}
                Map<String,String> aliases=Map.of("campusviewer","1234","modmentor","12345");
                for(JsonNode user:fixture.path("users")) {
                    String name=user.path("username").asText(), local=user.path("localId").asText();
                    UUID original=UUID.fromString(state.path("users").path(local).path("id").asText());
                    UUID id=aliases.containsKey(name)?userId(aliases.get(name)):original;
                    accounts.put(local,id);
                    if(aliases.containsKey(name)) {
                        for(JsonNode p:fixture.path("posts")) {
                            if(p.path("author").asText().equals(local)) move("posts",remote("posts",p.path("localId").asText()),original,id);
                            for(JsonNode c:p.path("comments")) if(c.path("author").asText().equals(local)) move("comments",remote("comments",c.path("localId").asText()),original,id);
                        }
                        var media=state.path("media").fields();
                        while(media.hasNext()) {var m=media.next();if(m.getKey().startsWith(local+"/")) exec("UPDATE media_objects SET owner_id=? WHERE id=? AND owner_id=?",id,UUID.fromString(m.getValue().asText()),original);}
                    }
                }
                // Real accounts, random private passwords; these generated community members supply the old aggregate scores.
                List<UUID> readers=new ArrayList<>();
                int maximum=0;
                for(JsonNode p:fixture.path("posts")) {maximum=Math.max(maximum,Math.abs(p.path("score").asInt()));for(JsonNode c:p.path("comments")) maximum=Math.max(maximum,Math.abs(c.path("score").asInt()));}
                for(int i=1;i<=maximum;i++) readers.add(createUser(String.format("demo_reader_%02d",i),String.format("Campus Reader %02d",i)));
                int originals=0,comments=0;
                for(JsonNode p:fixture.path("posts")) {
                    UUID id=remote("posts",p.path("localId").asText());
                    if(scalar("SELECT count(*) FROM posts WHERE id=? AND title=? AND body=?",id,p.path("title").asText(),p.path("body").asText())!=1) throw new IllegalStateException("Imported post has changed; manual reconciliation required.");
                    // Original content stays intact; only missing presentation data and relative ordering are restored.
                    exec("UPDATE posts SET category=?,pin_rank=?,created_at=? WHERE id=?",p.path("category").asText("Study"),p.path("pinRank").isNull()?null:p.path("pinRank").asInt(),at(p.path("ageMs").asLong()),id);
                    votes("post_votes","post_id",id,p.path("score").asInt(),readers,at(p.path("ageMs").asLong())); originals++;
                    for(JsonNode c:p.path("comments")) {
                        UUID cid=remote("comments",c.path("localId").asText());
                        exec("UPDATE comments SET created_at=? WHERE id=?",at(c.path("ageMs").asLong()),cid);
                        votes("comment_votes","comment_id",cid,c.path("score").asInt(),readers,at(c.path("ageMs").asLong()));comments++;
                    }
                }
                UUID member=userId("1234");
                for(JsonNode id:fixture.path("following")) follow(member,accounts.get(id.asText()));
                // Give the signed-in demo profile a persisted social graph too.
                for(JsonNode u:fixture.path("users")) {
                    UUID id=accounts.get(u.path("localId").asText());if(!id.equals(member)) follow(id,member);
                }
                for(JsonNode id:fixture.path("bookmarks")) bookmark(member,remote("posts",id.asText()));
                int liked=0;
                UUID ownPost=null;
                for(JsonNode p:fixture.path("posts")) {
                    UUID id=remote("posts",p.path("localId").asText());
                    if(liked++<5) {exec("INSERT INTO post_votes(user_id,post_id,value) VALUES(?,?,1) ON CONFLICT DO NOTHING",member,id);bookmark(member,id);}
                    if(ownPost==null && accounts.get(p.path("author").asText()).equals(member)) ownPost=id;
                }
                UUID buddy=findLocal("studybuddy"),sage=findLocal("treesage"),pilot=findLocal("uxpilot");
                if(ownPost!=null) {
                    exec("INSERT INTO post_votes(user_id,post_id,value) VALUES(?,?,1) ON CONFLICT DO NOTHING",buddy,ownPost);
                    bookmark(sage,ownPost);
                    notification(member,buddy,"LIKE","studybuddy liked your post",ownPost);
                    notification(member,sage,"BOOKMARK","treesage saved your post",ownPost);
                    UUID cid=stable("welcome-mention");
                    exec("INSERT INTO comments(id,post_id,author_id,body,created_at,depth) VALUES(?,?,?,?,?,0) ON CONFLICT DO NOTHING",cid,ownPost,pilot,"@1234 this direction is worth keeping.",at(5*60000));
                    notification(member,pilot,"MENTION","uxpilot mentioned you",ownPost);
                }
                // Exact original OHLC samples, persisted with explicit imported provenance. Live prices keep using actual activity.
                for(JsonNode m:fixture.path("markets")) {
                    int index=0;
                    for(JsonNode c:m.path("candles")) {
                        Instant bucket=Instant.ofEpochSecond(anchor.minusSeconds((6-index++)*3600).getEpochSecond()/300*300);
                        exec("INSERT INTO market_candles(forum_key,bucket,open,high,low,close,source) VALUES(?,?,?,?,?,?, 'IMPORTED_DEMO') ON CONFLICT DO NOTHING",m.path("forum").asText(),Timestamp.from(bucket),c.get(0).asInt(),c.get(1).asInt(),c.get(2).asInt(),c.get(3).asInt());
                    }
                }
                for(JsonNode trader:fixture.path("leaderboard")) {
                    String name=trader.path("username").asText(),forum=trader.path("forum").asText();
                    UUID id=createUser(name,name);
                    // Never replace an existing participant's wallet or trade history.
                    if(scalar("SELECT count(*) FROM market_wallets WHERE user_id=?",id)>0) continue;
                    exec("INSERT INTO market_wallets(user_id,cash,credits,reset_date) VALUES(?,1000,1000,?)",id,LocalDate.now(ZoneOffset.UTC));
                    long cash=1000;
                    int days=trader.path("activeDays").asInt();
                    for(int day=days;day>=1;day--) {
                        Timestamp time=Timestamp.from(anchor.minusSeconds(day*86400L));
                        int sell=100+(day==days?trader.path("assets").asInt()-1000:0);
                        trade(id,forum,"BUY",100,cash,cash-100,time);cash-=100;
                        trade(id,forum,"SELL",sell,cash,cash+sell,Timestamp.from(time.toInstant().plusSeconds(60)));cash+=sell;
                    }
                    exec("UPDATE market_wallets SET cash=? WHERE user_id=?",cash,id);
                }
                var summary=JSON.createObjectNode().put("originalPosts",originals).put("originalComments",comments).put("votingAccounts",readers.size()).put("namedTraders",fixture.path("leaderboard").size()).put("importedCandles",24).put("profileAliases","campusviewer -> 1234; modmentor -> 12345").put("source","Imported interview scenario, not organic users or financial performance");
                exec("INSERT INTO demo_data_batches(batch_key,summary) VALUES(?,?::jsonb)",BATCH,summary.toString());
                if(apply) {
                    Files.createDirectories(secrets.getParent());
                    if(!Files.exists(secrets)) Files.createFile(secrets,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                    Files.setPosixFilePermissions(secrets,PosixFilePermissions.fromString("rw-------"));
                    Files.writeString(secrets,credentials.toPrettyString());
                    db.commit();
                } else db.rollback();
                summary.put("applied",apply); summary.set("writes",JSON.valueToTree(writes));System.out.println(summary);
            } catch(Exception error) {db.rollback();throw error;}
        }
    }
    static String required(String k){String v=System.getenv(k);if(v==null||v.isBlank()) throw new IllegalArgumentException("Missing "+k);return v;}
    static UUID stable(String k){return UUID.nameUUIDFromBytes(("de:complete-demo:"+k).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static UUID remote(String kind,String local){return UUID.fromString(state.path(kind).path(local).asText());}
    static Timestamp at(long age){return Timestamp.from(anchor.minusMillis(age));}
    static UUID findLocal(String name){for(JsonNode u:fixture.path("users")) if(u.path("username").asText().equals(name)) return accounts.get(u.path("localId").asText());throw new IllegalStateException(name);}
    static UUID userId(String name) throws Exception {try(var p=db.prepareStatement("SELECT id FROM users WHERE username=?")){p.setString(1,name);try(var rs=p.executeQuery()){if(!rs.next())throw new IllegalStateException("Missing account "+name);return rs.getObject(1,UUID.class);}}}
    static UUID createUser(String name,String display) throws Exception {
        if(scalar("SELECT count(*) FROM users WHERE username=?",name)>0) {
            UUID id=userId(name);if(!id.equals(stable(name)))throw new IllegalStateException("Unrelated existing account "+name);return id;
        }
        String password=credentials.path(name).asText();if(password.isBlank()){byte[] entropy=new byte[32];new java.security.SecureRandom().nextBytes(entropy);password=Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);credentials.put(name,password);}
        UUID id=stable(name);exec("INSERT INTO users(id,username,password_hash,role,status,display_name) VALUES(?,?,?,'MEMBER','ACTIVE',?)",id,name,HASH.encode(password),display);return id;
    }
    static void move(String table,UUID content,UUID before,UUID after) throws Exception {
        if(scalar("SELECT count(*) FROM "+table+" WHERE id=? AND author_id IN (?,?)",content,before,after)!=1)throw new IllegalStateException("Unexpected imported author");
        exec("UPDATE "+table+" SET author_id=? WHERE id=? AND author_id=?",after,content,before);
    }
    static void votes(String table,String column,UUID id,int score,List<UUID> users,Timestamp at) throws Exception {
        for(int i=0;i<Math.abs(score);i++) exec("INSERT INTO "+table+"(user_id,"+column+",value,created_at) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",users.get(i),id,score>0?1:-1,at);
    }
    static void follow(UUID from,UUID to) throws Exception {if(!from.equals(to))exec("INSERT INTO user_follows(follower_id,followed_id) VALUES(?,?) ON CONFLICT DO NOTHING",from,to);}
    static void bookmark(UUID u,UUID p) throws Exception {exec("INSERT INTO post_bookmarks(user_id,post_id) VALUES(?,?) ON CONFLICT DO NOTHING",u,p);}
    static void notification(UUID u,UUID actor,String type,String title,UUID post) throws Exception {
        exec("INSERT INTO notifications(id,user_id,type,title,body,reference_type,reference_id,created_at) VALUES(?,?,?,?,?,'POST',?,?) ON CONFLICT DO NOTHING",stable(type+u+actor+post),u,type,title,title,post,at(5*60000));
    }
    static void trade(UUID u,String forum,String action,int price,long before,long after,Timestamp time) throws Exception {
        UUID request=stable(u+forum+action+time.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        exec("INSERT INTO market_trades(id,user_id,request_id,forum_key,action,units,price,expected_price,cash_before,cash_after,amount,created_at,source) VALUES(?,?,?,?,?,1,?,?,?,?,?,?,'IMPORTED_DEMO')",stable("trade"+request),u,request,forum,action,price,price,before,after,price,time);
    }
    static long scalar(String sql,Object...values) throws Exception {try(var p=db.prepareStatement(sql)){bind(p,values);try(var rs=p.executeQuery()){rs.next();return rs.getLong(1);}}}
    static void bind(PreparedStatement p,Object[] values)throws Exception {for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);}
    static void exec(String sql,Object...values)throws Exception {try(var p=db.prepareStatement(sql)){bind(p,values);boolean result=p.execute();if(!result)writes.merge(sql.split(" ")[0],Math.max(0,p.getUpdateCount()),Integer::sum);}}
}
