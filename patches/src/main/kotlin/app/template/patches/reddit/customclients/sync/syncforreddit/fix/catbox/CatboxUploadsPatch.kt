package app.template.patches.reddit.customclients.sync.syncforreddit.fix.catbox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.template.patches.reddit.customclients.sync.syncforreddit.SyncForRedditCompatible
import app.template.patches.reddit.customclients.sync.syncforreddit.api.imgurImageAPIFingerprint
import app.template.patches.reddit.customclients.sync.syncforreddit.api.imgurUploadHeadersFingerprint
import app.template.patches.reddit.customclients.sync.syncforreddit.api.spoofClientPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXT = "Lapp/morphe/extension/syncforreddit/CatboxUploadExtension;"

// Volley serializes POST bodies in Request.getBody(). Override it *only* for
// Sync's Catbox upload URL; every other Volley request falls through unchanged.
private val volleyBody = Fingerprint(
    definingClass = "Lcom/android/volley/Request;",
    name = "getBody",
)
private val volleyContentType = Fingerprint(
    definingClass = "Lcom/android/volley/Request;",
    name = "getBodyContentType",
)
private val uploadResponse = Fingerprint(
    name = "parseNetworkResponse",
    parameters = listOf("Lcom/android/volley/NetworkResponse;"),
    custom = { _, cls -> cls.sourceFile == "UploadImgurRequest.java" },
)

@Suppress("unused")
val catboxUploadsPatch = bytecodePatch(
    name = "Upload comment images to Catbox",
    description = "Uploads images to Catbox without an Imgur API key. Keeps Sync's normal image-link insertion.",
    default = true,
) {
    extendWith("extensions/syncforreddit.mpe")
    dependsOn(spoofClientPatch)
    compatibleWith(*SyncForRedditCompatible)

    execute {
        // The Spoof client patch has already rewritten this URL to api.imgur.com.
        // The original index remains stable after replaceInstruction.
        val urlIndex = imgurImageAPIFingerprint.stringMatches.first().index
        imgurImageAPIFingerprint.method.replaceInstruction(
            urlIndex,
            "const-string v1, \"https://catbox.moe/user/api.php\"",
        )

        // NEVER send the old Imgur authorization or RapidAPI credentials to Catbox.
        imgurUploadHeadersFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Ljava/util/Collections;->emptyMap()Ljava/util/Map;
                move-result-object v0
                return-object v0
            """.trimIndent(),
        )

        volleyBody.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0 }, $EXT->requestBody(Ljava/lang/Object;)[B
                    move-result-object v0
                    if-eqz v0, :normal_body
                    return-object v0
                """.trimIndent(),
                ExternalLabel("normal_body", getInstruction(0)),
            )
        }
        volleyContentType.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0 }, $EXT->requestContentType(Ljava/lang/Object;)Ljava/lang/String;
                    move-result-object v0
                    if-eqz v0, :normal_content_type
                    return-object v0
                """.trimIndent(),
                ExternalLabel("normal_content_type", getInstruction(0)),
            )
        }

        // Catbox returns a URL string, not Imgur's {"data":{"link":...}}.
        // Adapt the response bytes before Sync runs its existing JSON parser.
        uploadResponse.method.apply {
            val dataRead = implementation!!.instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    (instruction as? ReferenceInstruction)?.reference.let { ref ->
                        (ref as? FieldReference)?.let {
                            it.definingClass == "Lcom/android/volley/NetworkResponse;" &&
                                it.name == "data" && it.type == "[B"
                        } == true
                    }
            }
            check(dataRead >= 0) { "Could not find Imgur upload response data" }
            val register = getInstruction<OneRegisterInstruction>(dataRead).registerA
            addInstructions(
                dataRead + 1,
                """
                    invoke-static { v$register }, $EXT->wrapResponse([B)[B
                    move-result-object v$register
                """.trimIndent(),
            )
        }
    }
}
