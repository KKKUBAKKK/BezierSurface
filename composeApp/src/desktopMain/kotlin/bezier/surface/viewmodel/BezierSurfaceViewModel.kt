package bezier.surface.viewmodel

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import bezier.surface.model.Mesh
import bezier.surface.model.PhongParameters
import bezier.surface.model.Point2D
import bezier.surface.model.Point3D
import bezier.surface.model.Triangle
import bezier.surface.model.Triangle2D
import bezier.surface.model.Vertex
import org.jetbrains.skia.Point
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class BezierSurfaceViewModel (
    val pathToPoints: String = "/Users/jakubkindracki/AndroidStudioProjects/BezierSurface/composeApp/src/desktopMain/resources/control_points.txt",
) {
    // Display mode parameters
    var showWireframe by mutableStateOf(true)
    var showFilled by mutableStateOf(false)
    var lineColor by mutableStateOf(Color.White)
    var fillColor by mutableStateOf(Point3D(1.0, 0.0, 0.0))

    // Surface parameters
    var rotationXBeta by mutableStateOf(0f)
    var rotationZAlpha by mutableStateOf(0f)
    var resolution by mutableStateOf(3)

    // Light parameters
    var lightColor by mutableStateOf(Point3D(1.0, 1.0, 1.0))
    var kd by mutableStateOf(0.5f)
    var ks by mutableStateOf(0.5f)
    var m by mutableStateOf(10f)
    private var currentTime by mutableStateOf(0f)
    private val spiralRadius = 100.0  // Adjust this to control how wide the spiral is
    private val spiralHeight = 5.0   // Fixed height of the light
    private val rotationSpeed = 0.1f // Adjust to control animation speed
    private val spiralTightness = 0.5 // Adjust to control how tight the spiral is

    // Canvas size
    var width: Int = 1600
    var height: Int = 1200

    // Buffers
    private var pixelBuffer by mutableStateOf(Array(width) { IntArray(height) })

    // Add this function to calculate light position based on time
    private fun calculateLightPosition(): Point3D {
        val x =
            spiralRadius * cos(currentTime.toDouble())// * (1.0 - spiralTightness * cos(currentTime.toDouble()))
        val y =
            spiralRadius * sin(currentTime.toDouble())// * (1.0 - spiralTightness * cos(currentTime.toDouble()))
        return Point3D(x, y, spiralHeight)
    }

    // Initialize control points from file
    private val controlPoints = read4x4ArrayFromFile(pathToPoints)

    // Mesh
    private var mesh: Mesh? = null

    // Function to read a 4x4 array of doubles from a file
    fun read4x4ArrayFromFile(filePath: String): Array<Array<Vertex>> {
        // Create a 4x4 array to hold the double values
        val array = Array(4) { Array(4) { Vertex() } }

        // Read the file line by line
        val lines = File(filePath).readLines()

        if (lines.size != 16) {
            throw IllegalArgumentException("The file must contain exactly 16 lines.")
        }

        // Parse the values into the array
        var index = 0
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                // Read and clean the corresponding line
                val line = lines[index++]
                    .replace(",", " ") // Replace commas with spaces
                    .split("\\s+".toRegex()) // Split by whitespace
                    .map { it.toDouble() } // Convert to doubles
                if (line.size != 3) {
                    throw IllegalArgumentException("Each line must contain exactly 3 values.")
                }
                val point = Point3D(line[0], line[1], line[2])
                array[i][j] = Vertex(point = point)
            }
        }

        return array
    }

    // Function that generates a mesh from the control points
    private fun generateMesh(resolution: Int): Mesh {
        val triangles = mutableListOf<Triangle>()
        val step = 1.0 / resolution

        for (i in 0 until resolution) {
            for (j in 0 until resolution) {
                val u1 = i * step
                val u2 = (i + 1) * step
                val v1 = j * step
                val v2 = (j + 1) * step

                val p1 = calculateSurfaceProperties(u1, v1)
                val p2 = calculateSurfaceProperties(u2, v1)
                val p3 = calculateSurfaceProperties(u1, v2)
                val p4 = calculateSurfaceProperties(u2, v2)

                triangles.add(Triangle(p1, p2, p3))
                triangles.add(Triangle(p2, p4, p3))
            }
        }

        return Mesh(
            triangles,
            width,
            height,
            rotationXBeta.toDouble(),
            rotationZAlpha.toDouble(),
            resolution
        )
    }

    private fun interpolateSurfacePoint(u: Double, v: Double): Point3D {
        var point = Point3D.Zero()

        // Loop through control points grid (4x4)
        for (i in 0..3) {
            for (j in 0..3) {
                val basisU = bernstein(i, 3, u)
                val basisV = bernstein(j, 3, v)
                point += controlPoints[i][j].point * (basisU * basisV)
            }
        }

        return point
    }

    // TODO: Check if this is correct
    private fun calculatePu(u: Double, v: Double): Point3D {
        var pu = Point3D.Zero()
        val n = 3  // degree of the surface in u direction

        // Sum up to n-1 because we're using differences between points
        for (i in 0..2) {
            for (j in 0..3) {
                // Calculate vector between adjacent control points in u direction
                val deltaU = controlPoints[i + 1][j].point - controlPoints[i][j].point
                val basisUPrime = bernstein(i, 2, u)  // n-1 degree for derivative
                val basisV = bernstein(j, 3, v)
                pu += deltaU * (n * basisUPrime * basisV)
            }
        }

        return pu
    }

    // TODO: Check if this is correct
    private fun calculatePv(u: Double, v: Double): Point3D {
        var pv = Point3D.Zero()
        val m = 3  // degree of the surface in v direction

        for (i in 0..3) {
            for (j in 0..2) {
                // Calculate vector between adjacent control points in v direction
                val deltaV = controlPoints[i][j + 1].point - controlPoints[i][j].point
                val basisU = bernstein(i, 3, u)
                val basisVPrime = bernstein(j, 2, v)  // m-1 degree for derivative
                pv += deltaV * (m * basisU * basisVPrime)
            }
        }

        return pv
    }

    private fun calculateNormal(pu: Point3D, pv: Point3D): Point3D {
        return (pu cross pv).normalized()
    }

    private fun calculateSurfaceProperties(u: Double, v: Double): Vertex {
        val point = interpolateSurfacePoint(u, v)
        val pu = calculatePu(u, v)
        val pv = calculatePv(u, v)
        val normal = calculateNormal(pu, pv)

        return Vertex(
            point = point,
            pu = pu,
            pv = pv,
            normal = normal,
            u = u,
            v = v
        )
    }

    // Function to calculate the Bernstein basis function
    private fun bernstein(i: Int, n: Int, t: Double): Double {
        fun binomial(n: Int, k: Int): Int {
            if (k == 0 || k == n) return 1
            return binomial(n - 1, k - 1) + binomial(n - 1, k)
        }

        return binomial(n, i) * Math.pow(t, i.toDouble()) * Math.pow(1 - t, (n - i).toDouble())
    }


    // Function to transform a 3D point to a 2D point on the screen
    private fun transformPoint(vertex: Vertex, canvasWidth: Float, canvasHeight: Float): Point2D {
        val rotatedVertex = rotatePoint(vertex, rotationZAlpha, rotationXBeta)
        val scale = 30.0
        val perspectiveZ = 1.0// + rotatedVertex.point.z * 0.2

        return Point2D(
            ((rotatedVertex.point.x / perspectiveZ) * scale) + canvasWidth / 2,
            ((rotatedVertex.point.y / perspectiveZ) * scale) + canvasHeight / 2
        )
    }

    // Function to rotate a 3D triangle around X and Z axes
    fun rotateTriangle(triangle: Triangle, alpha: Float, beta: Float): Triangle {
        val v1 = rotatePoint(triangle.v1, alpha, beta)
        val v2 = rotatePoint(triangle.v2, alpha, beta)
        val v3 = rotatePoint(triangle.v3, alpha, beta)
        return Triangle(v1, v2, v3)
    }

    // TODO: KONIECZNIE ZAMIEN
    // Function to rotate a vertex around X and Z axes, preserving UV coordinates and rotating the normal
    fun rotatePoint(vertex: Vertex, alpha: Float, beta: Float): Vertex {
        val point = vertex.point
        val normal = vertex.normal

        // Rotation around Z-axis (alpha) for position
        val x1 = point.x * cos(alpha) - point.y * sin(alpha)
        val y1 = point.x * sin(alpha) + point.y * cos(alpha)

        // Rotation around X-axis (beta) for position
        val z1 = point.z * cos(beta) - y1 * sin(beta)
        val y2 = point.z * sin(beta) + y1 * cos(beta)

        // Rotate the normal vector similarly to the position
        val nx1 = normal.x * cos(alpha) - normal.y * sin(alpha)
        val ny1 = normal.x * sin(alpha) + normal.y * cos(alpha)

        val nz1 = normal.z * cos(beta) - ny1 * sin(beta)
        val ny2 = normal.z * sin(beta) + ny1 * cos(beta)

        // Return a new Vertex with the rotated position and normal, and the same UV coordinates
        return Vertex(
            point = Point3D(x1, y2, z1),
            normal = Point3D(nx1, ny2, nz1).normalize(), // Normalize the rotated normal
            u = vertex.u,
            v = vertex.v
        )
    }

    fun drawSurface(drawScope: DrawScope, width: Int, height: Int) {
        // Update time
        currentTime += rotationSpeed

        // Calculate new light position
        val lightPosition = calculateLightPosition()
//        val lightPosition = Point3D(0.0, 0.0, 5.0)

        if (mesh == null || mesh?.canvasWidth != width || mesh?.canvasHeight != height || mesh?.resolution != resolution) {
            mesh = generateMesh(resolution)
        }

        // Reset buffers
        pixelBuffer = Array(width) { IntArray(height) }

        val rotatedTriangles =
            mesh?.triangles?.map { rotateTriangle(it, rotationZAlpha, rotationXBeta) }

        with(drawScope) {
            if (showFilled) {
                val phong = PhongParameters(
                    kd.toDouble(),
                    ks.toDouble(),
                    m.toDouble(),
                    lightColor,
                    fillColor,
                    lightPosition
                )
                val polygonFiller = PolygonFiller(width, height, pixelBuffer, phong)

                rotatedTriangles?.forEach {
                    polygonFiller.fillPolygon(it)
                }

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val color = Color(pixelBuffer[x][y])
                        if (color != Color.Transparent) {
                            drawRect(
                                color = color,
                                topLeft = Offset(x.toFloat(), y.toFloat()),
                                size = Size(1f, 1f)
                            )
                        }
                    }
                }
            }

            if (showWireframe) {
                mesh?.triangles?.forEach { triangle ->
                    val p1 = transformPoint(triangle.v1, width.toFloat(), height.toFloat())
                    val p2 = transformPoint(triangle.v2, width.toFloat(), height.toFloat())
                    val p3 = transformPoint(triangle.v3, width.toFloat(), height.toFloat())

                    drawScope.drawLine(
                        lineColor,
                        Offset(p1.x.toFloat(), p1.y.toFloat()),
                        Offset(p2.x.toFloat(), p2.y.toFloat())
                    )
                    drawScope.drawLine(
                        lineColor,
                        Offset(p2.x.toFloat(), p2.y.toFloat()),
                        Offset(p3.x.toFloat(), p3.y.toFloat())
                    )
                    drawScope.drawLine(
                        lineColor,
                        Offset(p3.x.toFloat(), p3.y.toFloat()),
                        Offset(p1.x.toFloat(), p1.y.toFloat())
                    )
                }
            }
        }
    }
}