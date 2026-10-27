package app.template.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.reddit.customclients.sync.syncforreddit.SyncForRedditCompatible

// Adapted from ReVanced / Patcheddit (GPLv3). The final call in Sync's
// MainActivity.onCreate shows the discontinued-app / Lemmy bottom sheet.
private val mainActivityOnCreateFingerprint = Fingerprint(
    definingClass = "/MainActivity;",
    name = "onCreate",
)

@Suppress("unused")
val disableSyncForLemmyBottomSheetPatch = bytecodePatch(
    name = "Disable Sync for Lemmy bottom sheet",
    description = "Removes the startup warning that Sync for Reddit is shutting down and the Sync for Lemmy invitation.",
    default = true,
) {
    compatibleWith(*SyncForRedditCompatible)

    execute {
        mainActivityOnCreateFingerprint.method.apply {
            val instructions = implementation?.instructions ?: error("MainActivity.onCreate has no bytecode")
            check(instructions.size >= 2) { "MainActivity.onCreate has unexpected bytecode" }
            removeInstruction(instructions.lastIndex - 1)
        }
    }
}
