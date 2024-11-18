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
import bezier.surface.model.Vertex
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class BezierSurfaceViewModel (
    val pathToPoints: String = "src/Resources/control_points/control_points.txt",
) {
    // Display mode parameters
    var showWireframe by mutableStateOf(true)
    var showFilled by mutableStateOf(false)
    var lineColor by mutableStateOf(Color.White)
    var fillColor by mutableStateOf(Point3D(0.5, 0.0, 0.5))
    var scale = 50.0

    // Surface parameters
    var rotationXBeta by mutableStateOf(3.14f / 2)
    var rotationZAlpha by mutableStateOf(3.14f / 2)
    var resolution by mutableStateOf(3)

    // Light parameters
    var lightMove  by mutableStateOf(false)
    var lightPosition by mutableStateOf(Point3D(0.0, 0.0, 2.5))
    var lightColor by mutableStateOf(Point3D(1.0, 1.0, 1.0))
    var kd by mutableStateOf(0.05f)
    var ks by mutableStateOf(0.5f)
    var m by mutableStateOf(10f)
    private var currentTime by mutableStateOf(0f)
    private val spiralRadius = 5.0  // Adjust this to control how wide the spiral is
    private val spiralHeight = 2.5   // Fixed height of the light
    private val rotationSpeed = 0.1f // Adjust to control animation speed
//    private val spiralTightness = 0.5 // Adjust to control how tight the spiral is

    // Normal mapping parameters
    var isMapping = false
    var normalMap = arrayOf<Array<Point3D>>()

    // Texture parameters
    var isTexture = false
    var texture = arrayOf<Array<Color>>()

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

//    // Initialize control points from file
    private val controlPoints = readFromFile(pathToPoints)

    // Mesh
    private var mesh: Mesh? = null

    // Function to read a 4x4 array of doubles from a file
    fun readFromFile(filePath: String): List<Point3D> {
        val list = mutableListOf<Point3D>()

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
                list.add(point)
            }
        }

        return list
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

                var p1 = calculateSurfaceProperties(u1, v1)
                var p2 = calculateSurfaceProperties(u2, v1)
                var p3 = calculateSurfaceProperties(u1, v2)
                var p4 = calculateSurfaceProperties(u2, v2)

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

        // Loop through control points
        for (i in 0..3) {
            for (j in 0..3) {
                val basisU = bernstein(i, 3, u)
                val basisV = bernstein(j, 3, v)
                point += controlPoints[i * 4 + j] * (basisU * basisV)
            }
        }

        return point
    }

    private fun calculatePu(u: Double, v: Double): Point3D {
        var pu = Point3D.Zero()

        for (i in 0..2) {
            for (j in 0..3) {

                val basisV = bernstein(j, 3, v)
                // Calculate the derivative of the Bernstein polynomial with respect to u
                val basisUPrime = if (i == 0) {
                    -3 * (1 - u).pow(2)
                } else if (i == 1) {
                    3 * (1 - u).pow(2) - 6 * u * (1 - u)
                } else if (i == 2) {
                    6 * u * (1 - u) - 3 * u.pow(2)
                } else {
                    3 * u.pow(2)
                }

                pu += controlPoints[i * 4 + j] * (basisUPrime * basisV)
            }
        }

        return pu
    }

    private fun calculatePv(u: Double, v: Double): Point3D {
        var pv = Point3D.Zero()

        for (i in 0..3) {
            for (j in 0..3) {
                val basisU = bernstein(i, 3, u)
                // Calculate the derivative of the Bernstein polynomial with respect to v
                val basisVPrime = if (j == 0) {
                    -3 * (1 - v).pow(2)
                } else if (j == 1) {
                    3 * (1 - v).pow(2) - 6 * v * (1 - v)
                } else if (j == 2) {
                    6 * v * (1 - v) - 3 * v.pow(2)
                } else {
                    3 * v.pow(2)
                }

                pv += controlPoints[i * 4 + j] * (basisU * basisVPrime)
            }
        }

        return pv
    }

    private fun calculateNormal(pu: Point3D, pv: Point3D): Point3D {
        val normal = pu cross pv

        return if (normal.z < 0) {
            (normal * (-1).toDouble()).normalized()
        } else {
            normal.normalized()
        }
    }

    private fun calculateNormalFromMap(u: Double, v: Double): Point3D {
        val x = (u * (normalMap.size - 1)).coerceIn(0.0, normalMap.size - 2.0).toInt()
        val y = (v * (normalMap[0].size - 1)).coerceIn(0.0, normalMap[0].size - 2.0).toInt()

        val tl = normalMap[x][y]
        val tr = normalMap[x + 1][y]
        val bl = normalMap[x][y + 1]
        val br = normalMap[x + 1][y + 1]

        val dx = u * (normalMap.size - 1) - x
        val dy = v * (normalMap[0].size - 1) - y

        val top = tl * (1 - dx) + tr * dx
        val bottom = bl * (1 - dx) + br * dx

        val res = top * (1 - dy) + bottom * dy

        return res
    }

    private fun calculateSurfaceProperties(u: Double, v: Double): Vertex {
        val point = interpolateSurfacePoint(u, v)
        val pu = calculatePu(u, v)
        val pv = calculatePv(u, v)
        var normal = calculateNormal(pu, pv)

        if (isMapping) {
            normal = calculateNormalFromMap(u, v)
        }

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
        val rotatedVertex = rotateVertex(vertex, rotationZAlpha, rotationXBeta)
        val scale = scale
        val perspectiveZ = 1.0// + rotatedVertex.point.z * 0.2

        return Point2D(
            ((rotatedVertex.point.x / perspectiveZ) * scale) + canvasWidth / 2,
            ((rotatedVertex.point.y / perspectiveZ) * scale) + canvasHeight / 2
        )
    }

    // Function to rotate a 3D triangle around X and Z axes
    fun rotateTriangle(triangle: Triangle, alpha: Float, beta: Float): Triangle {
        val v1 = rotateVertex(triangle.v1, alpha, beta)
        val v2 = rotateVertex(triangle.v2, alpha, beta)
        val v3 = rotateVertex(triangle.v3, alpha, beta)
        return Triangle(v1, v2, v3)
    }

    // Function to rotate a 3D vertex around X and Z axes
    fun rotateVertex(vertex: Vertex, alpha: Float, beta: Float): Vertex {
        // Create rotation matrices
        val cosA = cos(alpha)
        val sinA = sin(alpha)
        val cosB = cos(beta)
        val sinB = sin(beta)

        // Rotate around Z-axis
        val x1 = vertex.point.x * cosA - vertex.point.y * sinA
        val y1 = vertex.point.x * sinA + vertex.point.y * cosA
        val z1 = vertex.point.z

        // Rotate around X-axis
        val x2 = x1
        val y2 = z1 * cosB - y1 * sinB
        val z2 = z1 * sinB + y1 * cosB

        // Apply the same rotation to the normal vector
        val nx1 = vertex.normal.x * cosA - vertex.normal.y * sinA
        val ny1 = vertex.normal.x * sinA + vertex.normal.y * cosA
        val nz1 = vertex.normal.z

        val nx2 = nx1
        val ny2 = nz1 * cosB - ny1 * sinB
        val nz2 = nz1 * sinB + ny1 * cosB

        return Vertex(
            point = Point3D(x2, y2, z2),
            normal = Point3D(nx2, ny2, nz2).normalize(),
            u = vertex.u,
            v = vertex.v
        )
    }

    fun drawSurface(drawScope: DrawScope, width: Int, height: Int) {

        if (lightMove) {
            // Update time
            currentTime += rotationSpeed

            // Calculate new light position
            lightPosition = calculateLightPosition()
        }

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
                val polygonFiller = PolygonFiller(width, height, pixelBuffer, phong, isTexture, texture)

                rotatedTriangles?.forEach {
                    polygonFiller.fillPolygon(it, scale)
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

    fun newMapping(path: String?) {
        isMapping = true
        normalMap = fillNormalMap(path!!)
    }

    fun newTexture(path: String?) {
        isTexture = true
        texture = fillTexture(path!!)
    }

    fun fillNormalMap(path: String): Array<Array<Point3D>> {
        val file = File(path)
        val normalImage = ImageIO.read(file)
        val normalMap = Array(normalImage.width) { Array(normalImage.height) { Point3D.Zero() } }

        for (x in 0 until normalImage.width) {
            for (y in 0 until normalImage.height) {
                val rgb = normalImage.getRGB(x, y)
                val r = (rgb shr 16 and 0xFF) / 255f
                val g = (rgb shr 8 and 0xFF) / 255f
                val b = (rgb and 0xFF) / 255f

                val normal = Point3D(
                    x = 2.0 * r - 1.0,
                    y = 2.0 * g - 1.0,
                    z = 2.0 * b - 1.0
                ).normalize()

                normalMap[x][y] = normal
            }
        }

        return normalMap
    }

    fun fillTexture(path: String): Array<Array<Color>> {
        val file = File(path)
        val textureImage = ImageIO.read(file)
        val texture = Array(textureImage.width) { Array(textureImage.height) { Color.Black } }

        for (x in 0 until textureImage.width) {
            for (y in 0 until textureImage.height) {
                val rgb = textureImage.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF
                val red = (rgb shr 16) and 0xFF
                val green = (rgb shr 8) and 0xFF
                val blue = rgb and 0xFF

                texture[x][y] = Color(
                    red = red / 255f,
                    green = green / 255f,
                    blue = blue / 255f,
                    alpha = alpha / 255f
                )
            }
        }

        return texture
    }
}