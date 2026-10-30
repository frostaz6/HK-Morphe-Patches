package app.template.patches.reddit.customclients.sync.syncforreddit.fix.redditgallery

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.template.patches.reddit.customclients.sync.syncforreddit.SyncForRedditCompatible
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXT = "Lapp/morphe/extension/syncforreddit/RedditGalleryExtension;"

/**
 * Unlike Imgur albums, Reddit galleries load post details from the Reddit OAuth API.
 * Keep the existing OAuth request/response path and only repair URLs and JSON that
 * the legacy gallery parser would otherwise reject.
 */
// Gallery parser method names can also be obfuscated in the released APK.
// These JSON keys occur together in Sync's native Reddit gallery parser,
// giving us a stable match without relying on reconstructed Java names.
private val galleryJsonParser = Fingerprint(
    strings = listOf("media_metadata", "gallery_data"),
)

// This method is static and may be obfuscated in Sync's original APK.
// Match its literal endpoint, not the decompiled method name or a class-wide
// fingerprint based on strings located in a different method.
private val galleryUrlBuilder = Fingerprint(
    strings = listOf("https://oauth.reddit.com/api/info.json?id=t3_"),
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
)

@Suppress("unused")
val fixRedditGalleriesPatch = bytecodePatch(
    name = "Fix Reddit galleries",
    description = "Fixes Reddit gallery URLs containing query strings and recovers gallery images from alternate post metadata where possible.",
    default = true,
) {
    compatibleWith(*SyncForRedditCompatible)
    extendWith("extensions/syncforreddit.mpe")

    execute {
        // URL compatibility is optional: if Sync's build differs, keep its
        // original URL builder rather than failing the entire patch.
        galleryUrlBuilder.methodOrNull?.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0 }, $EXT->galleryApiUrl(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v0
                    if-eqz v0, :original_url
                    return-object v0
                """.trimIndent(),
                ExternalLabel("original_url", getInstruction(0)),
            )
        }

        galleryJsonParser.method.apply {
            val dataRead = implementation!!.instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    (instruction as? ReferenceInstruction)?.reference.let { reference ->
                        (reference as? FieldReference)?.let { field ->
                            field.definingClass == "Lcom/android/volley/NetworkResponse;" &&
                                field.name == "data" && field.type == "[B"
                        } == true
                    }
            }
            check(dataRead >= 0) { "Could not find Reddit gallery response data" }
            val register = getInstruction<OneRegisterInstruction>(dataRead).registerA
            addInstructions(
                dataRead + 1,
                """
                    invoke-static { v$register }, $EXT->normalizeGallery([B)[B
                    move-result-object v$register
                """.trimIndent(),
            )
        }
    }
}
