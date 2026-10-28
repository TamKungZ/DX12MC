val harness = project.extensions.getByType(SourceSetContainer::class.java).create("harness") {
    val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath + main.output
}

val harnessRunDir = layout.buildDirectory.dir("harness-run").get().asFile
val harnessOptions = tasks.register("prepareHarnessRun") {
    group = "dx12mc verification"
    description = "Creates a fresh temporary run directory for the flight harness."
    doLast {
        File(harnessRunDir, "saves").deleteRecursively()
        harnessRunDir.mkdirs()
        File(harnessRunDir, "options.txt").writeText(
            listOf(
                "version:3955", "renderDistance:12", "simulationDistance:12", "maxFps:260",
                "enableVsync:false", "pauseOnLostFocus:false", "onboardAccessibility:false",
                "skipMultiplayerWarning:true", "tutorialStep:none", "joinedFirstServer:true",
                "graphicsMode:1", "guiScale:2", "fov:0.0", "bobView:false"
            ).joinToString("\n", postfix = "\n")
        )
    }
}

tasks.matching { it.name == "runHarness" }.configureEach { dependsOn(harnessOptions) }
