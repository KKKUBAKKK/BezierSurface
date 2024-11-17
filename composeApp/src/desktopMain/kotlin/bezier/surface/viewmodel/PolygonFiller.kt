package bezier.surface.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import bezier.surface.model.PhongParameters
import bezier.surface.model.Point2D
import bezier.surface.model.Point3D
import bezier.surface.model.Triangle2D
import bezier.surface.model.Vertex
import org.jetbrains.skia.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

//import bezier.surface.model.Edge

data class Edge(
    val yMaxIdx: Int,
    val yMax: Double,
    var yIdx: Int,
    var y: Double,
    var xIdx: Int,
    var x: Double,
    val slope: Double,
)

class PolygonFiller {
    private lateinit var ET: Array<MutableList<Edge>>
    private var AET = mutableListOf<Edge>()
    private var scanline = 0
    private var height = 0
    private var width = 0
    private var yOffset = height / 2
    private var xOffset = width / 2
    private lateinit var pixelBuffer: Array<IntArray>

    constructor(width: Int, height: Int, pixelBuffer: Array<IntArray>) {
        this.height = height
        this.width = width
        this.yOffset = height / 2
        this.xOffset = width / 2
        ET = Array(height + 1) { mutableListOf() }
        this.pixelBuffer = pixelBuffer
    }

    private fun yToIndex(y: Double): Int {
        return (y + yOffset).toInt()
    }

    private fun xToIndex(x: Double): Int {
        return (x + xOffset).toInt()
    }

    private fun loadEdgeTable(vertices: List<Point2D>) {
        for (i in 0 until vertices.size) {
            val v1 = vertices[i]
            val v2 = vertices[(i + 1) % vertices.size]

            if (abs(v1.y - v2.y) < 1) { // TODO: Handle horizontal lines
                val startX = xToIndex(min(v1.x, v2.x).toDouble())
                val endX = yToIndex(max(v1.x, v2.x).toDouble())
                val y = yToIndex(v1.y.toDouble())
                fillScanLine(startX, endX, y)
            }

            val start = if (v1.y < v2.y) v1 else v2
            val end = if (v1.y < v2.y) v2 else v1

            val edge = Edge(
                yMaxIdx = yToIndex(end.y.toDouble()) - 1,
                yMax = end.y.toDouble() - 1,
                yIdx = yToIndex(start.y.toDouble()),
                y = start.y.toDouble(),
                xIdx = xToIndex(start.x.toDouble()),
                x = start.x.toDouble(),
                slope = (end.x.toDouble() - start.x.toDouble()) / (end.y.toDouble() - start.y.toDouble())
            )

            ET[edge.yIdx].add(edge)
        }
    }

    private fun bucketSort() {
        val bucketCount = width + 1
        val buckets = Array(bucketCount) { mutableListOf<Edge>() }

        for (edge in AET) {
            buckets[edge.xIdx].add(edge)
        }

        AET.clear()
        for (bucket in buckets) {
            if (bucket.isNotEmpty()) {
                AET.addAll(bucket)
            }
        }
    }

    private fun fillScanLine() {
        for (i in 0 until AET.size - 1 step 2) {
            val left = AET[i]
            val right = AET[i + 1]

            val y = left.yIdx;
            val xStart = left.xIdx;
            val xEnd = right.xIdx;
            for (x in xStart .. xEnd) {
                pixelBuffer[x][y] = Color.Magenta.toArgb()
            }
        }
    }

    private fun fillScanLine(xStart: Int, xEnd: Int, y: Int) {
        for (x in xStart .. xEnd) {
            pixelBuffer[x][y] = Color.Magenta.toArgb()
        }
    }

    fun isETEmpty(): Boolean {
        return ET.firstOrNull() { it.isNotEmpty() } == null
    }

