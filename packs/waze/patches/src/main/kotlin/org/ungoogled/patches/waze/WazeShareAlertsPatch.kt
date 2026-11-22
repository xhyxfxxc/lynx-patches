package org.ungoogled.patches.waze

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import org.ungoogled.patches.waze.WazeConstants.COMPATIBILITY_WAZE

// Lynx build: the "Share alerts with Maps" patch that used to be in this file was removed (see LYNX.md).
// Only "Stay awake in the background" is left here, so the file keeps its upstream name for easy merges.

@Suppress("unused")
val wazeStayAwakePatch = bytecodePatch(
    name = "Stay awake in the background",
    description = "Keeps Waze running in the background without a route. Uses more battery.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_WAZE)
    dependsOn(wazeConfigOverridesPatch)

    val minutes by intOption(
        key = "minutes",
        default = 180,
        title = "Minutes in the background",
        description = "Waze: 10.",
        required = true,
    )

    execute {
        keepWazeConfig("stayAwake", "CONFIG_VALUE_POWER_BACKGROUND_TIMEOUT_MS", minutes!! * 60_000)
    }
}
