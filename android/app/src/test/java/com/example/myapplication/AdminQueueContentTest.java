package com.example.myapplication;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdminQueueContentTest {
    @Test public void modelFormattingIsRemovedOnlyWhenAbsentFromReportedSource() throws Exception {
        var item = new JSONObject().put("sourceTitle", "One UI person")
                .put("sourcePreview", "Original plain comment")
                .put("title", "一名 UI </i>").put("body", "<b>中文评论</b>");
        assertEquals("一名 UI", AdminQueueContent.translatedTitle(item));
        assertEquals("中文评论", AdminQueueContent.translatedPreview(item));
        // The evidence can itself discuss literal HTML; do not rewrite that text.
        item.put("sourcePreview", "Use <b>text</b> in HTML.");
        assertEquals("<b>中文评论</b>", AdminQueueContent.translatedPreview(item));
        assertEquals("One UI person", item.getString("sourceTitle"));
        assertEquals("一名 UI </i>", item.getString("title"));
    }

    @Test public void translationMustMatchTheCaseAndReportedPreview() throws Exception {
        var original=new JSONObject().put("id","case").put("contentTitle","Reported").put("contentPreview","Original body");
        var translated=new JSONObject().put("id","case").put("sourceTitle","Reported").put("sourcePreview","Original body")
                .put("title","举报标题").put("body","举报正文");
        assertTrue(AdminQueueContent.matchesTranslation(original,translated));
        translated.put("sourcePreview","Edited body");assertFalse(AdminQueueContent.matchesTranslation(original,translated));
        translated.put("sourcePreview","Original body").put("id","other-case");assertFalse(AdminQueueContent.matchesTranslation(original,translated));
    }
    @Test public void missingPreviewNeverFallsBackToIdentifiersOrModelAdvice() throws Exception {
        JSONObject item = new JSONObject().put("id", "case-uuid").put("engine", "Gemini")
                .put("recommendedDecision", "ALLOW").put("contentTitle", JSONObject.NULL);
        assertEquals("", AdminQueueContent.title(item));
        assertEquals("", AdminQueueContent.preview(item));
    }

    @Test public void contentIsBoundedWithoutSplittingEmoji() throws Exception {
        JSONObject item = new JSONObject().put("contentTitle", "  Reported\n title ")
                .put("contentPreview", "😀".repeat(181));
        assertEquals("Reported title", AdminQueueContent.title(item));
        assertEquals("😀".repeat(180) + "…", AdminQueueContent.preview(item));
    }
}