    fun fillPolygon(vertices: List<Point2D>): Array<IntArray> {
        loadEdgeTable(vertices)
        scanline = ET.indexOfFirst { it.isNotEmpty() }
        while (!isETEmpty() || AET.isNotEmpty())
        {
            // Move edges with yMin = scanline from ET to AET
            AET.addAll(ET[scanline])
            ET[scanline].clear()

            if (AET.size > 1) {
                // Sort AET by x
                bucketSort()

                // Fill scanline
                fillScanLine()
            }

            // Delete edges with yMax = scanline from AET
            AET.removeIf { it.yMaxIdx <= scanline }

            // Update the scanline
            scanline++;

            // Update x coordinates for next scanline (and indexes)
            for (edge in AET) {
                edge.y += 1
                edge.yIdx = yToIndex(edge.y)
                edge.x += edge.slope
                edge.xIdx = xToIndex(edge.x)
            }
        }

        return pixelBuffer
    }

    // Lighting experiments ------------------------------------------------------------------------
    fun calculatePhongLighting(
        point: Point3D,
        normal: Point3D,
        params: PhongParameters
    ): Color {
        // Normalize vectors
        val N = normal.normalize()

        // Calculate light direction vector (L)
        val L = (params.lightPosition - point).normalize()

        // View vector is [0, 0, 1] normalized
        val V = Point3D(0.0, 0.0, 1.0)

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
            return (diffuse + specular).coerceIn(0.0, 1.0).toFloat()
        }

        return Color(
            red = calculateComponent(params.lightColor.red, params.objectColor.red),
            green = calculateComponent(params.lightColor.green, params.objectColor.green),
            blue = calculateComponent(params.lightColor.blue, params.objectColor.blue),
            alpha = params.objectColor.alpha
        )
    }

    // Helper function to interpolate values using barycentric coordinates
    fun interpolateWithBarycentric(
        v1: Point3D,
        v2: Point3D,
        v3: Point3D,
        barycentricCoords: Triple<Double, Double, Double>
    ): Point3D {
        val (w1, w2, w3) = barycentricCoords
        return Point3D(
            x = w1 * v1.x + w2 * v2.x + w3 * v3.x,
            y = w1 * v1.y + w2 * v2.y + w3 * v3.y,
            z = w1 * v1.z + w2 * v2.z + w3 * v3.z
        )
    }

    // Calculate barycentric coordinates for a point inside a triangle
    fun calculateBarycentricCoordinates(
        point: Point2D,
        v1: Point2D,
        v2: Point2D,
        v3: Point2D
    ): Triple<Double, Double, Double> {
        val denominator = (v2.y - v3.y) * (v1.x - v3.x) + (v3.x - v2.x) * (v1.y - v3.y)

        val w1 = ((v2.y - v3.y) * (point.x - v3.x) + (v3.x - v2.x) * (point.y - v3.y)) / denominator
        val w2 = ((v3.y - v1.y) * (point.x - v3.x) + (v1.x - v3.x) * (point.y - v3.y)) / denominator
        val w3 = 1 - w1 - w2

        return Triple(w1, w2, w3)
    }

//    // Extension function to integrate with ScanlinePolygonFiller
//    fun calculatePixelColor(
//        point: Point2D,
//        triangle: Triangle2D,
//        params: PhongParameters
//    ): Color {
//        // Calculate barycentric coordinates for the point
//        val barycentricCoords = calculateBarycentricCoordinates(
//            point,
//            Point2D(triangle.v1.x, triangle.v1.y),
//            Point2D(triangle.v2.x, triangle.v2.y),
//            Point2D(triangle.v3.x, triangle.v3.y)
//        )
//
//        // Interpolate normal vector using barycentric coordinates
//        val interpolatedNormal = interpolateWithBarycentric(
//            triangle.vertex1.normal,
//            triangle.vertex2.normal,
//            triangle.vertex3.normal,
//            barycentricCoords
//        ).normalize()
//
//        // Interpolate z coordinate
//        val interpolatedZ = interpolateWithBarycentric(
//            Point3D(0.0, 0.0, triangle.v1.z.toDouble()),
//            Point3D(0.0, 0.0, triangle.v2.z.toDouble()),
//            Point3D(0.0, 0.0, triangle.v3.z.toDouble()),
//            barycentricCoords
//        ).z
//
//        // Create 3D point from 2D point and interpolated z
//        val point3D = Point3D(point.x, point.y, interpolatedZ)
//
//        return calculatePhongLighting(point3D, interpolatedNormal, params)
//    }
}