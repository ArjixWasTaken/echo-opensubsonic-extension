plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

fun git(vararg args: String): String = providers.exec {
    commandLine("git", *args)
}.standardOutput.asText.get().trim()

// The version name is also the release tag, which Echo compares it against to find updates:
// {year}{month}{commit count}-{short hash}, dated by the commit, e.g. 202609143-9b73525
val gitHash = git("rev-parse", "HEAD").take(7)
val gitCount = git("rev-list", "--count", "HEAD").toInt()
val gitYearMonth = git("show", "-s", "--format=%cd", "--date=format:%Y%m", "HEAD")
val verCode by extra(gitCount)
val verName by extra("$gitYearMonth$gitCount-$gitHash")

tasks.register("printVersionName") {
    group = "help"
    description = "Prints the version name, which is also the release tag"
    doLast { println(verName) }
}

tasks.register("buildEapk") {
    group = "build"
    description = "Build .eapk (assemble + package .eapk)"
    dependsOn(":app:assembleDebug", ":ext:shadowJar")
    doLast {
        val tag: String = project.findProperty("extId") as? String ?: "unknown"
        val src = project.file("app/build/outputs/apk/debug/app-debug.apk")
        if (!src.exists()) throw GradleException("APK not found: ${src.absolutePath}")
        val outDir = project.file("app/build")
        val outFile = project.file("${outDir.path}/${tag}-$verName.eapk")
        copy { from(src); into(outDir); rename { outFile.name } }
        println("EAPK: ${outFile.absolutePath}")
    }
}
