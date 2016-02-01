
import sbt.Keys._
import sbt._

import gov.nasa.jpl.imce.sbt._

useGpg := true

developers := List(
  Developer(
    id="rouquett",
    name="Nicolas F. Rouquette",
    email="nicolas.f.rouquette@jpl.nasa.gov",
    url=url("https://gateway.jpl.nasa.gov/personal/rouquett/default.aspx")))

lazy val buildUTCDate = SettingKey[String]("build-utc-date", "The UDC Date of the build")

buildUTCDate in Global := {
  import java.util.{ Date, TimeZone }
  val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm")
  formatter.setTimeZone(TimeZone.getTimeZone("UTC"))
  formatter.format(new Date)
}

lazy val core = Project("oti-uml-change_migration", file("."))
  .enablePlugins(IMCEGitPlugin)
  .enablePlugins(IMCEReleasePlugin)
  .settings(dynamicScriptsResourceSettings(Some("org.omg.oti.uml.change_migration")))
  .settings(IMCEPlugin.strictScalacFatalWarningsSettings)
  .settings(IMCEPlugin.scalaDocSettings(diagrams=false))
  .settings(
    IMCEKeys.licenseYearOrRange := "2014-2016",
    IMCEKeys.organizationInfo := IMCEPlugin.Organizations.oti,
    IMCEKeys.targetJDK := IMCEKeys.jdk17.value,

    organization := "org.omg.tiwg",
    organizationHomepage :=
      Some(url("http://www.omg.org/members/sysml-rtf-wiki/doku.php?id=rtf5:groups:tools_infrastructure:index")),

    buildInfoPackage := "org.omg.oti.uml.change_migration",
    buildInfoKeys ++= Seq[BuildInfoKey](BuildInfoKey.action("buildDateUTC") { buildUTCDate.value }),

    mappings in (Compile, packageSrc) ++= {
      import Path.{flat, relativeTo}
      val base = (sourceManaged in Compile).value
      val srcs = (managedSources in Compile).value
      srcs x (relativeTo(base) | flat)
    },

    projectID := {
      val previous = projectID.value
      previous.extra("build.date.utc" -> buildUTCDate.value)
    },

    git.baseVersion := Versions.version,
    organizationName := "JPL, Caltech & Object Management Group",
    organizationHomepage := Some(url("http://solitaire.omg.org/browse/TIWG")),

    scalaSource in Compile := baseDirectory.value / "svn" / "org.omg.oti.changeMigration" / "src" / "main" / "scala",

    classDirectory in Compile := baseDirectory.value / "svn" / "org.omg.oti.changeMigration" / "bin",
    cleanFiles += (classDirectory in Compile).value,

    packageOptions in (Compile, packageBin) += {
      val mf = baseDirectory.value / "svn" / "org.omg.oti.changeMigration" / "META-INF" / "MANIFEST.MF"
      val manifest = Using.fileInputStream(mf) { in =>
        new java.util.jar.Manifest(in)
      }
      Package.JarManifest(manifest)
    },
    mappings in (Compile, packageBin) += {
      (baseDirectory.value / "svn" / "org.omg.oti.changeMigration" / "plugin.xml") -> "plugin.xml"
    },

    libraryDependencies ++= Seq (
      "org.omg.tiwg" %% "oti-uml-core"
        % Versions.oti_uml_core % "compile" withSources() withJavadoc() artifacts
        Artifact("oti-uml-core", "resource", "zip", Some("resource"), Seq(), None, Map()),

      "org.eclipse.emf" % "org.eclipse.emf.ecore"
        % Versions.emf_ecore % "provided" withSources() withJavadoc(),

      "org.eclipse.emf" % "org.eclipse.emf.ecore.xmi"
        % Versions.emf_ecore % "provided" withSources() withJavadoc(),

      "org.eclipse.emf" % "org.eclipse.emf.common"
        % Versions.emf_ecore % "provided" withSources() withJavadoc()
    ),

    IMCEKeys.nexusJavadocRepositoryRestAPIURL2RepositoryName := Map(
       "https://oss.sonatype.org/service/local" -> "releases",
       "https://cae-nexuspro.jpl.nasa.gov/nexus/service/local" -> "JPL",
       "https://cae-nexuspro.jpl.nasa.gov/nexus/content/groups/jpl.beta.group" -> "JPL Beta Group",
       "https://cae-nexuspro.jpl.nasa.gov/nexus/content/groups/jpl.public.group" -> "JPL Public Group"),
    IMCEKeys.pomRepositoryPathRegex := """\<repositoryPath\>\s*([^\"]*)\s*\<\/repositoryPath\>""".r

  )
  .settings(IMCEReleasePlugin.packageReleaseProcessSettings)


def dynamicScriptsResourceSettings(dynamicScriptsProjectName: Option[String] = None): Seq[Setting[_]] = {

  import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

  def addIfExists(f: File, name: String): Seq[(File, String)] =
    if (!f.exists) Seq()
    else Seq((f, name))

  val QUALIFIED_NAME = "^[a-zA-Z][\\w_]*(\\.[a-zA-Z][\\w_]*)*$".r
     

  Seq(
    // the '*-resource.zip' archive will start from: 'dynamicScripts/<dynamicScriptsProjectName>'
    com.typesafe.sbt.packager.Keys.topLevelDirectory in Universal := {
      val projectName = dynamicScriptsProjectName.getOrElse(baseDirectory.value.getName)
      require(
        QUALIFIED_NAME.pattern.matcher(projectName).matches,
        s"The project name, '$projectName` is not a valid Java qualified name")
      Some("dynamicScripts/" + projectName)
    },

    // name the '*-resource.zip' in the same way as other artifacts
    com.typesafe.sbt.packager.Keys.packageName in Universal :=
      normalizedName.value + "_" + scalaBinaryVersion.value + "-" + version.value + "-resource",

    // contents of the '*-resource.zip' to be produced by 'universal:packageBin'
    mappings in Universal <++= (
      baseDirectory,
      packageBin in Compile,
      packageSrc in Compile,
      packageDoc in Compile,
      packageBin in Test,
      packageSrc in Test,
      packageDoc in Test) map {
      (base, bin, src, doc, binT, srcT, docT) =>
        val projectName = "org.omg.oti.changeMigration"
        val dir = base / "svn" / projectName
        (dir ** "*.dynamicScripts").pair(relativeTo(dir)) ++
          ((dir ** "*.md") --- (dir / "sbt.staging" ***)).pair(relativeTo(dir)) ++
          (dir / "models" ** "*.mdzip").pair(relativeTo(dir)) ++
          com.typesafe.sbt.packager.MappingsHelper.directory(dir / "resources") ++
          addIfExists(bin, "lib/" + bin.name) ++
          addIfExists(binT, "lib/" + binT.name) ++
          addIfExists(src, "lib.sources/" + src.name) ++
          addIfExists(srcT, "lib.sources/" + srcT.name) ++
          addIfExists(doc, "lib.javadoc/" + doc.name) ++
          addIfExists(docT, "lib.javadoc/" + docT.name)
    },

    artifacts <+= (name in Universal) { n => Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) },
    packagedArtifacts <+= (packageBin in Universal, name in Universal) map { (p, n) =>
      Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) -> p
    }
  )
}