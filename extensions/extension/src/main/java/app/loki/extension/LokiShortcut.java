/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.loki.extension;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.Collections;

/**
 * The "Lynx Patches" entry on the launcher's long-press menu.
 *
 * <p>It is added next to the app's own shortcuts and never replaces them: the app keeps the shortcuts it publishes
 * itself (Amazon's search, orders and cart, for one), and no other id is ever removed. The app can replace its
 * dynamic shortcuts at any time, which removes ours, so the shortcut is also put back a few seconds after the
 * app comes to the front, once the app has had its turn. Apps with no menu of their own (Amazon) depend on it.
 */
final class LokiShortcut {
    /** Keep the id stable: publishing again updates the installed shortcut instead of adding a second one. */
    static final String ID = "loki-patches";

    /** After the app has published its own shortcuts, which it does while it starts and when it comes to the front. */
    private static final long RESUME_DELAY_MS = 3000;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static boolean started;

    private LokiShortcut() {
    }

    /** Called once from the menu provider, which starts with the app. */
    static synchronized void start(Context context) {
        if (context == null || started) return;
        started = true;
        // LinkedIn has a shortcut of its own for the same screen (SettingsShortcut, id loki_settings) that it
        // publishes and repairs by itself; a second entry would only be a duplicate.
        if (context.getPackageName().startsWith("com.linkedin.android")) return;

        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        // A provider is created while the app is still in the background, and Android drops or throttles
        // dynamic shortcuts pushed from a background app: the call returns without error and nothing is
        // stored. Publish again a few seconds later, by which time the launcher activity is on screen.
        HANDLER.postDelayed(() -> publish(app), 5000);
        HANDLER.postDelayed(() -> publish(app), 20000);

        if (!(app instanceof Application)) return;
        // Any activity of the app, not only the launcher one: Amazon's home screen is one of several web activities
        // and the launcher entry can be an alias. The check is cheap and does nothing while the shortcut is there.
        final Runnable restore = () -> publishIfMissing(app);
        ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                // Debounced: a burst of resumes (activity changes) leads to one check, after the delay.
                HANDLER.removeCallbacks(restore);
                HANDLER.postDelayed(restore, RESUME_DELAY_MS);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle state) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    /** Publishes the shortcut again only if the app has replaced its shortcuts without it. */
    private static void publishIfMissing(Context context) {
        try {
            if (Build.VERSION.SDK_INT < 25) return;
            ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
            if (shortcuts == null) return;
            for (ShortcutInfo existing : shortcuts.getDynamicShortcuts()) {
                if (ID.equals(existing.getId())) return;
            }
            publish(context);
        } catch (Throwable error) {
            android.util.Log.e("Loki", "shortcut: check failed", error);
        }
    }

    /**
     * Adds the shortcut at the top, or moves the existing one back there. API 30 and up push it, which shifts the
     * app's own shortcuts down by one; older versions add it. Neither removes anything.
     */
    private static void publish(Context context) {
        try {
            if (Build.VERSION.SDK_INT < 25) return;
            ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
            if (shortcuts == null) return;
            Intent open = new Intent(context, MenuActivity.class).setAction(Intent.ACTION_VIEW);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, ID)
                    .setShortLabel(MenuInfo.BRAND)
                    .setLongLabel(MenuInfo.BRAND)
                    .setIcon(brandIcon(context))
                    .setIntent(open)
                    .setRank(0)
                    .build();
            if (Build.VERSION.SDK_INT >= 30) {
                shortcuts.pushDynamicShortcut(shortcut);
            } else {
                shortcuts.addDynamicShortcuts(Collections.singletonList(shortcut));
            }
            android.util.Log.i("Loki", "shortcut: published, dynamic="
                    + shortcuts.getDynamicShortcuts().size() + " pinned=" + shortcuts.getPinnedShortcuts().size());
        } catch (Throwable error) {
            // A shortcut is a convenience. Failing to add one must not stop the app, but it must be visible in logcat.
            android.util.Log.e("Loki", "shortcut: failed", error);
        }
    }

    /** The long-press icon: the same accent tile with the puzzle piece every app uses. */
    private static Icon brandIcon(Context context) {
        android.graphics.Bitmap bitmap = ((BitmapDrawable) LokiArt.tile(context)).getBitmap();
        return Build.VERSION.SDK_INT >= 26 ? Icon.createWithAdaptiveBitmap(bitmap) : Icon.createWithBitmap(bitmap);
    }
}
