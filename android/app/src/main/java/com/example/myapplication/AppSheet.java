package com.example.myapplication;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The app's own dialog surface: a white rounded card, a bold title with an
 * optional count, a round close button, and rows on a tinted fill.
 *
 * <p>Extracted from the profile sheets so that every dialog in the app is built
 * from the same pieces. The Material defaults are a different design language —
 * grey-blue list rows, small caps buttons — and using them for some dialogs and
 * this for others made the app look like two apps.
 */
final class AppSheet {

    interface Option {
        CharSequence label();

        boolean selected();
    }

    private AppSheet() {
    }

    /**
     * The card itself. Callers add their own content and pass it to
     * {@link #show}; {@code onClose} is what the corner button does.
     */
    static LinearLayout sheet(
            Context context, CharSequence title, @Nullable Integer count, Runnable onClose) {

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setClipToOutline(true);
        shell.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 18));
        shell.setBackground(roundRect(context, R.color.surface, R.color.surface_border, 28, 1));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.HORIZONTAL);
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleBlock, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ink_primary));
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setIncludeFontPadding(false);
        titleBlock.addView(titleView);

        if (count != null) {
            TextView badge = new TextView(context);
            badge.setText(String.valueOf(count));
            badge.setTextColor(ContextCompat.getColor(context, R.color.ink_secondary));
            badge.setTextSize(13);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(dp(context, 34));
            badge.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
            badge.setBackground(roundRect(context, R.color.surface_alt, R.color.surface_border, 999, 1));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.setMargins(dp(context, 10), 0, 0, 0);
            titleBlock.addView(badge, badgeParams);
        }

        ImageButton close = new ImageButton(context);
        close.setImageResource(R.drawable.ic_close_24);
        close.setColorFilter(ContextCompat.getColor(context, R.color.ink_primary));
        close.setBackground(roundRect(context, R.color.surface_alt, R.color.surface_border, 999, 1));
        close.setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9));
        close.setContentDescription(context.getString(R.string.action_close));
        close.setOnClickListener(v -> onClose.run());
        header.addView(close, new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)));

        return shell;
    }

    /**
     * A choice in a list of choices. The selected one is outlined and carries a
     * tick, which is legible without relying on the fill alone — the difference
     * between the two fills is small in dark mode.
     */
    static View optionRow(
            Context context,
            @DrawableRes int iconResId,
            CharSequence label,
            boolean selected,
            Runnable onClick) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));
        // Selection is a deeper fill and a tick. No outline: a heavy border
        // changes the row's apparent size, so the list appeared to shift as the
        // selection moved down it.
        row.setBackground(roundRect(
                context,
                selected ? R.color.option_selected_fill : R.color.surface_alt,
                R.color.surface_border,
                20,
                1));
        row.setOnClickListener(v -> onClick.run());

        int foreground = selected ? R.color.ink_primary : R.color.ink_secondary;

        if (iconResId != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconResId);
            icon.setColorFilter(ContextCompat.getColor(context, foreground));
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22));
            iconParams.setMargins(0, 0, dp(context, 12), 0);
            row.addView(icon, iconParams);
        }

        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(ContextCompat.getColor(context, R.color.ink_primary));
        text.setTextSize(17);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setIncludeFontPadding(false);
        row.addView(text, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (selected) {
            ImageView tick = new ImageView(context);
            tick.setImageResource(R.drawable.ic_check_24);
            tick.setColorFilter(ContextCompat.getColor(context, R.color.ink_primary));
            row.addView(tick, new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)));
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(context, 10), 0, 0);
        row.setLayoutParams(rowParams);
        return row;
    }

    /** A caption under a group of rows, for the things a row cannot say itself. */
    static TextView note(Context context, CharSequence text) {
        TextView note = new TextView(context);
        note.setText(text);
        note.setTextColor(ContextCompat.getColor(context, R.color.ink_secondary));
        note.setTextSize(13);
        note.setLineSpacing(dp(context, 3), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, 2), dp(context, 12), dp(context, 2), 0);
        note.setLayoutParams(params);
        return note;
    }

    /**
     * The action row at the bottom of a sheet. Laid out here rather than through
     * the dialog's own buttons, which would render in the Material style this
     * class exists to avoid.
     */
    static LinearLayout actions(Context context) {
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 16), 0, 0);
        actions.setLayoutParams(params);
        return actions;
    }

    static TextView button(Context context, CharSequence label, boolean primary, Runnable onClick) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(context, 22), dp(context, 13), dp(context, 22), dp(context, 13));
        // Emphasis by contrast rather than by hue: the primary action is ink on
        // the page, the secondary is ink on the tinted fill.
        button.setTextColor(ContextCompat.getColor(
                context, primary ? R.color.surface : R.color.ink_primary));
        button.setBackground(roundRect(
                context,
                primary ? R.color.ink_primary : R.color.surface_alt,
                primary ? R.color.ink_primary : R.color.surface_border,
                999,
                1));
        button.setOnClickListener(v -> onClick.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, 10), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    static AlertDialog show(Context context, View content) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                context, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(content)
                .create();
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            // The card draws its own background, so the dialog's own one has to
            // go or its corners show through the rounded ones.
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.62f);
            int width = context.getResources().getDisplayMetrics().widthPixels - dp(context, 48);
            window.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    static GradientDrawable roundRect(
            Context context,
            @ColorRes int fillResId,
            @ColorRes int strokeResId,
            int radiusDp,
            int strokeWidthDp) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setColor(ContextCompat.getColor(context, fillResId));
        if (strokeResId != 0 && strokeWidthDp > 0) {
            drawable.setStroke(dp(context, strokeWidthDp), ContextCompat.getColor(context, strokeResId));
        }
        return drawable;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
