package bezier.surface.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import bezier.surface.model.Edge
import bezier.surface.model.PhongParameters
import bezier.surface.model.Point3D
import bezier.surface.model.Triangle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

//data class Edge(
//    val yMax: Int,
//    var y: Double,
//    var x: Double,
//    val slope: Double,
//)

class PolygonFiller(
    // Canvas size
    private var width: Int,
    private var height: Int,

    // Pixel buffer
    private var pixelBuffer: Array<IntArray>,

    // Phong parameters for lighting calculations
    private var phongParameters: PhongParameters,

    // Texture params
    isTexture: Boolean = false,
    texture: Array<Array<Color>>? = null
) {
    // Edge table and active edge table for scan line algorithm
    private lateinit var ET: Array<MutableList<Edge>>
    private var AET = mutableListOf<Edge>()

    // Scanline and offsets
    private var scanline = 0
    private var yOffset = height / 2
    private var xOffset = width / 2

    // Triangle before changing and vertices after
    private var triangle: Triangle? = null
    private lateinit var vertices: List<Point3D>

    // Texture params
    private var isTexture = isTexture
    private var texture = texture

    // Initialize edge table
    init {
        this.yOffset = height / 2
        this.xOffset = width / 2
        ET = Array(height + 1) { mutableListOf() }
    }

    // Load ET with edges index by yMin
    private fun loadEdgeTable(vertices: List<Point3D>) {
        for (i in 0 until vertices.size) {
            val v1 = vertices[i]
            val v2 = vertices[(i + 1) % vertices.size]

            if (abs(v1.y - v2.y) < 1) { // TODO: Handle horizontal lines
                val startX = min(v1.x, v2.x)
                val endX = max(v1.x, v2.x)
                val y = v1.y
                if (y < 0 || y >= height || startX < 0 || startX >= width || endX < 0 || endX >= width) {
                    continue
                }
                fillScanLine(startX.toInt(), endX.toInt(), y.toInt())
                continue
            }

            val start = if (v1.y < v2.y) v1 else v2
            val end = if (v1.y < v2.y) v2 else v1

            val edge = Edge(
                yMax = end.y.toInt() - 1,
                y = start.y,
                x = start.x,
                slope = (end.x - start.x) / (end.y - start.y) // TODO: jesli slope jest maly to moga byc problemy
            )

            ET[edge.y.toInt()].add(edge)
        }
    }

    // Bucket sort for AET
    private fun bucketSort() {
        val bucketCount = width + 1
        val buckets = Array(bucketCount) { mutableListOf<Edge>() }

        for (edge in AET) {
            buckets[edge.x.toInt()].add(edge)
        }

        AET.clear()
        for (bucket in buckets) {
            if (bucket.isNotEmpty()) {
                AET.addAll(bucket)
            }
        }
    }

    // Getting indexes to fill the line
    private fun fillScanLine() {
        for (i in 0 until AET.size - 1 step 2) {
            val left = AET[i]
            val right = AET[i + 1]

            val y = left.y.toInt();
            val xStart = left.x.toInt();
            val xEnd = right.x.toInt();
            fillScanLine(xStart, xEnd, y)
        }
    }

    // Filling the line with color
    private fun fillScanLine(xStart: Int, xEnd: Int, y: Int) {
        for (x in xStart..xEnd) {
            val color = calculatePhongLighting(x.toFloat(), y.toFloat(), vertices, triangle!!, phongParameters)
            pixelBuffer[x][y] = color.toArgb()
        }
    }

    // Checking if ET is empty
    fun isETEmpty(): Boolean {
        return ET.firstOrNull() { it.isNotEmpty() } == null
    }

    // Filling given triangle with color
    fun fillPolygon(triangle: Triangle, scale: Double = 50.0): Array<IntArray> {
        // Setting the triangle
        this.triangle = triangle

        // Creating vertices for screen
        vertices = listOf(
            triangle.v1.point * scale + Point3D(xOffset.toDouble(), yOffset.toDouble(), 0.0),
            triangle.v2.point * scale + Point3D(xOffset.toDouble(), yOffset.toDouble(), 0.0),
            triangle.v3.point * scale + Point3D(xOffset.toDouble(), yOffset.toDouble(), 0.0)
        )

        // Loading edge table with edges for the screen
        loadEdgeTable(vertices)

        // Finding the first non-empty scanline
        scanline = ET.indexOfFirst { it.isNotEmpty() }

        // Scanline algorithm
        while (!isETEmpty() || AET.isNotEmpty()) {
            // Move edges with yMin = scanline from ET to AET
            AET.addAll(ET[scanline])
            ET[scanline].clear()

            // If there is more than one edge in AET, sort them by x and fill the scanline
            if (AET.size > 1) {
                // Sort AET by x
                bucketSort()

                // Fill scanline
                fillScanLine()
            }

            // Delete edges with yMax = scanline from AET
            AET.removeIf { it.yMax <= scanline }

            // Update the scanline
            scanline++;

            // Update x coordinates for next scanline (and indexes)
            for (edge in AET) {
                edge.y += 1
                edge.x += edge.slope
            }
        }

        return pixelBuffer
    }

    // Lighting experiments ------------------------------------------------------------------------
    fun calculatePhongLighting(
        x: Float,
        y: Float,
        vertices: List<Point3D>,
        triangle: Triangle,
        // Phong parameters for lighting calculations
        params: PhongParameters
    ): Color {

        // Get coordinates
        val point = interpolatePoint(x, y, vertices, triangle)

        // Normalize vectors
        val N = interpolateNormal(point, triangle!!).normalize()

        // Calculate light direction vector (L)
        val L = (params.lightPosition - point).normalize()

        // View vector is [0, 0, 1] normalized
        val V = Point3D(0.0, 0.0, -5.0).normalize()

        // Calculate reflection vector (R)
        val NdotL = N.dot(L)
        val R = Point3D(
            2 * NdotL * N.x - L.x,
            2 * NdotL * N.y - L.y,
            2 * NdotL * N.z - L.z
        ).normalize()

        // Calculate diffuse component (cos between N and L)
        val diffuseFactor = max(0.0, NdotL)

        // Calculate specular component (cos between V and R)
        val specularFactor = max(0.0, V.dot(R)).pow(params.m)

        // Calculate color components separately
        fun calculateComponent(lightComponent: Float, objectComponent: Float): Float {
            val diffuse = params.kd * lightComponent * objectComponent * diffuseFactor
            val specular = params.ks * lightComponent * objectComponent * specularFactor
            return (diffuse + specular).coerceIn(0.0, 1.0).toFloat() * 255
        }

        var color = Color.Transparent
        if (!isTexture) {
            color = Color(
                red = calculateComponent(params.lightColor.x.toFloat(), params.objectColor.x.toFloat()),
                green = calculateComponent(params.lightColor.y.toFloat(), params.objectColor.y.toFloat()),
                blue = calculateComponent(params.lightColor.z.toFloat(), params.objectColor.z.toFloat()),
            )
        }
        else {
            val textureColor = getTextureColor(point.x, point.y, triangle)
            color = Color(
                red = calculateComponent(params.lightColor.x.toFloat(), textureColor.red),
                green = calculateComponent(params.lightColor.y.toFloat(), textureColor.green),
                blue = calculateComponent(params.lightColor.z.toFloat(), textureColor.blue),
            )
        }

        return color
    }

    // TODO: DO ZMIANY
    private fun interpolatePoint(
        x: Float,
        y: Float,
        vertices: List<Point3D>,
        triangle: Triangle
    ): Point3D {
        val v0 = vertices[0]
        val v1 = vertices[1]
        val v2 = vertices[2]

        val b = barycentric(x, y, v0, v1, v2)

        val p0 = triangle.v1.point
        val p1 = triangle.v2.point
        val p2 = triangle.v3.point

        return (p0 * b.first) + (p1 * b.second) + (p2 * b.third)
    }

    // Function for getting barycentric coordinates
    fun barycentric(
        x: Float,
        y: Float,
        v0: Point3D,
        v1: Point3D,
        v2: Point3D
    ): Triple<Double, Double, Double> {
        val p = Point3D(x.toDouble(), y.toDouble(), 0.0)
        val denominator = (v1.y - v2.y) * (v0.x - v2.x) + (v2.x - v1.x) * (v0.y - v2.y)
        val w0 = ((v1.y - v2.y) * (p.x - v2.x) + (v2.x - v1.x) * (p.y - v2.y)) / denominator
        val w1 = ((v2.y - v0.y) * (p.x - v2.x) + (v0.x - v2.x) * (p.y - v2.y)) / denominator
        val w2 = 1f - w0 - w1

        return Triple(w0, w1, w2)
    }

    fun interpolateUv(
        point: Point3D,
        triangle: Triangle
    ): Point3D {
        val b = barycentric(point.x.toFloat(), point.y.toFloat(), triangle.v1.point, triangle.v2.point, triangle.v3.point)

        val u0 = triangle.v1.u
        val u1 = triangle.v2.u
        val u2 = triangle.v3.u

        val v0 = triangle.v1.v
        val v1 = triangle.v2.v
        val v2 = triangle.v3.v

        var res = Point3D(
            (u0 * b.first) + (u1 * b.second) + (u2 * b.third),
            (v0 * b.first) + (v1 * b.second) + (v2 * b.third),
            0.0)

        return res
    }

    fun interpolateNormal(
        point: Point3D,
        triangle: Triangle
    ): Point3D {
        val v0 = triangle.v1.point
        val v1 = triangle.v2.point
        val v2 = triangle.v3.point

        val n0 = triangle.v1.normal
        val n1 = triangle.v2.normal
        val n2 = triangle.v3.normal

        val b = barycentric(point.x.toFloat(), point.y.toFloat(), v0, v1, v2)

        val interpolatedNormal = (n0 * b.first) + (n1 * b.second) + (n2 * b.third)

        return interpolatedNormal.normalize()
    }

    fun getTextureColor(
        x: Double,
        y: Double,
        triangle: Triangle
    ): Color {
        val uv = interpolateUv(Point3D(x, y, 0.0), triangle)
        val tx = (uv.x * (texture!!.size - 1)).toInt().coerceIn(0, texture!!.size - 1)
        val ty = (uv.y * (texture!![0].size - 1)).toInt().coerceIn(0, texture!![0].size - 1)
        return texture!![tx][ty]
    }
}