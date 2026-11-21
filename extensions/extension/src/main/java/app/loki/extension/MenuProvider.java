/*
 * Copyright (C) 2026 Lokesh Sparrow
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.loki.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

/**
 * Starts with the app and puts "Lynx Patches" on the launcher's long-press menu. Declared by the menu patch;
 * the extension itself ships no manifest entries.
 */
public final class MenuProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        LokiLog.attach(getContext());
        // Written on purpose at every start: a report that arrives empty cannot be diagnosed, and this line alone
        // says whether the patched code ran at all.
        // audit: keep
        /* audit: keep */ LokiLog.note("loki: extension started in " + (getContext() == null ? "?" : getContext().getPackageName())
                + " (SDK " + Build.VERSION.SDK_INT + ")");
        // Splitwise's AMOLED switch layers its theme overlay onto every screen; registered before any activity.
        LokiSwitches.installAmoled(getContext());
        // The launcher shortcut is added beside the app's own and put back when the app replaces them.
        LokiShortcut.start(getContext());
        return true;
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
