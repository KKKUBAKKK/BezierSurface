package bezier.surface.model

data class Point3D(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Point3D) = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun times(scalar: Double) = Point3D(x * scalar, y * scalar, z * scalar)
}