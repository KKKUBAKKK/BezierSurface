package bezier.surface.model

data class Point3D(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: Point3D) = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun times(scalar: Double) = Point3D(x * scalar, y * scalar, z * scalar)

    // Function to calculate the magnitude (length) of the point
    fun magnitude(): Float {
        return Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }

    // Function to normalize the point
    fun normalized(): Point3D {
        val mag = magnitude()
        return if (mag != 0f) {
            Point3D(x / mag, y / mag, z / mag)
        } else {
            this  // Return the point as is if its magnitude is 0
        }
    }
}