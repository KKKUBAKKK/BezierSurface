package bezier.surface.model

data class Point2D(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val normal: Point3D
)