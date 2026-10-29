package app.template.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import app.morphe.patcher.StringComparisonType
import app.morphe.patches.all.misc.string.replaceStringPatch
import app.template.patches.reddit.customclients.spoofClientPatch
import app.template.patches.reddit.customclients.sync.syncforreddit.SyncForRedditCompatible
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.util.Base64

// Sync's shared Imgur client ID is rate-limited. Give each user an option
// to use their own client ID without changing Reddit OAuth credentials.
internal const val ORIGINAL_IMGUR_CLIENT_ID = "981c2f80e996ca3"
private const val IMGUR_HEADER_PREFIX = "Client-ID "
private val IMGUR_CLIENT_ID_PATTERN = Regex("^[0-9a-fA-F]{15,16}$")

// Shared with Fix imgur links: one option controls upload and album-read credentials.
internal lateinit var imgurClientIdOption: Option<String>

@Suppress("unused")
val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    imgurClientIdOption = stringOption(
        "imgur-client-id",
        ORIGINAL_IMGUR_CLIENT_ID,
        null,
        "Imgur client ID",
        "Optional legacy Imgur client ID for single-image API lookups. " +
            "Catbox uploads and public album viewing work without an Imgur ID. " +
            "New Imgur API app registration may be unavailable.",
        false,
        validator = { value -> value != null && IMGUR_CLIENT_ID_PATTERN.matches(value) }
    )
    dependsOn(
        // Redirects from SSL to WWW domain are bugged causing auth problems.
        // Manually rewrite the URLs to fix this.
        replaceStringPatch("ssl.reddit.com", "www.reddit.com", comparison = StringComparisonType.CONTAINS)
    )

    compatibleWith(*SyncForRedditCompatible)

    execute {
        val clientId = clientIdOption.value!!
        val redirectUri = redirectUriOption.value!!
        val userAgent = userAgentOption.value!!

        // region Patch client id.
        getBearerTokenFingerprint.method.apply {
            val auth = Base64.getEncoder().encodeToString("$clientId:".toByteArray(Charsets.UTF_8))
            addInstructions(0, """
                const-string v0, "Basic $auth"
                return-object v0
            """)

            val occurrenceIndex =
                getAuthorizationStringFingerprint.stringMatches.first().index

            getAuthorizationStringFingerprint.method.apply {
                val authorizationStringInstruction = getInstruction<ReferenceInstruction>(occurrenceIndex)
                val targetRegister = (authorizationStringInstruction as OneRegisterInstruction).registerA
                val reference = authorizationStringInstruction.reference as StringReference

                val newAuthorizationUrl = reference.string.replace(
                    "client_id=.*?&".toRegex(),
                    "client_id=$clientId&",
                )

                replaceInstruction(
                    occurrenceIndex,
                    "const-string v$targetRegister, \"$newAuthorizationUrl\"",
                )
            }
        }
        // endregion

        // region Patch redirect URI.
        getRedirectUriFingerprint.method.addInstructions(0, """
            const-string v0, "$redirectUri"
            return-object v0
        """)
        // endregion

        // region Patch user agent.
        getUserAgentFingerprint.method.addInstructions(0, """
            const-string v0, "$userAgent"
            return-object v0
        """)
        // endregion

        // region Patch Imgur API URL.

        imgurImageAPIFingerprint.let {
            val apiUrlIndex = it.stringMatches.first().index
            it.method.replaceInstruction(
                apiUrlIndex,
                "const-string v1, \"https://api.imgur.com/3/image\"",
            )
        }

        // endregion

        // region Patch the Imgur upload Authorization header.
        // The existing URL rewrite sends images directly to api.imgur.com, but
        // retaining Sync's shared Client-ID makes that upload rate-limited.
        imgurUploadHeadersFingerprint.method.apply {
            val original = implementation?.instructions ?: error("Imgur upload method has no bytecode")
            val headerIndex = original.indexOfFirst { instruction ->
                instruction.opcode == Opcode.CONST_STRING &&
                    (instruction as? ReferenceInstruction)?.reference.let { reference ->
                        (reference as? StringReference)?.string?.startsWith(IMGUR_HEADER_PREFIX) == true
                    }
            }
            check(headerIndex >= 0) { "Could not find the Imgur Client-ID upload header" }

            val headerRegister = getInstruction<OneRegisterInstruction>(headerIndex).registerA
            replaceInstruction(
                headerIndex,
                "const-string v$headerRegister, \"$IMGUR_HEADER_PREFIX${imgurClientIdOption.value}\""
            )
        }
        // endregion
    }
}
