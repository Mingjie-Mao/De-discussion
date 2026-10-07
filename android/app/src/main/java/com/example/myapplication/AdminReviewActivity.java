package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import backend.BackendAdminApi;

/** Bounded server queues shared with the browser console. */
public final class AdminReviewActivity extends AdminScreenActivity {
    private int selection;
    private int page;
    private boolean loading;
    private boolean hasNext;
    private AdminEvidenceTranslations translations;
    private final java.util.List<Runnable> translatedRows=new java.util.ArrayList<>();
    private static final String[] STATUSES = {"AWAITING_REVIEW", "RESOLVED", "QUEUED", "ANALYSING", "PENDING", "UPHELD", "OVERTURNED"};
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!requireAdmin()) return;
        translations=new AdminEvidenceTranslations(admin);
        selection = saved == null ? getIntent().getIntExtra("queue", 0) : saved.getInt("queue", 0);
        page = saved == null ? 0 : saved.getInt("page", 0);
        if (selection < 0 || selection >= STATUSES.length) selection = 0;
        screen(R.string.admin_workspace);
        Spinner filter = new Spinner(this);
        ArrayAdapter<CharSequence> choices = ArrayAdapter.createFromResource(this, R.array.admin_queue_filters, android.R.layout.simple_spinner_item);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filter.setAdapter(choices);
        filter.setSelection(selection);
        ((android.widget.LinearLayout) status.getParent()).addView(filter, 3);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selection != position) { selection = position; page = 0; load(); }
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    @Override protected void onResume() { super.onResume(); if (requireAdmin() && content != null) load(); }
    private void load() {
        if (!requireAdmin()) return;
        loading = true;
        long request = ++revision;
        status.setText(R.string.admin_loading);
        content.removeAllViews();
        translatedRows.clear(); translations.pause();
        action(R.string.admin_refresh, () -> { if (!loading) load(); });
        if (selection < 4) admin.cases(STATUSES[selection], page, callback(request, this::render));
        else admin.appeals(STATUSES[selection], page, callback(request, this::render));
    }
    private void render(JSONArray items) {
        loading = false;
        hasNext = items.length() == BackendAdminApi.PAGE_SIZE;
        status.setText(getString(R.string.admin_page, page + 1, items.length()));
        if (items.length() == 0) content.addView(text(getString(R.string.admin_empty), 16));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String id = value(item, "id");
            boolean appeal = selection >= 4;
            Runnable open = () -> {
                Intent intent = new Intent(this, appeal ? AdminAppealActivity.class : AdminCaseActivity.class).putExtra("id", id);
                if (appeal) intent.putExtra("appeal", item.toString());
                startActivity(intent);
            };
            content.addView(caseCard(item, appeal, open));
        }
        View previous = action(R.string.admin_previous, () -> { if (!loading && page > 0) { page--; load(); } });
        previous.setEnabled(page > 0);
        View next = action(R.string.admin_next, () -> { if (!loading && hasNext) { page++; load(); } });
        next.setEnabled(hasNext);
        if(selection<4 && "zh-CN".equals(UiPreferences.getLanguageTag(this)))
            translations.show(items,()->{if(!isFinishing()&&!isDestroyed())translatedRows.forEach(Runnable::run);});
    }

    private View caseCard(JSONObject item, boolean appeal, Runnable open) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = AppSheet.dp(this, 16);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(AppSheet.roundRect(this, R.color.surface_alt, R.color.surface_border, 20, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, AppSheet.dp(this, 10), 0, 0);
        card.setLayoutParams(params);
        String type = getString(appeal ? R.string.admin_appeal_type
                : "COMMENT".equals(value(item, "targetType")) ? R.string.admin_comment_type : R.string.admin_post_type);
        String state = ModerationLabels.status(this, value(item, "status"));
        card.addView(cardText(type + " · " + state, 14, false));
        String title = AdminQueueContent.title(item);
        String body = appeal ? value(item, "reason") : AdminQueueContent.preview(item);
        TextView titleView=cardText(title,19,true);
        if (!title.isEmpty() && !appeal) card.addView(titleView);
        if (body.isEmpty() && title.isEmpty()) body = getString(item.optBoolean("hasAttachment")
                ? R.string.admin_image_content : R.string.admin_missing_preview);
        TextView bodyView=cardText(body,16,false);
        if (!body.isEmpty()) card.addView(bodyView);
        String meta = appeal ? value(item, "createdAt")
                : item.optInt("reportCount") + " " + getString(R.string.admin_reports);
        String outcome = ModerationLabels.action(this, value(item, "finalAction"));
        if (!outcome.isEmpty()) meta += " · " + outcome;
        card.addView(cardText(meta + " · " + getString(R.string.admin_view_content), 13, false));
        card.setOnClickListener(v -> open.run());
        card.setFocusable(true);
        card.setContentDescription(type + ", " + state + ", " + title + ", " + body);
        if(!appeal && "zh-CN".equals(UiPreferences.getLanguageTag(this)) && (!title.isEmpty()||!AdminQueueContent.preview(item).isEmpty())) {
            boolean[] original={false};
            TextView hint=cardText(getString(R.string.admin_translation_loading),12,false);
            android.widget.Button toggle=new android.widget.Button(this);
            toggle.setText(R.string.translation_show_original);toggle.setVisibility(View.GONE);
            card.addView(hint);card.addView(toggle);
            String originalBody=body;
            Runnable update=()->{
                JSONObject translated=translations.get(item);
                boolean ready=translated!=null;
                String displayTitle=ready&&!original[0]?AdminQueueContent.translatedTitle(translated):title;
                String displayBody=ready&&!original[0]?AdminQueueContent.translatedPreview(translated):originalBody;
                titleView.setText(displayTitle);bodyView.setText(displayBody);
                hint.setText(ready?R.string.admin_translation_note:translations.failed()?R.string.admin_translation_failed:R.string.admin_translation_loading);
                toggle.setVisibility(ready?View.VISIBLE:View.GONE);
                toggle.setText(original[0]?R.string.translation_show_translated:R.string.translation_show_original);
                card.setContentDescription(type+", "+state+", "+displayTitle+", "+displayBody);
            };
            toggle.setOnClickListener(v->{original[0]=!original[0];update.run();});
            translatedRows.add(update);update.run();
        }
        return card;
    }

    private TextView cardText(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(this, bold ? R.color.ink_primary : R.color.ink_secondary));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, AppSheet.dp(this, 8));
        return view;
    }
    @Override protected void onRequestFailed(backend.BackendException error) {
        loading = false;
        action(R.string.admin_refresh, this::load);
    }
    @Override protected void onStop() { if(translations!=null)translations.pause();super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("queue", selection); state.putInt("page", page); super.onSaveInstanceState(state);
    }
}
