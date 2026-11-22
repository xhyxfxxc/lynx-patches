/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.extension;

import app.loki.extension.LokiLog;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * The Skip rewarded ads patch: a rewarded ad is never loaded or played. The game is told, in the order a real ad
 * would tell it, that an ad loaded; and when the game asks to show it, that it was shown, that the reward was earned
 * and that it was closed. The game then hands out its reward as if the child had watched the video.
 *
 * Both Google Mobile Ads Unity bridges are served: the next-gen one (callbacks run on the bridge's own executor)
 * and the legacy one (callbacks run on a new thread, as the legacy bridge does). The callback is the game's own
 * object (a java.lang.reflect.Proxy that Unity makes), and the extension is not compiled against the bridge, so its
 * methods are looked up through its interfaces by name. The calls are never made inside the game's own call: they
 * are posted, like the answers of a real ad.
 */
public final class PinkfongRewards {
    private static final String REWARD_TYPE = "Reward";
    private static final float REWARD_AMOUNT = 1.0f;

    /** The "an ad is ready" call, named after the format: the callback has only one of these. */
    private static final String[] LOADED = {"onRewardedAdLoaded", "onRewardedInterstitialAdLoaded"};

    private static boolean logged;

    private PinkfongRewards() {
    }

    /** Next-gen bridge: answers a rewarded load with "loaded". The executor is the bridge's own. */
    public static void loaded(Object callback, Executor executor) {
        post(executor, callback, false);
    }

    /** Legacy bridge: answers a rewarded load with "loaded", from a new thread like the bridge's own answers. */
    public static void loaded(Object callback) {
        post(null, callback, false);
    }

    /** Next-gen bridge: plays the show of an ad that was not there: shown, impression, reward, closed. */
    public static void shown(Object callback, Executor executor) {
        post(executor, callback, true);
    }

    /** Legacy bridge: the same, from a new thread. */
    public static void shown(Object callback) {
        post(null, callback, true);
    }

    private static void post(Executor executor, final Object callback, final boolean show) {
        if (callback == null) return;
        Runnable work = new Runnable() {
            @Override
            public void run() {
                try {
                    if (show) {
                        call(callback, "onAdShowedFullScreenContent");
                        call(callback, "onAdImpression");
                        call(callback, "onUserEarnedReward", REWARD_TYPE, REWARD_AMOUNT);
                        call(callback, "onAdDismissedFullScreenContent");
                        LokiLog.debug("Pinkfong: a rewarded ad was granted without playing");
                    } else {
                        boolean told = false;
                        for (String name : LOADED) {
                            if (call(callback, name)) {
                                told = true;
                                break;
                            }
                        }
                        if (!told) note("no loaded call on " + callback.getClass().getName(), null);
                    }
                } catch (Throwable t) {
                    note("could not answer a rewarded ad", t);
                }
            }
        };
        try {
            if (executor != null) executor.execute(work);
            else new Thread(work).start();
        } catch (Throwable t) {
            note("could not post a rewarded ad answer", t);
        }
    }

    /** Calls a method of the callback by name; false when it has no such method. */
    private static boolean call(Object callback, String name, Object... arguments) throws Exception {
        Method method = find(callback.getClass(), name);
        if (method == null) return false;
        method.invoke(callback, arguments);
        return true;
    }

    /**
     * The callback is a Proxy whose own class is not public API, so the method is taken from an interface it
     * implements (Unity's callback interface or one of the interfaces that extends); the class itself is the fallback.
     */
    private static Method find(Class<?> type, String name) {
        for (Class<?> iface : type.getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (method.getName().equals(name)) return method;
            }
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) return method;
        }
        return null;
    }

    /** The log is only written once: a rewarded ad can be asked for many times and would flood it. */
    private static synchronized void note(String what, Throwable t) {
        if (logged) return;
        logged = true;
        LokiLog.note("Pinkfong rewards failed: " + what + (t != null ? ": " + t : ""));
    }
}
