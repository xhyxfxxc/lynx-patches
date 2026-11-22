group = "crimera"

patches {
    about {
        name = "Piko for X (Lynx build)"
        description = "Morphe patches focused on Twitter/X"
        source = "https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/x"
        author = "crimera, modified by xhyxfxxc"
        contact = "na"
        website = "https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/x"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    compileOnly("com.github.REAndroid:ARSCLib:a28c6fb2a7")

    // Used by JsonGenerator.
    implementation(libs.gson)

    implementation(libs.morphe.patches.library)
}

tasks {
    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.resource.CheckStringKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
