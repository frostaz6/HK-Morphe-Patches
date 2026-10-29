package app.template.patches.reddit.customclients.sync.syncforreddit.fix.imgur

import app.template.patches.reddit.customclients.sync.syncforreddit.SyncForRedditCompatible
import app.template.patches.reddit.customclients.sync.syncforreddit.api.ORIGINAL_IMGUR_CLIENT_ID
import app.template.patches.reddit.customclients.sync.syncforreddit.api.imgurClientIdOption
import app.template.patches.reddit.customclients.sync.syncforreddit.api.spoofClientPatch

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions

private const val ALBUM_EXTENSION = "Lapp/morphe/extension/syncforreddit/ImgurAlbumWebExtension;"

private val galleryResponseFingerprint = Fingerprint(
    name = "parseNetworkResponse",
    parameters = listOf("Lcom/android/volley/NetworkResponse;"),
    custom = { _, cls -> cls.sourceFile == "ImgurGalleryRequest.java" },
)

private fun getUrlExtractionSmali(fieldClass: String, fieldName: String, baseUrl: String, suffix: String = ""): String = """
    new-instance v0, Ljava/lang/StringBuilder;
    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
    const-string v1, "$baseUrl"
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    iget-object v1, p0, ${fieldClass}->${fieldName}:Ljava/lang/String;
    
    const-string v2, "-"
    invoke-virtual {v1, v2}, Ljava/lang/String;->lastIndexOf(Ljava/lang/String;)I
    move-result v2
    if-ltz v2, :no_hyphen
    add-int/lit8 v2, v2, 0x1
    invoke-virtual {v1, v2}, Ljava/lang/String;->substring(I)Ljava/lang/String;
    move-result-object v1
    :no_hyphen
    
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v1, "$suffix"
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    return-object v0
""".trimIndent()

val fixImgurAlbumPatch = bytecodePatch(
    name = "Fix imgur links",
    description = "Loads public Imgur albums without an API key, with archived-page fallback; keeps single-image support.",
    default = true
) {
    compatibleWith(*SyncForRedditCompatible)
    dependsOn(spoofClientPatch)
    extendWith("extensions/syncforreddit.mpe")

    execute {
        // Preserve the previous album-reading credential for users who have not
        // configured a personal ID, to avoid a regression from v1.9.2.
        // Once a personal ID is supplied, use it for both album/single-image
        // reads and uploads (handled by Spoof client).
        val selectedClientId = imgurClientIdOption.value ?: ORIGINAL_IMGUR_CLIENT_ID
        val clientId = if (selectedClientId == ORIGINAL_IMGUR_CLIENT_ID) {
            "546c25a59c58ad7"
        } else {
            selectedClientId
        }
        
        externalGalleryUrlFingerprint.method.apply {
            addInstructions(0, getUrlExtractionSmali("Lg8/b;", "a", "https://imgur.com/a/", "/embed?pub=true"))
        }

        // Album pages are public HTML. Do not send the user's Imgur client ID
        // to the web page; no API account is needed.
        imgurGalleryRequestHeadersFingerprint.method.addInstructions(
            0,
            """
                new-instance v0, Ljava/util/HashMap;
                invoke-direct {v0}, Ljava/util/HashMap;-><init>()V
                const-string v1, "User-Agent"
                const-string v2, "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                invoke-interface {v0, v1, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                return-object v0
            """.trimIndent(),
        )

        // Keep Sync's original gallery JSON parser. Translate Imgur's public
        // embed HTML (and older archived copies if necessary) into data.images.
        galleryResponseFingerprint.method.apply {
            val dataRead = implementation!!.instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    (instruction as? ReferenceInstruction)?.reference.let { ref ->
                        (ref as? FieldReference)?.let {
                            it.definingClass == "Lcom/android/volley/NetworkResponse;" &&
                                it.name == "data" && it.type == "[B"
                        } == true
                    }
            }
            check(dataRead >= 0) { "Could not find album response data" }
            val register = getInstruction<OneRegisterInstruction>(dataRead).registerA
            addInstructions(
                dataRead + 1,
                """
                    invoke-static { v$register, p0 }, $ALBUM_EXTENSION->rewriteAlbum([BLjava/lang/Object;)[B
                    move-result-object v$register
                """.trimIndent(),
            )
        }

        externalImageUrlFingerprint.method.apply {
            addInstructions(0, getUrlExtractionSmali("Lg8/d;", "a", "https://api.imgur.com/3/image/"))
        }

        imgurImageRequestHeadersFingerprint.method.apply {
            addInstructions(0, """
                new-instance v0, Ljava/util/HashMap;
                invoke-direct {v0}, Ljava/util/HashMap;-><init>()V
                
                const-string v1, "User-Agent"
                const-string v2, "sync_for_reddit"
                invoke-interface {v0, v1, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                
                const-string v1, "Authorization"
                const-string v2, "Client-ID $clientId"
                invoke-interface {v0, v1, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                
                return-object v0
            """)
        }
    }
}
