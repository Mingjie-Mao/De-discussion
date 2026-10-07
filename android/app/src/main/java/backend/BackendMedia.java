package backend;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class BackendMedia {
    private BackendMedia() {}

    /** Avatars do not need a camera-sized image on every post and comment. */
    public static BackendForumGateway.MediaUpload readAvatar(Context context, Uri uri) throws IOException {
        var source = read(context, uri);
        if (source == null) throw new IOException("Choose an image.");
        var bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(source.bytes(), 0, source.bytes().length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Choose a readable image.");
        bounds.inSampleSize = 1;
        while (bounds.outWidth / bounds.inSampleSize > 512 || bounds.outHeight / bounds.inSampleSize > 512) bounds.inSampleSize *= 2;
        bounds.inJustDecodeBounds = false;
        var image = android.graphics.BitmapFactory.decodeByteArray(source.bytes(), 0, source.bytes().length, bounds);
        if (image == null) throw new IOException("Choose a readable image.");
        try (var out = new ByteArrayOutputStream()) {
            boolean alpha = image.hasAlpha();
            if (!image.compress(alpha ? android.graphics.Bitmap.CompressFormat.PNG : android.graphics.Bitmap.CompressFormat.JPEG, 85, out))
                throw new IOException("Could not prepare the avatar.");
            return new BackendForumGateway.MediaUpload(alpha ? "avatar.png" : "avatar.jpg", alpha ? "image/png" : "image/jpeg", out.toByteArray());
        } finally { image.recycle(); }
    }

    public static BackendForumGateway.MediaUpload read(Context context, Uri uri) throws IOException {
        if (uri == null) return null;
        String type = context.getContentResolver().getType(uri);
        if (type == null || type.isBlank()) type = "image/jpeg";
        String name = "attachment";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && cursor.getString(column) != null) name = cursor.getString(column);
            }
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("The selected attachment is unavailable.");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 8 * 1024 * 1024) throw new IOException("Image exceeds 8 MiB.");
                output.write(buffer, 0, count);
            }
            return new BackendForumGateway.MediaUpload(name, type, output.toByteArray());
        }
    }
}
