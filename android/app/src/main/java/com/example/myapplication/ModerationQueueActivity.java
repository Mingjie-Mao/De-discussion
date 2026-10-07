package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Compatibility entry point for navigation from older screens. */
public final class ModerationQueueActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, AdminReviewActivity.class).putExtra("queue", 0));
        finish();
    }
}
