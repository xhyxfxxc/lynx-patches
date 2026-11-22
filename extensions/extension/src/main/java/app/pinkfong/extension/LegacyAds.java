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
import java.util.HashSet;
import java.util.Set;

/**
 * Answers an ad load that the Hide ads patch blocked in the legacy Google Mobile Ads SDK
 * (com.google.android.gms.ads), the way a real ad network answers one it cannot fill: the load callback gets an
 * "onAdFailedToLoad" with a NO_FILL error, so the game is told no ad is coming instead of waiting for one.
 *
 * The first blocked load of each ad format (each format's bridge callback is its own class) is answered at once, so
 * a game that waits for a start-up ad carries on straight away. Every later blocked load of the same format is
 * answered after {@link #RETRY_DELAY_MS}. A real network only answers a no-fill after a network round trip, but an
 * instant answer makes games that load again from their failure callback retry thousands of times a second, which
 * filled the JNI global reference table and crashed the app.
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
    /**
     * How long every blocked load after the first of its format waits for its no-fill answer. Several games (Smart
     * Study's "SSAdMob" manager) load again from inside the failure callback; answered at once, that loop ran about
     * 500 times a second, until the JNI global reference table overflowed (max 51200) and ART aborted the app. A
     * minute still tells the game no ad is coming, at the pace a real network failing repeatedly would.
     */
    private static final long RETRY_DELAY_MS = 60_000L;

    private static Constructor<?> errorConstructor;
    private static Method onAdFailedToLoad;
    private static boolean looked;
    private static boolean logged;
    private static Handler mainThread;
    /** The callback classes whose first blocked load has been answered. Guarded by itself. */
    private static final Set<String> answered = new HashSet<String>();

    private LegacyAds() {
    }

    /**
     * Called in place of the ad load, with the load callback the game passed to it (an AdLoadCallback of any
     * format). Never throws: the patched method has to return whatever happens.
     */
    public static void failLoad(final Object callback) {
        if (callback == null) return;
        try {
            if (!resolve(callback.getClass().getClassLoader())) return;
            final Object error = errorConstructor.newInstance(NO_FILL, MESSAGE, "com.google.android.gms.ads", null, null);
            boolean first;
            synchronized (answered) {
                first = answered.add(callback.getClass().getName());
            }
            Runnable answer = new Runnable() {
                @Override
                public void run() {
                    try {
                        onAdFailedToLoad.invoke(callback, error);
                        LokiLog.debug("Pinkfong: an ad load was answered with no fill");
                    } catch (Throwable t) {
                        note("could not answer an ad load", t);
                    }
                }
            };
            if (first) {
                handler().post(answer);
            } else {
                handler().postDelayed(answer, RETRY_DELAY_MS);
            }
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
