package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class ImageAttachmentViewer {
    private ImageAttachmentViewer() {
    }

    static void show(Context context, Uri imageUri, int titleResId) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_avatar_picker_dialog);

        TextView title = new TextView(context);
        title.setText(titleResId);
        title.setTextColor(ContextCompat.getColor(context, R.color.ink_primary));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 12));
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ImageView preview = new ImageView(context);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        RemoteImageLoader.display(preview, imageUri);
        int padding = dp(context, 12);
        preview.setBackgroundColor(Color.TRANSPARENT);
        preview.setPadding(0, padding, 0, padding);
        content.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(content)
                .create();

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setGravity(android.view.Gravity.END);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 18));

        TextView cancelButton = makeDialogButton(context, R.string.action_cancel);
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        TextView saveButton = makeDialogButton(context, R.string.action_save_image);
        saveButton.setOnClickListener(v -> {
            save(context, imageUri);
            dialog.dismiss();
        });
        buttonRow.addView(cancelButton);
        buttonRow.addView(saveButton);
        content.addView(buttonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static TextView makeDialogButton(Context context, int textResId) {
        TextView button = new TextView(context);
        button.setText(textResId);
        button.setTextColor(ContextCompat.getColor(context, R.color.ink_primary));
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void save(Context context, Uri sourceUri) {
        String mimeType = context.getContentResolver().getType(sourceUri);
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }

        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension == null || extension.isEmpty()) {
            extension = "jpg";
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "campus_image_" + System.currentTimeMillis() + "." + extension);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CampusModeration");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri destinationUri = null;
        try {
            destinationUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (destinationUri == null) {
                throw new IOException("Could not create image destination.");
            }

            try (InputStream input = openSource(context, sourceUri);
                 OutputStream output = context.getContentResolver().openOutputStream(destinationUri)) {
                if (input == null || output == null) {
                    throw new IOException("Could not open image streams.");
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues completedValues = new ContentValues();
                completedValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                context.getContentResolver().update(destinationUri, completedValues, null, null);
            }
            Toast.makeText(context, R.string.toast_image_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException exception) {
            if (destinationUri != null) {
                context.getContentResolver().delete(destinationUri, null, null);
            }
            Toast.makeText(context, R.string.toast_image_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static InputStream openSource(Context context, Uri uri) throws IOException {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return new java.net.URL(uri.toString()).openStream();
        }
        return context.getContentResolver().openInputStream(uri);
    }
}
