package bezier.surface.model

data class Point2D(
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val normal: Point3D
) {
    constructor(x: Double, y: Double) : this(x, y, 0.0, Point3D(0.0, 0.0, 1.0))
}