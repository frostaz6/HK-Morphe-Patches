package app.template.patches.reddit.customclients.sync.syncforreddit.fix.imgur

import app.template.patches.reddit.customclients.sync.syncforreddit.SyncForRedditCompatible
import app.template.patches.reddit.customclients.sync.syncforreddit.api.ORIGINAL_IMGUR_CLIENT_ID
import app.template.patches.reddit.customclients.sync.syncforreddit.api.imgurClientIdOption
import app.template.patches.reddit.customclients.sync.syncforreddit.api.spoofClientPatch

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions

private fun getUrlExtractionSmali(fieldClass: String, fieldName: String, baseUrl: String): String = """
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
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    return-object v0
""".trimIndent()

val fixImgurAlbumPatch = bytecodePatch(
    name = "Fix imgur links",
    description = "Uses Imgur JSON API to load albums with a client ID, like older working Sync builds and ReSync.",
    default = true
) {
    compatibleWith(*SyncForRedditCompatible)
    dependsOn(spoofClientPatch)

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
            addInstructions(0, getUrlExtractionSmali("Lg8/b;", "a", "https://api.imgur.com/3/album/"))
        }

        imgurGalleryRequestHeadersFingerprint.method.apply {
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
