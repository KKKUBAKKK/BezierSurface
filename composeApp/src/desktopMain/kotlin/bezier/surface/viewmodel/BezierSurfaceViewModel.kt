package bezier.surface.viewmodel

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import bezier.surface.model.Point3D
import bezier.surface.model.Triangle
import kotlin.math.cos
import kotlin.math.sin

class BezierSurfaceViewModel {
    // State holders
    var showWireframe by mutableStateOf(true)
    var showFilled by mutableStateOf(true)
    var rotationX by mutableStateOf(0f)
    var rotationZ by mutableStateOf(0f)
    var resolution by mutableStateOf(10)

    // Control points for a 3x3 Bezier surface
    private val controlPoints = Array(3) { i ->
        Array(3) { j ->
            Point3D(
                x = i.toDouble() - 1.0,
                y = (if ((i + j) % 2 == 0) 0.5 else -0.5),
                z = j.toDouble() - 1.0
            )
        }
    }

    // Bernstein polynomial calculation
    private fun bernstein(i: Int, n: Int, t: Double): Double {
        fun binomial(n: Int, k: Int): Int {
            if (k == 0 || k == n) return 1
            return binomial(n - 1, k - 1) + binomial(n - 1, k)
        }

        return binomial(n, i) * Math.pow(t, i.toDouble()) * Math.pow(1 - t, (n - i).toDouble())
    }

    // Calculate point on Bezier surface
    fun calculateSurfacePoint(u: Double, v: Double): Point3D {
        var point = Point3D(0.0, 0.0, 0.0)

        for (i in 0..2) {
            for (j in 0..2) {
                val basis = bernstein(i, 2, u) * bernstein(j, 2, v)
                point += controlPoints[i][j] * basis
            }
        }

        return point
    }

    // Generate mesh of triangles
    fun generateMesh(resolution: Int): List<Triangle> {
        val triangles = mutableListOf<Triangle>()
        val step = 1.0 / resolution

        for (i in 0 until resolution) {
            for (j in 0 until resolution) {
                val u1 = i * step
                val u2 = (i + 1) * step
                val v1 = j * step
                val v2 = (j + 1) * step

                val p1 = calculateSurfacePoint(u1, v1)
                val p2 = calculateSurfacePoint(u2, v1)
                val p3 = calculateSurfacePoint(u1, v2)
                val p4 = calculateSurfacePoint(u2, v2)

                triangles.add(Triangle(p1, p2, p3))
                triangles.add(Triangle(p2, p4, p3))
            }
        }

        return triangles
    }

    // Transform point for display
    private fun transformPoint(point: Point3D): Offset {
//        // Apply rotation around X axis
//        val rotatedX = point.x
//        val rotatedY = point.y * cos(rotationX.toDouble()) - point.z * sin(rotationX.toDouble())
//        val rotatedZ = point.y * sin(rotationX.toDouble()) + point.z * cos(rotationX.toDouble())
//
//        // Apply rotation around Z axis
//        val rotatedX2 = rotatedX * cos(rotationZ.toDouble()) - rotatedY * sin(rotationZ.toDouble())
//        val rotatedY2 = rotatedX * sin(rotationZ.toDouble()) + rotatedY * cos(rotationZ.toDouble())
//        val rotatedZ2 = rotatedZ
//
//        // Simple perspective projection
//        val scale = 200.0
//        val perspectiveZ = 1.0 + rotatedZ2 * 0.2
//
//        return Offset(
//            ((rotatedX2 / perspectiveZ) * scale + 400).toFloat(),
//            ((rotatedY2 / perspectiveZ) * scale + 300).toFloat()
//        )

        // Step 1: Rotate around the X-axis
        val rotatedX1 = point.x
        val rotatedY1 = point.y * cos(rotationX) - point.z * sin(rotationX)
        val rotatedZ1 = point.y * sin(rotationX) + point.z * cos(rotationX)

        // Step 2: Rotate around the Z-axis (reinterpreted as rotating in the X-Z plane)
        val rotatedX2 = rotatedX1 * cos(rotationZ) - rotatedZ1 * sin(rotationZ)
        val rotatedZ2 = rotatedX1 * sin(rotationZ) + rotatedZ1 * cos(rotationZ)
        val rotatedY2 = rotatedY1 // Y remains the same in rotation around the Z-axis

        // Simple perspective projection
        val scale = 200.0
        val perspectiveZ = 1.0 + rotatedZ2 * 0.2

        return Offset(
            ((rotatedX2 / perspectiveZ) * scale + 400).toFloat(),
            ((rotatedY2 / perspectiveZ) * scale + 300).toFloat()
        )

//        // Step 1: Rotate around the X-axis
//        val rotatedX1 = point.x
//        val rotatedY1 = point.y * cos(rotationX.toDouble()) - point.z * sin(rotationX.toDouble())
//        val rotatedZ1 = point.y * sin(rotationX.toDouble()) + point.z * cos(rotationX.toDouble())
//
//        // Step 2: Rotate around the Z-axis
//        val rotatedX2 = rotatedX1 * cos(rotationZ.toDouble()) - rotatedY1 * sin(rotationZ.toDouble())
//        val rotatedY2 = rotatedX1 * sin(rotationZ.toDouble()) + rotatedY1 * cos(rotationZ.toDouble())
//        val rotatedZ2 = rotatedZ1 // Z remains the same in rotation around the Z-axis
//
//        // Simple perspective projection
//        val scale = 200.0
//        val perspectiveZ = 1.0 + rotatedZ2 * 0.2
//
//        return Offset(
//            ((rotatedX2 / perspectiveZ) * scale + 400).toFloat(),
//            ((rotatedY2 / perspectiveZ) * scale + 300).toFloat()
//        )
    }

    // Draw the surface
    fun drawSurface(drawScope: DrawScope) {
        val triangles = generateMesh(resolution)

        with(drawScope) {
            // Draw filled triangles
            if (showFilled) {
                triangles.forEach { triangle ->
                    val p1 = transformPoint(triangle.v1)
                    val p2 = transformPoint(triangle.v2)
                    val p3 = transformPoint(triangle.v3)

                    // Bucket sort the edges
                    val edges = listOf(
                        Pair(p1, p2),
                        Pair(p2, p3),
                        Pair(p3, p1)
                    ).sortedBy { it.first.y }

                    // Fill the triangle
                    for (y in edges.first().first.y.toInt()..edges.last().first.y.toInt()) {
                        val left = edges.firstOrNull { it.first.y <= y && it.second.y > y }
                            ?.let { lerp(it.first, it.second, (y - it.first.y) / (it.second.y - it.first.y)) }
                        val right = edges.lastOrNull { it.first.y <= y && it.second.y > y }
                            ?.let { lerp(it.first, it.second, (y - it.first.y) / (it.second.y - it.first.y)) }

                        if (left != null && right != null) {
                            drawLine(Color.Magenta.copy(alpha = 0.3f), left, right)
                        }
                    }
                }
            }

            // Draw wireframe
            if (showWireframe) {
                triangles.forEach { triangle ->
                    val p1 = transformPoint(triangle.v1)
                    val p2 = transformPoint(triangle.v2)
                    val p3 = transformPoint(triangle.v3)

                    drawLine(Color.White, p1, p2)
                    drawLine(Color.White, p2, p3)
                    drawLine(Color.White, p3, p1)
                }
            }
        }
    }

    private fun lerp(p1: Offset, p2: Offset, t: Float): Offset {
        return Offset(
            p1.x + (p2.x - p1.x) * t.toFloat(),
            p1.y + (p2.y - p1.y) * t.toFloat()
        )
    }
}
