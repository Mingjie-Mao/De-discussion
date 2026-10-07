import com.example.myapplication.AppData;
import com.example.myapplication.R;
import dao.PostDAO;
import dao.UserDAO;
import dao.model.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class LegacyDataExport {
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        AppData.ensurePopulated();
        var forum = AppData.class.getDeclaredMethod("getForumKey", Post.class);
        var parent = AppData.class.getDeclaredMethod("getParentId", Message.class);
        forum.setAccessible(true); parent.setAccessible(true);
        JSONArray users = new JSONArray(), posts = new JSONArray();
        for (var it = UserDAO.getInstance().getAll(); it.hasNext();) {
            User u = it.next();
            users.put(new JSONObject().put("localId", u.id()).put("username", u.username())
                .put("displayName", u.username().equals("campusviewer") ? "Campus Buddy" : u.username()));
        }
        int commentCount = 0;
        long exportedAt = System.currentTimeMillis();
        var categories = (Map<UUID,String>) field("POST_CATEGORIES");
        var ranks = (Map<UUID,Integer>) field("POST_TOP_RANKS");
        for (var it = PostDAO.getInstance().getAll(); it.hasNext();) {
            Post p = it.next(); Message root = AppData.getRootMessage(p);
            JSONArray comments = new JSONArray();
            List<Message> ordered = new ArrayList<>();
            for (var msgs = p.messages.getAll(); msgs.hasNext();) { Message m = msgs.next(); if (!m.id().equals(root.id())) ordered.add(m); }
            ordered.sort(Comparator.comparingLong(Message::timestamp));
            for (Message m : ordered) {
                UUID parentId = (UUID) parent.invoke(null, m);
                if (root.id().equals(parentId)) parentId = null;
                comments.put(new JSONObject().put("localId", m.id()).put("author", m.poster())
                    .put("parent", parentId == null ? JSONObject.NULL : parentId).put("body", m.message())
                    .put("image", image(AppData.getMessageImageUri(m)))
                    .put("score", AppData.getMessageVoteScore(m)).put("ageMs", exportedAt-m.timestamp()));
                commentCount++;
            }
            posts.put(new JSONObject().put("localId", p.id).put("author", p.poster)
                .put("forum", forum.invoke(null, p)).put("title", AppData.getPostTitle(p)).put("body", AppData.getPostBody(p))
                .put("image", image(AppData.getPostImageUri(p))).put("comments", comments)
                .put("score", AppData.getPostVoteScore(p)).put("category", categories.getOrDefault(p.id,"Study"))
                .put("pinRank", ranks.containsKey(p.id) ? ranks.get(p.id) : JSONObject.NULL)
                .put("ageMs", exportedAt-root.timestamp()));
        }
        JSONArray follows = new JSONArray(), bookmarks = new JSONArray();
        for (UUID id : (Set<UUID>) field("FOLLOWED_USERS")) follows.put(id.toString());
        for (UUID id : (Set<UUID>) field("BOOKMARKED_POSTS")) bookmarks.put(id.toString());
        Files.writeString(Path.of(args[0]), new JSONObject().put("users", users).put("posts", posts)
            .put("following",follows).put("bookmarks",bookmarks).toString(2));
        System.out.println("Exported users=" + users.length() + " posts=" + posts.length() + " comments=" + commentCount);
    }
    static Object field(String name) throws Exception {
        var f=AppData.class.getDeclaredField(name); f.setAccessible(true); return f.get(null);
    }
    static Object image(String uri) {
        if (uri == null) return JSONObject.NULL;
        if (uri.endsWith("/" + R.drawable.avatar_anu)) return "avatar_anu.jpg";
        if (uri.endsWith("/" + R.drawable.avatar_unsw)) return "avatar_unsw.jpg";
        if (uri.endsWith("/" + R.drawable.avatar_usyd)) return "avatar_usyd.jpg";
        if (uri.endsWith("/" + R.drawable.avatar_um)) return "avatar_um.jpg";
        return JSONObject.NULL;
    }
}
