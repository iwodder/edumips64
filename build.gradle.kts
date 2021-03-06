/*
 * EduMIPS64 Gradle build configuration
 */
import java.time.LocalDateTime

plugins {
    java
    // The Eclipse plugin adds the "eclipse" task, which generates
    // files needed for Visual Studio Code and other IDEs.
    id ("eclipse")
    id ("application")
    id ("jacoco")
    id ("com.dorongold.task-tree") version "1.5"
    id ("us.ascendtech.gwt.classic") version "0.5.1"
}

repositories {
    jcenter()
}

dependencies {
    compileOnly("com.google.gwt:gwt-user:2.9.0")
    compileOnly("com.google.gwt:gwt-dev:2.9.0")
    compileOnly("com.google.elemental2:elemental2-dom:1.1.0")
    implementation("javax.help:javahelp:2.0.05")
    implementation("com.vertispan.rpc:workers:1.0-alpha-5")
    implementation("info.picocli:picocli:4.5.1")
    testImplementation("junit:junit:4.13")
}

application {
  mainClassName = "org.edumips64.Main"  
}
val codename: String by project
val version: String by project

// Specify Java source/target version.
tasks.compileJava {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

/* 
 * Documentation tasks
 */
tasks.create<Exec>("htmlDocsEn"){
    workingDir = File("${projectDir}/docs/user/en/src")
    commandLine("make", "html", "BUILDDIR=${buildDir}/docs/en", "SPHINXOPTS=-N -a -E")
}

tasks.create<Exec>("htmlDocsIt") {
    workingDir = File("${projectDir}/docs/user/it/src")
    commandLine("make", "html", "BUILDDIR=${buildDir}/docs/it", "SPHINXOPTS=-N -a -E")
}

tasks.create<Exec>("pdfDocsEn") {
    workingDir = File("${projectDir}/docs/user/en/src")
    commandLine("make", "pdf", "BUILDDIR=${buildDir}/docs/en", "SPHINXOPTS=-N -a -E")
}

tasks.create<Exec>("pdfDocsIt") {
    workingDir = File("${projectDir}/docs/user/it/src")
    commandLine("make", "pdf", "BUILDDIR=${buildDir}/docs/it", "SPHINXOPTS=-N -a -E")
}

// Catch-all task for documentation
tasks.create<GradleBuild>("allDocs") {
    tasks = listOf("htmlDocsIt", "htmlDocsEn", "pdfDocsEn", "pdfDocsIt")
    description = "Run all documentation tasks"
}

/*
 * Jar tasks
 */
val docsDir = "build/classes/java/main/docs"
// Include the docs folder at the root of the jar, for JavaHelp
tasks.create<Copy>("copyHelpEn") {
    from("${buildDir}/docs/en") {
        include("html/**")
        exclude("**/_sources/**")
    }
    into ("${docsDir}/user/en")
    dependsOn("htmlDocsEn")
}

tasks.create<Copy>("copyHelpIt") {
    from("${buildDir}/docs/it") {
        include("html/**")
        exclude("**/_sources/**")
    }
    into ("${docsDir}/user/it")
    dependsOn("htmlDocsIt")
}

tasks.create<Copy>("copyHelp") {
    from("docs/") {
        exclude("**/src/**", "**/design/**", "**/*.py",  "**/*.pyc", 
            "**/*.md", "**/.buildinfo", "**/objects.inv", "**/*.txt", "**/__pycache__/**")
    }
    into ("${docsDir}")
    dependsOn("copyHelpEn")
    dependsOn("copyHelpIt")
}

/*
 * Helper function to execute a command and return its output.
 */
fun String.runCommand(workingDir: File = file("./")): String {
    val parts = this.split("\\s".toRegex())
    val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

    proc.waitFor(1, TimeUnit.MINUTES)
    return proc.inputStream.bufferedReader().readText().trim()
}

fun getSourceControlMetadata() : Triple<String, String, String> {
    val branch: String
    val commitHash: String
    val qualifier: String
    if(System.getenv("GITHUB_ACTIONS").isNullOrEmpty()) {
        println("Running locally")
        branch = "git rev-parse --abbrev-ref HEAD".runCommand()
        commitHash = "git rev-parse --verify --short HEAD".runCommand()
        qualifier = ""
    } else {
        println("Running under GitHub Actions")
        branch = System.getenv("GITHUB_REF")
        commitHash = System.getenv("GITHUB_SHA").substring(0, 7)
        qualifier = "alpha"
    }
    return Triple(branch, commitHash, qualifier)
}

val sharedManifest = the<JavaPluginConvention>().manifest {
    attributes["Signature-Version"] = version
    attributes["Codename"] = codename
    attributes["Build-Date"] = LocalDateTime.now()

    val (branch, gitRevision, qualifier) = getSourceControlMetadata()
    attributes["Full-Buildstring"] = "$branch@$gitRevision"
    attributes["Git-Revision"] = gitRevision
    attributes["Build-Qualifier"] = qualifier
}

// Main JAR
tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.contains("javahelp") && it.name.endsWith("jar") }.map { zipTree(it) }
        configurations.runtimeClasspath.get().filter { it.name.contains("picocli") && it.name.endsWith("jar") }.map { zipTree(it) }
    })
    manifest {
        attributes["Main-Class"] = application.mainClassName
        attributes["SplashScreen-Image"] = "images/splash.png"
        from(sharedManifest)   
    }
    dependsOn("copyHelp")
}

// CLI JAR
tasks.create<Jar>("cliJar"){
    classifier = "cli"
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.contains("picocli") && it.name.endsWith("jar") }.map { zipTree(it) }
    })
    manifest {
        attributes["Main-Class"] = "org.edumips64.MainCLI"
        from(sharedManifest)
    }
}

tasks.assemble{
    dependsOn("jar") 
    dependsOn("cliJar") 
}

// NoHelp JAR
tasks.create<Jar>("noHelpJar"){
    classifier = "nohelp"
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.contains("picocli") && it.name.endsWith("jar") }.map { zipTree(it) }
    })
    manifest {
        attributes["Main-Class"] = application.mainClassName
        attributes["SplashScreen-Image"] = "images/splash.png"
        from(sharedManifest)   
    }
}

/*
 * Code coverage report tasks
 */
tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        csv.isEnabled = false
        html.isEnabled = false
    }
}

tasks.check{
    dependsOn("jacocoTestReport")
}

tasks.register("release") {
    group = "Release"
    description = "Creates all artifacts for a given EduMIPS64 release"
    dependsOn("allDocs")
    dependsOn("jar")

    doFirst {
        println("Creating artifacts for version $version")
    }
}

/*
 * GWT tasks
 */
gwt {
    modules.add("org.edumips64.webclient") 
    sourceLevel = "1.11"
}