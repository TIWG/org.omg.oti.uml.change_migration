
object Versions {

  // this project
  val version = "445380.4"

  // https://github.jpl.nasa.gov/imce/org.omg.oti
  val oti_uml_core="445379.3"

  // Eclipse EMF (emf.ecore, emf.ecore.xmi, emf.common)
  // Note must be binary compatible with the EMF used in OTI adapters (MD, Papyrus)
  // otherwise there would have to be adapter-specific versions of this package.
  val emf_ecore = "2.10.1"
}
