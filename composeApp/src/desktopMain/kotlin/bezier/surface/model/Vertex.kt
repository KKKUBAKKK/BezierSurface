package bezier.surface.model

// Vertex structure representing a single vertex
data class Vertex(
    // Before rotation
    val pointBeforeRotation: Point3D,      // Point P before rotation
    val tangentUBeforeRotation: Point3D,   // Tangent vector Pu before rotation
    val tangentVBeforeRotation: Point3D,   // Tangent vector Pv before rotation
    val normalBeforeRotation: Point3D,     // Normal vector N before rotation

    // After rotation
    val pointAfterRotation: Point3D,       // Point P after rotation
    val tangentUAfterRotation: Point3D,    // Tangent vector Pu after rotation
    val tangentVAfterRotation: Point3D,    // Tangent vector Pv after rotation
    val normalAfterRotation: Point3D,      // Normal vector N after rotation

    // u, v parameters used to compute the vertex
    val u: Float,
    val v: Float
)