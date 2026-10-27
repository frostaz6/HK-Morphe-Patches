package app.template.patches.reddit.customclients.sync.syncforreddit.fix.postlinks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.template.patches.reddit.customclients.sync.syncforreddit.SyncForRedditCompatible
import app.template.patches.reddit.customclients.sync.syncforreddit.fix.redditvideolink.linkHandlerOpenLinkFingerprint

private const val EXTENSION = "Lapp/morphe/extension/syncforreddit/RedditPostLinksExtension;"

private val applicationOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/RedditApplication;",
    name = "onCreate",
)

@Suppress("unused")
val fixRedditPostLinksPatch = bytecodePatch(
    name = "Fix Reddit shared post links",
    description = "Resolves Reddit /r/.../s/... share links to real post URLs so links to other posts open inside Sync.",
    default = true,
) {
    extendWith("extensions/syncforreddit.mpe")
    compatibleWith(*SyncForRedditCompatible)

    execute {
        // Obtain application context before a post link is opened.
        applicationOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION->initialize(Landroid/content/Context;)V",
        )

        linkHandlerOpenLinkFingerprint.method.apply {
            // The same link helper is patched by Fix Reddit Video Links.
            // p3 is the URL; v2 is a scratch register used in this handler.
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p3 }, $EXTENSION->resolveShareLink(Ljava/lang/String;)Z
                    move-result v2
                    if-eqz v2, :open_normally
                    return v2
                """.trimIndent(),
                ExternalLabel("open_normally", getInstruction(0)),
            )
        }
    }
}
