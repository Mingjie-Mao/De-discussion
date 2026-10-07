package com.example.myapplication;

import backend.BackendForumGateway;
import dao.model.Post;
import org.junit.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

public class RemoteForumCacheTest {
    @Test public void serverCategoriesSurviveChineseAndEnglishClientProjection() {
        java.util.Locale before=java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE);
            var p=AppData.upsertRemotePost(new BackendForumGateway.PostSnapshot(UUID.randomUUID(),UUID.randomUUID(),"Author","anu","Title","Body",null,1,"Social",null),"https://example.test");
            assertEquals("社交",AppData.getPostCategory(null,p));
            assertEquals("Social",AppData.canonicalPostCategory(AppData.getPostCategory(null,p)));
            java.util.Locale.setDefault(java.util.Locale.ENGLISH);
            var other=AppData.upsertRemotePost(new BackendForumGateway.PostSnapshot(UUID.randomUUID(),UUID.randomUUID(),"Author","anu","Title","Body",null,1,"社交",null),"https://example.test");
            assertEquals("Social",AppData.getPostCategory(null,other));
            AppData.setPostCategory(other,"OWeek"); assertEquals("OWeek",AppData.getPostCategory(null,other));
        } finally {java.util.Locale.setDefault(before);}
    }

    @Test public void refreshedThreadRemovesUnavailableCommentsAndKeepsLaterPagesAppendOnly() {
        UUID author = UUID.randomUUID(), id = UUID.randomUUID(), first = UUID.randomUUID(), later = UUID.randomUUID();
        Post post = AppData.upsertRemotePost(new BackendForumGateway.PostSnapshot(id, author, "Original author", "anu", "Title", "Body", null, 1), "https://example.test");
        var comment = new BackendForumGateway.CommentSnapshot(first, null, author, "Original author", "First comment", null, 2);
        var next = new BackendForumGateway.CommentSnapshot(later, null, author, "Original author", "Later page", null, 3);
        AppData.replaceRemoteComments(id, List.of(comment), "https://example.test");
        AppData.synchronizeComments(id, List.of(next), "https://example.test");
        assertEquals(2, AppData.getMessagesByUser(author).size());
        assertEquals("Original author", AppData.getUsername(author));
        AppData.replaceRemoteComments(id, List.of(), "https://example.test");
        assertTrue(AppData.getMessagesByUser(author).isEmpty());
        assertTrue(AppData.getMessages(post).stream().noneMatch(m -> first.equals(m.id()) || later.equals(m.id())));
        AppData.synchronizeComments(id, List.of(next), "https://example.test");
        assertEquals(1, AppData.getMessagesByUser(author).size());
        assertEquals(later, AppData.getMessagesByUser(author).get(0).id());
    }
}
