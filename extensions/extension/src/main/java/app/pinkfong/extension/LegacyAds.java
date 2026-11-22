/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.extension;

import android.os.Handler;
import android.os.Looper;

import app.loki.extension.LokiLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Answers an ad load that the Hide ads patch blocked in the legacy Google Mobile Ads SDK
 * (com.google.android.gms.ads), the way a real ad network answers one it cannot fill: the load callback gets an
 * "onAdFailedToLoad" with a NO_FILL error, so the game is told no ad is coming instead of waiting for one.
 *
 * The extension is not compiled against the Google Mobile Ads SDK (it is not a dependency of Lynx Patches), so the
 * error is built and delivered by reflection. The lookups are made once, on the first blocked load, and kept. All
 * the names used are the SDK's public API, which R8 keeps. The SDK delivers its load results on the main thread, so
 * the callback is called from there too.
 */
public final class LegacyAds {
    private static final String ADS = "com.google.android.gms.ads.";
    /** AdRequest.ERROR_CODE_NO_FILL. */
    private static final int NO_FILL = 3;
    private static final String MESSAGE = "No ad: blocked by Lynx Patches";

    private static Constructor<?> errorConstructor;
    private static Method onAdFailedToLoad;
    private static boolean looked;
    private static boolean logged;
    private static Handler mainThread;

    private LegacyAds() {
    }

    /**
     * Called in place of the ad load, with the load callback the game passed to it (an AdLoadCallback of any
     * format). Never throws: the patched method has to return whatever happens.
     */
    public static void failLoad(Object callback) {
        if (callback == null) return;
        try {
            if (!resolve(callback.getClass().getClassLoader())) return;
            final Object error = errorConstructor.newInstance(NO_FILL, MESSAGE, "com.google.android.gms.ads", null, null);
            handler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        onAdFailedToLoad.invoke(callback, error);
                        LokiLog.debug("Pinkfong: an ad load was answered with no fill");
                    } catch (Throwable t) {
                        note("could not answer an ad load", t);
                    }
                }
            });
        } catch (Throwable t) {
            note("could not answer an ad load", t);
        }
    }

    private static synchronized Handler handler() {
        if (mainThread == null) mainThread = new Handler(Looper.getMainLooper());
        return mainThread;
    }

    /** Looks the SDK's types up once; false when they are missing, in which case it is not tried again. */
    private static synchronized boolean resolve(ClassLoader loader) {
        if (looked) return errorConstructor != null;
        looked = true;
        try {
            Class<?> error = Class.forName(ADS + "LoadAdError", false, loader);
            Class<?> cause = Class.forName(ADS + "AdError", false, loader);
            Class<?> info = Class.forName(ADS + "ResponseInfo", false, loader);
            Class<?> listener = Class.forName(ADS + "AdLoadCallback", false, loader);
            Constructor<?> constructor =
                    error.getConstructor(int.class, String.class, String.class, cause, info);
            Method method = listener.getMethod("onAdFailedToLoad", error);
            onAdFailedToLoad = method;
            errorConstructor = constructor;
            return true;
        } catch (Throwable t) {
            note("could not find the ad error types", t);
            return false;
        }
    }

    /** The log is only written once: a blocked load can repeat many times and would flood it. */
    private static synchronized void note(String what, Throwable t) {
        if (logged) return;
        logged = true;
        LokiLog.note("Pinkfong ads failed: " + what + ": " + t);
    }
}
