/*
 * Copyright (C) 2026 Lokesh Sparrow
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.linkedin.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.lang.ref.WeakReference;

/**
 * With GmsCore support, checks once per start that microG RE is installed, recent enough, allowed to run in the
 * background and running, and says what to do when it is not. Without it "Continue with Google" fails silently.
 * It starts from the launcher activity, so it also runs before sign-in, and from the first feed screen.
 * <p>
 * The package name, the gservices provider and the version codes are read from microG RE's own APK
 * (https://github.com/MorpheApp/MicroG-RE, release 7.3.0: app.revanced.android.gms, provider
 * app.revanced.android.gsf.gservices, version code 255070304).
 */
final class GmsCoreCheck {
    private static final String GMS_CORE_PACKAGE = "app.revanced.android.gms";
    private static final Uri GMS_CORE_PROVIDER = Uri.parse("content://app.revanced.android.gsf.gservices/prefix");
    /** Oldest microG RE accepted; older builds are asked to update. 7.3.0 is 255070304. */
    private static final long MIN_VERSION_CODE = 255070104L;
    private static final String DOWNLOAD_URL = "https://github.com/MorpheApp/microg-RE/releases";
    private static final String DONT_KILL_MY_APP_URL = "https://dontkillmyapp.com/?app=MicroG";

    private static boolean checked;
    /** The screen in front. Signed out, the launcher activity finishes right after it starts the sign-in screen. */
    private static WeakReference<Activity> resumed = new WeakReference<>(null);

    private GmsCoreCheck() {
    }

    /**
     * Called when a screen of the app starts or resumes; does its work once per process, a moment later so the
     * screen is ready. Without the GmsCore patch it does nothing, and a second call does nothing either.
     */
    static void check(Activity activity) {
        if (checked || !Settings.isGmsCoreSupportIncluded()) return;
        checked = true;
        resumed = new WeakReference<>(activity);
        Application application = activity.getApplication();
        if (application != null) application.registerActivityLifecycleCallbacks(new Front());
        new Handler(Looper.getMainLooper()).postDelayed(() -> inFront(activity, 3), 1500);
    }

    /** Runs the check on the screen in front: the one that started it may be gone (signed out) or not the one to see. */
    private static void inFront(Activity started, int retries) {
        Activity front = resumed.get();
        if (front == null || front.isFinishing() || front.isDestroyed()) {
            if (retries > 0) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> inFront(started, retries - 1), 1000);
                return;
            }
            front = started;
        }
        run(front);
    }

    /** Remembers which screen of the app is resumed. */
    private static final class Front implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(Activity activity) {
            resumed = new WeakReference<>(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (resumed.get() == activity) resumed = new WeakReference<>(null);
        }

        @Override
        public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }

    private static void run(Activity activity) {
        try {
            PackageManager manager = activity.getPackageManager();
            PackageInfo info;
            try {
                info = manager.getPackageInfo(GMS_CORE_PACKAGE, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Settings.debugLog("GmsCore check: microG RE is not installed");
                Downloads.toast(activity, I18n.tr("microG RE is not installed. Opening its download page."));
                open(activity, DOWNLOAD_URL);
                return;
            }
            long version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
            if (version < MIN_VERSION_CODE) {
                Settings.debugLog("GmsCore check: microG RE " + version + " is older than " + MIN_VERSION_CODE);
                Downloads.toast(activity, I18n.tr("microG RE is too old. Opening its download page to update it."));
                open(activity, DOWNLOAD_URL);
                return;
            }

            boolean automotive = manager.hasSystemFeature("android.hardware.type.automotive");
            PowerManager power = (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
            if (!automotive && power != null && !power.isIgnoringBatteryOptimizations(GMS_CORE_PACKAGE)) {
                if (!Settings.get(Settings.GMS_CORE_BATTERY_REMINDER, true)) {
                    Settings.debugLog("GmsCore check: microG RE is under battery optimization, reminder turned off");
                } else {
                    Settings.debugLog("GmsCore check: microG RE is under battery optimization");
                    ask(activity, I18n.tr("microG RE is limited by battery optimization, so Google sign-in may fail. "
                                    + "Allow it to run in the background."),
                            I18n.tr("Open settings"),
                            () -> activity.startActivity(
                                    new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),
                            () -> Settings.set(Settings.GMS_CORE_BATTERY_REMINDER, false));
                    return;
                }
            }

            ContentProviderClient client = activity.getContentResolver().acquireContentProviderClient(GMS_CORE_PROVIDER);
            if (client == null) {
                Settings.debugLog("GmsCore check: microG RE is not running");
                ask(activity, I18n.tr("microG RE is not running in the background, so Google sign-in may fail. "
                                + "Your phone may stop it; the website explains how to allow it."),
                        I18n.tr("Open website"), () -> open(activity, DONT_KILL_MY_APP_URL), null);
            } else {
                client.close();
            }
        } catch (Throwable t) {
            Diagnostics.error("GmsCore check failed", t);
        }
    }

    /** {@code silence}, when given, adds a "Don't remind me" button that runs it. */
    private static void ask(Activity activity, String message, String action, Runnable onAction, Runnable silence) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("microG RE")
                .setMessage(message)
                .setPositiveButton(action, (d, which) -> onAction.run())
                .setNegativeButton(I18n.tr("Not now"), null);
        if (silence != null) builder.setNeutralButton(I18n.tr("Don't remind me"), (d, which) -> silence.run());
        AlertDialog dialog = builder.show();
        SettingsActivity.tintButtons(dialog, activity);
    }

    private static void open(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Diagnostics.error("could not open " + url, t);
        }
    }
}
