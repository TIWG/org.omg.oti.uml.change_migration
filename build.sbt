
import sbt.Keys._
import sbt._

import gov.nasa.jpl.imce.sbt._
import gov.nasa.jpl.imce.sbt.ProjectHelper._

updateOptions := updateOptions.value.withCachedResolution(true)

lazy val core = Project("oti-uml-change_migration", file("."))
  .enablePlugins(IMCEGitPlugin)
  .settings(dynamicScriptsResourceSettings("org.omg.oti.uml.change_migration"))
  .settings(IMCEPlugin.strictScalacFatalWarningsSettings)
  .settings(
    IMCEKeys.licenseYearOrRange := "2015-2016",
    IMCEKeys.organizationInfo := IMCEPlugin.Organizations.oti,
    IMCEKeys.targetJDK := IMCEKeys.jdk18.value,

    buildInfoPackage := "org.omg.oti.uml.change_migration",
    buildInfoKeys ++= Seq[BuildInfoKey](BuildInfoKey.action("buildDateUTC") { buildUTCDate.value }),

    mappings in (Compile, packageSrc) ++= {
      import Path.{flat, relativeTo}
      val base = (sourceManaged in Compile).value
      val srcs = (managedSources in Compile).value
      srcs pair (relativeTo(base) | flat)
    },

    projectID := {
      val previous = projectID.value
      previous.extra(
        "build.date.utc" -> buildUTCDate.value,
        "artifact.kind" -> "generic.library")
    },

    resourceDirectory in Compile :=
      baseDirectory.value / "src" / "main" / "resources",

    packageOptions in (Compile, packageBin) += {
      val mf = baseDirectory.value / "META-INF" / "MANIFEST.MF"
      val manifest = Using.fileInputStream(mf) { in =>
        new java.util.jar.Manifest(in)
      }
      Package.JarManifest(manifest)
    },
    mappings in (Compile, packageBin) += {
      (baseDirectory.value / "plugin.xml") -> "plugin.xml"
    },

    resolvers += Resolver.bintrayRepo("jpl-imce", "gov.nasa.jpl.imce"),
    resolvers += Resolver.bintrayRepo("tiwg", "org.omg.tiwg"),

    resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases",
    scalacOptions in (Compile, compile) += s"-P:artima-supersafe:config-file:${baseDirectory.value}/project/supersafe.cfg",
    scalacOptions in (Test, compile) += s"-P:artima-supersafe:config-file:${baseDirectory.value}/project/supersafe.cfg",
    scalacOptions in (Compile, doc) += "-Xplugin-disable:artima-supersafe",
    scalacOptions in (Test, doc) += "-Xplugin-disable:artima-supersafe",

    libraryDependencies ++= Seq (
      "org.eclipse.emf" % "org.eclipse.emf.ecore"
        % Versions.emf_ecore % "provided" withSources() withJavadoc(),

      "org.eclipse.emf" % "org.eclipse.emf.ecore.xmi"
        % Versions.emf_ecore % "provided" withSources() withJavadoc(),

      "org.eclipse.emf" % "org.eclipse.emf.common"
        % Versions.emf_ecore % "provided" withSources() withJavadoc()
    )
  )
  .dependsOnSourceProjectOrLibraryArtifacts(
    "oti-uml-core",
    "org.omg.oti.uml.core",
    Seq(
      "org.omg.tiwg" %% "org.omg.oti.uml.core"
        % Versions_oti_uml_core.version % "compile" withSources() withJavadoc() artifacts
        Artifact("org.omg.oti.uml.core", "zip", "zip", "resource")
    )
  )


def dynamicScriptsResourceSettings(projectName: String): Seq[Setting[_]] = {

  import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

  def addIfExists(f: File, name: String): Seq[(File, String)] =
    if (!f.exists) Seq()
    else Seq((f, name))

  val QUALIFIED_NAME = "^[a-zA-Z][\\w_]*(\\.[a-zA-Z][\\w_]*)*$".r
     

  Seq(
    // the '*-resource.zip' archive will start from: 'dynamicScripts'
    com.typesafe.sbt.packager.Keys.topLevelDirectory in Universal := None,

    // name the '*-resource.zip' in the same way as other artifacts
    com.typesafe.sbt.packager.Keys.packageName in Universal :=
      normalizedName.value + "_" + scalaBinaryVersion.value + "-" + version.value + "-resource",

    // contents of the '*-resource.zip' to be produced by 'universal:packageBin'
    mappings in Universal ++= {
      val dir = baseDirectory.value
      val bin = (packageBin in Compile).value
      val src = (packageSrc in Compile).value
      val doc = (packageDoc in Compile).value
      val binT = (packageBin in Test).value
      val srcT = (packageSrc in Test).value
      val docT = (packageDoc in Test).value

      (dir * ".classpath").pair(rebase(dir, projectName)) ++
      (dir * "*.md").pair(rebase(dir, projectName)) ++
      (dir / "resources" ***).pair(rebase(dir, projectName)) ++
      addIfExists(bin, projectName + "/lib/" + bin.name) ++
      addIfExists(binT, projectName + "/lib/" + binT.name) ++
      addIfExists(src, projectName + "/lib.sources/" + src.name) ++
      addIfExists(srcT, projectName + "/lib.sources/" + srcT.name) ++
      addIfExists(doc, projectName + "/lib.javadoc/" + doc.name) ++
      addIfExists(docT, projectName + "/lib.javadoc/" + docT.name)
    },

    artifacts += {
      val n = (name in Universal).value
      Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map())
    },
    packagedArtifacts += {
      val p = (packageBin in Universal).value
      val n = (name in Universal).value
      Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) -> p
    }
  )
}