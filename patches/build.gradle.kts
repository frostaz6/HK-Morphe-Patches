group = "app.mcnannay"

patches {
    about {
        name = "HK Morphe Patches (mcnannay)"
        description = "Patches for apps I like"
        source = "https://github.com/mcnannay/HK-Morphe-Patches"
        author = "mcnannay"
        contact = "na"
        website = "https://github.com/mcnannay/HK-Morphe-Patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    implementation(libs.morphe.patches.library)
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}