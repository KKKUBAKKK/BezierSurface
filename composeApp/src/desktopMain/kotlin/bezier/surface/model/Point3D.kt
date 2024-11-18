package bezier.surface.model

data class Point3D(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: Point3D) = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun times(scalar: Double) = Point3D(x * scalar, y * scalar, z * scalar)
    operator fun minus(other: Point3D) = Point3D(x - other.x, y - other.y, z - other.z)
    operator fun div(scalar: Double) = Point3D(x / scalar, y / scalar, z / scalar)

    fun length() = kotlin.math.sqrt(x * x + y * y + z * z)

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

    fun normalize(): Point3D {
        val length = kotlin.math.sqrt(x * x + y * y + z * z)
        return Point3D(x / length, y / length, z / length)
    }

    fun dot(other: Point3D): Double {
        return x * other.x + y * other.y + z * other.z
    }

    // Function to calculate the cross product of two points
    infix fun cross(v2: Point3D): Point3D {
        val x = y * v2.z - z * v2.y
        val y = z * v2.x - x * v2.z
        val z = x * v2.y - y * v2.x
        return Point3D(x, y, z)
    }

    companion object {
        fun Zero() = Point3D(0.0, 0.0, 0.0)
    }
}