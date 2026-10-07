package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import java.lang.ref.WeakReference;

/** Reuse bounded image downloads, with one weak proxy tied to each visible avatar. */
final class AvatarRenderer {
    private AvatarRenderer() {}
    static void display(TextView view, String url, String letter, int color) {
        Proxy proxy = view.getTag(R.id.avatar_loader) instanceof Proxy ? (Proxy)view.getTag(R.id.avatar_loader) : null;
        if (url != null && proxy != null && url.equals(proxy.getTag()) && proxy.bitmap != null) { proxy.apply(proxy.bitmap); return; }
        GradientDrawable background = new GradientDrawable(); background.setShape(GradientDrawable.OVAL); background.setColor(color);
        view.setBackground(background); view.setText(letter);
        if (proxy == null) { proxy = new Proxy(view); view.setTag(R.id.avatar_loader, proxy); }
        RemoteImageLoader.display(proxy, url == null ? null : Uri.parse(url));
    }
    private static final class Proxy extends AppCompatImageView {
        private final WeakReference<TextView> target;
        private Bitmap bitmap;
        Proxy(TextView view) { super(view.getContext().getApplicationContext()); target = new WeakReference<>(view); }
        @Override public void setImageBitmap(Bitmap value) { super.setImageBitmap(value); bitmap = value; apply(value); }
        @Override public void setImageDrawable(android.graphics.drawable.Drawable value) {
            super.setImageDrawable(value);
            if (value == null) bitmap = null;
        }
        void apply(Bitmap value) {
            TextView view = target.get();
            if (value == null || view == null || view.getTag(R.id.avatar_loader) != this) return;
            var drawable = RoundedBitmapDrawableFactory.create(view.getResources(), value); drawable.setCircular(true);
            view.setBackground(drawable); view.setText("");
        }
    }
}
