package bezier.surface.viewmodel

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import bezier.surface.model.Point3D
import bezier.surface.model.Triangle
import java.io.File
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sin

class BezierSurfaceViewModel {
    // State holders
    var showWireframe by mutableStateOf(true)
    var showFilled by mutableStateOf(true)
    var lineColor by mutableStateOf(Color.White)
    var fillColor by mutableStateOf(Color.Magenta.copy(alpha = 0.3f))
    var lightColor by mutableStateOf(Color.White)
    var rotationX by mutableStateOf(0f)
    var rotationZ by mutableStateOf(0f)
    var resolution by mutableStateOf(3)
    var kd by mutableStateOf(0.5f)
    var ks by mutableStateOf(0.5f)
    var m by mutableStateOf(10f)

    // Control points for a 3x3 Bezier surface
//    private val controlPoints = loadControlPoints("src/desktopMain/resources/control_points1.txt")
    private val controlPoints = Array(4) { i ->
        Array(4) { j ->
            Point3D(
                x = i.toDouble() - 1.0,
                y = (if ((i + j) % 2 == 0) 0.5 else -0.5),
                z = j.toDouble() - 1.0
            )
        }
    }

    // Loading the control points
    fun loadControlPoints(fileName: String): Array<Array<Point3D>> {
        val points = Array(4) { Array(4) { Point3D(0.0, 0.0, 0.0) } }

        try {
            val lines = File(fileName).readLines()

            if (lines.size < 16) throw IOException("File does not contain enough control points")

            for ((index, line) in lines.withIndex()) {
                val (x, y, z) = line.split(" ").map { it.toDouble() }
                val row = index / 4
                val col = index % 4
                points[row][col] = Point3D(x, y, z)
            }
        } catch (e: IOException) {
            println("Error while reading from: ${e.message}")
        }
        return points
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
    private fun transformPoint(point: Point3D, canvasWidth: Float, canvasHeight: Float): Offset {
        // Step 1: Rotate around the X-axis
        val rotatedX1 = point.x
        val rotatedY1 = point.y * cos(rotationX) - point.z * sin(rotationX)
        val rotatedZ1 = point.y * sin(rotationX) + point.z * cos(rotationX)

        // Step 2: Rotate around the Z-axis
        val rotatedX2 = rotatedX1 * cos(rotationZ) - rotatedY1 * sin(rotationZ)
        val rotatedY2 = rotatedX1 * sin(rotationZ) + rotatedY1 * cos(rotationZ)
        val rotatedZ2 = rotatedZ1 // Z remains the same in rotation around the Z-axis

        // Simple perspective projection
        val scale = 200.0
        val perspectiveZ = 1.0 + rotatedZ2 * 0.2

        // Calculate the center of the canvas
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2

        return Offset(
            ((rotatedX2 / perspectiveZ) * scale + centerX).toFloat(),
            ((rotatedY2 / perspectiveZ) * scale + centerY).toFloat()
        )
    }

    // Adjusts the color to the light TODO: Implement lighting
    private fun adjustColor(color: Color, normal: Point3D): Color {
        val lightDirection = Point3D(0.0, 0.0, 1.0)
        val dotProduct = normal.x * lightDirection.x + normal.y * lightDirection.y + normal.z * lightDirection.z
        val intensity = if (dotProduct > 0) dotProduct else 0.0
        return color.copy(alpha = intensity.toFloat())
    }

    // Draw a line pixel by pixel
    private fun drawLinePixelByPixel(p1: Offset, p2: Offset, drawScope: DrawScope) {
        val x1 = p1.x
        val y1 = p1.y
        val x2 = p2.x
        val y2 = p2.y

        val dx = x2 - x1
        val dy = y2 - y1
        val steps = if (Math.abs(dx) > Math.abs(dy)) Math.abs(dx) else Math.abs(dy)
        val xIncrement = dx / steps
        val yIncrement = dy / steps

        var x = x1
        var y = y1

        for (i in 0..steps.toInt()) {
            drawScope.drawCircle(fillColor, radius = 1f, center = Offset(x, y))
            x += xIncrement
            y += yIncrement
        }
    }

    // Interpolate between two points
    private fun interpolate(y: Float, pA: Offset, pB: Offset): Offset {
        val t = (y - pA.y) / (pB.y - pA.y)
        val x = pA.x + t * (pB.x - pA.x)
        return Offset(x, y)
    }

    // Fill the triangle
    private fun fillTriangle(p1: Offset, p2: Offset, p3: Offset, drawScope: DrawScope) {
        // Sort vertices by y-coordinate (top to bottom)
        val vertices = listOf(p1, p2, p3).sortedBy { it.y }
        val top = vertices[0]
        val middle = vertices[1]
        val bottom = vertices[2]

        // Draw from top to middle
        for (y in top.y.toInt()..<middle.y.toInt()) {
            val left = interpolate(y.toFloat(), top, middle)
            val right = interpolate(y.toFloat(), top, bottom)
            drawLinePixelByPixel(left, right, drawScope)
        }

        // Draw from middle to bottom
        for (y in middle.y.toInt()..bottom.y.toInt()) {
            val left = interpolate(y.toFloat(), middle, bottom)
            val right = interpolate(y.toFloat(), top, bottom)
            drawLinePixelByPixel(left, right, drawScope)
        }
    }

    // Draw the surface
    fun drawSurface(drawScope: DrawScope) {
        val triangles = generateMesh(resolution)

        with(drawScope) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Draw filled triangles
            if (showFilled) {
                triangles.forEach { triangle ->
                    val p1 = transformPoint(triangle.v1, canvasWidth, canvasHeight)
                    val p2 = transformPoint(triangle.v2, canvasWidth, canvasHeight)
                    val p3 = transformPoint(triangle.v3, canvasWidth, canvasHeight)

                    fillTriangle(p1, p2, p3, drawScope)

//                    // Bucket sort the edges
//                    val edges = listOf(
//                        Pair(p1, p2),
//                        Pair(p2, p3),
//                        Pair(p3, p1)
//                    ).sortedBy { it.first.y }
//
//                    // Fill the triangle
//                    for (y in edges.first().first.y.toInt()..edges.last().first.y.toInt()) {
//                        val left = edges.firstOrNull { it.first.y <= y && it.second.y > y }
//                            ?.let { lerp(it.first, it.second, (y - it.first.y) / (it.second.y - it.first.y)) }
//                        val right = edges.lastOrNull { it.first.y <= y && it.second.y > y }
//                            ?.let { lerp(it.first, it.second, (y - it.first.y) / (it.second.y - it.first.y)) }
//
//                        if (left != null && right != null) {
//                            drawLine(Color.Magenta.copy(alpha = 0.3f), left, right)
//                        }
//                    }
                }
            }

            // Draw wireframe
            if (showWireframe) {
                triangles.forEach { triangle ->
                    val p1 = transformPoint(triangle.v1, canvasWidth, canvasHeight)
                    val p2 = transformPoint(triangle.v2, canvasWidth, canvasHeight)
                    val p3 = transformPoint(triangle.v3, canvasWidth, canvasHeight)

                    drawLine(lineColor, p1, p2)
                    drawLine(lineColor, p2, p3)
                    drawLine(lineColor, p3, p1)
                }
            }
        }
    }

//    private fun lerp(p1: Offset, p2: Offset, t: Float): Offset {
//        return Offset(
//            p1.x + (p2.x - p1.x) * t.toFloat(),
//            p1.y + (p2.y - p1.y) * t.toFloat()
//        )
//    }
}
