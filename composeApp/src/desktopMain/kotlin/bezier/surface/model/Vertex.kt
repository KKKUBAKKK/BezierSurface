package bezier.surface.model

data class Vertex(
    val point: Point3D = Point3D.Zero(),
    val pu: Point3D = Point3D.Zero(),
    val pv: Point3D = Point3D.Zero(),
    val normal: Point3D = Point3D.Zero(),
    val u: Double = 0.0,
    val v: Double = 0.0
)