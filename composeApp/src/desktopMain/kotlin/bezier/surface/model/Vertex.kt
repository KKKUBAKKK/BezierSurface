package bezier.surface.model

data class Vertex(
    val point: Point3D,
    val pu: Point3D,
    val pv: Point3D,
    val normal: Point3D,
    val u: Double,
    val v: Double
)