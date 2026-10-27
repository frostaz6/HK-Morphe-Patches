package app.template.patches.niagara.weather

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val FORECAST_CLASS_DESCRIPTOR = "Lapp/morphe/extension/niagara/NiagaraWeather\$Forecast;"

private fun MethodReference.smali() =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun FieldReference.smali() = "$definingClass->$name:$type"

/**
 * Routes Niagara's weather fetch through the NiagaraWeather extension. Source patches depend on this
 * and call [setWeatherSource].
 */
internal val niagaraWeatherPatch = bytecodePatch {
    extendWith("extensions/niagara.mpe")

    execute {
        // Fill the placeholders, so the extension never names obfuscated classes.
        val manager = fetchWeatherFingerprint.classDef.type
        val decode = decodeWeatherFingerprint.method
        val saveResponse = saveResponseFingerprint.method

        extensionDecodeFingerprint.method.addInstructions(
            0,
            """
                check-cast p0, $manager
                invoke-virtual { p0, p1, p2, p3 }, ${decode.smali()}
                move-result-object p0
                return-object p0
            """
        )

        extensionSaveResponseFingerprint.method.addInstructions(
            0,
            """
                check-cast p0, $manager
                invoke-virtual { p0, p1 }, ${saveResponse.smali()}
                return-void
            """
        )

        val (latitude, longitude) = weatherLocationCoordinatesFingerprint.method.instructions
            .filter { it.opcode == Opcode.IPUT }
            .map { (it as ReferenceInstruction).reference as FieldReference }
        listOf(extensionLatitudeFingerprint to latitude, extensionLongitudeFingerprint to longitude)
            .forEach { (fingerprint, field) ->
                fingerprint.method.addInstructions(
                    0,
                    """
                        check-cast p0, ${field.definingClass}
                        iget p0, p0, ${field.smali()}
                        return p0
                    """
                )
            }

        val (managerField, locationField) = fetchFromServerConstructorFingerprint.method.instructions
            .filter { it.opcode == Opcode.IPUT_OBJECT }
            .take(2)
            .map { (it as ReferenceInstruction).reference as FieldReference }

        // The other int field is the coroutine's step, 0 until it first suspends.
        val selectorField = fetchFromServerConstructorFingerprint.method.instructions
            .first { it.opcode == Opcode.IPUT }
            .let { (it as ReferenceInstruction).reference as FieldReference }
        val stepField = fetchFromServerFingerprint.classDef.fields
            .first { it.type == "I" && it.name != selectorField.name }

        // The server fetch is the switch's fall-through case. Answer it from the source on its first
        // step; v0 and v1 are overwritten by the original code, and "this" needs move/from16.
        fetchFromServerFingerprint.method.apply {
            val fetchIndex = instructions.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH } + 1

            addInstructionsWithLabels(
                fetchIndex,
                """
                    move-object/from16 v0, p0
                    iget v1, v0, ${stepField.definingClass}->${stepField.name}:I
                    if-nez v1, :fetch

                    iget-object v1, v0, ${managerField.smali()}
                    iget-object v0, v0, ${locationField.smali()}
                    invoke-static { v1, v0 }, $NIAGARA_WEATHER_CLASS_DESCRIPTOR->fetchWeather(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :fetch
                    return-object v0
                """,
                ExternalLabel("fetch", getInstruction(fetchIndex))
            )
        }
    }
}

/**
 * Sets the weather source: a class with `static NiagaraWeather.Forecast fetch(double, double)`.
 * Fails if a source is already set.
 */
context(_: BytecodePatchContext)
internal fun setWeatherSource(sourceClass: String) {
    extensionFetchFromSourceFingerprint.method.apply {
        // Unfilled, it's just `return null`.
        if (instructions.first().opcode != Opcode.CONST_4) {
            throw PatchException("Only one Niagara weather source patch can be applied")
        }

        addInstructions(
            0,
            """
                invoke-static { p0, p1, p2, p3 }, $sourceClass->fetch(DD)$FORECAST_CLASS_DESCRIPTOR
                move-result-object v0
                return-object v0
            """
        )
    }
}
