package com.example.myapplication;

import android.os.Handler;
import android.os.Looper;
import backend.BackendAdminGateway;
import backend.BackendException;
import backend.BackendModerationGateway;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** One bounded page; translations must match the report-time preview, never live post IDs. */
final class AdminEvidenceTranslations {
    private final BackendAdminGateway admin;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, JSONObject> cache = new HashMap<>();
    private long generation;
    private boolean failed;
    AdminEvidenceTranslations(BackendAdminGateway admin) { this.admin=admin; }
    JSONObject get(JSONObject item) {
        JSONObject translated=cache.get(item.optString("id"));
        return translated!=null && AdminQueueContent.matchesTranslation(item,translated) ? translated : null;
    }
    boolean failed() { return failed; }
    void pause() { generation++;main.removeCallbacksAndMessages(null); }
    void show(JSONArray items, Runnable refresh) {
        pause(); failed=false;
        request(items,refresh,generation,0);
    }
    private void request(JSONArray items, Runnable refresh, long ticket, int attempt) {
        if(ticket!=generation)return;
        JSONArray ids=new JSONArray();
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i);
            if(item!=null&&get(item)==null)ids.put(item.optString("id"));
        }
        if(ids.length()==0)return;
        admin.translations(ids,new BackendModerationGateway.Callback<>() {
            public void onSuccess(JSONObject result) {
                if(ticket!=generation)return;
                JSONArray rows=result.optJSONArray("cases");
                if(rows!=null)for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.optJSONObject(i);
                    if(row!=null)cache.put(row.optString("id"),row);
                }
                boolean pending=result.optBoolean("pending");
                failed=!pending; // Missing/unsupported evidence must not look perpetually busy.
                refresh.run();
                if(pending&&attempt<6)main.postDelayed(()->request(items,refresh,ticket,attempt+1),5000);
                else if(pending){failed=true;refresh.run();}
            }
            public void onError(BackendException error) {
                if(ticket!=generation)return;
                failed=true;refresh.run();
                if(error.status()!=404&&error.status()!=401&&error.status()!=403&&attempt<6)
                    main.postDelayed(()->request(items,refresh,ticket,attempt+1),error.status()==429?60000:5000);
            }
        });
    }
}
