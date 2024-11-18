package bezier.surface.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import bezier.surface.model.Edge
import bezier.surface.model.PhongParameters
import bezier.surface.model.Point2D
import bezier.surface.model.Point3D
import bezier.surface.model.Triangle
import bezier.surface.model.Triangle2D
import bezier.surface.model.Vertex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

//import bezier.surface.model.Edge

//data class Edge(
//    val yMaxIdx: Int,
//    val yMax: Double,
//    var yIdx: Int,
//    var y: Double,
//    var xIdx: Int,
//    var x: Double,
//    val slope: Double,
//)

class PolygonFiller(
    private var width: Int,
    private var height: Int,
    private var pixelBuffer: Array<IntArray>,
    private var phongParameters: PhongParameters
) {
    private lateinit var ET: Array<MutableList<Edge>>
    private var AET = mutableListOf<Edge>()
    private var scanline = 0
    private var yOffset = height / 2
    private var xOffset = width / 2
    private var triangle: Triangle? = null
    private lateinit var vertices: List<Point3D>

    init {
        this.yOffset = height / 2
        this.xOffset = width / 2
        ET = Array(height + 1) { mutableListOf() }
    }

    private fun yToIndex(y: Double): Int {
//        return (y + yOffset).toInt()
        return (y).toInt()
    }

    private fun xToIndex(x: Double): Int {
//        return (x + xOffset).toInt()
        return (x).toInt()
    }

    private fun loadEdgeTable(vertices: List<Point3D>) {
        for (i in 0 until vertices.size) {
            val v1 = vertices[i]
            val v2 = vertices[(i + 1) % vertices.size]

            if (abs(v1.y - v2.y) < 1) { // TODO: Handle horizontal lines
                val startX = xToIndex(min(v1.x, v2.x).toDouble())
                val endX = yToIndex(max(v1.x, v2.x).toDouble())
                val y = yToIndex(v1.y.toDouble())
                if (y < 0 || y >= height || startX < 0 || startX >= width || endX < 0 || endX >= width) {
                    continue
                }
                fillScanLine(startX, endX, y)
                continue
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
            fillScanLine(xStart, xEnd, y)
        }
    }

    private fun fillScanLine(xStart: Int, xEnd: Int, y: Int) {
        for (x in xStart .. xEnd) {
            val point = interpolateBarycentric(x.toFloat(), y.toFloat(), vertices, triangle!!)
            val color = calculatePhongLighting(point, phongParameters)
            pixelBuffer[x][y] = color.toArgb()
//            val point = Point2D((x - xOffset).toDouble(), (y - yOffset).toDouble())
//            val color = calculatePixelColor(point, triangle!!, phongParameters)
//            pixelBuffer[x][y] = color.toArgb()
        }
    }

    fun isETEmpty(): Boolean {
        return ET.firstOrNull() { it.isNotEmpty() } == null
    }

    // TODO: moze trzeba by na wektorach dodatnich zrobic
    fun fillPolygon(triangle: Triangle): Array<IntArray> {
        val scale = 30.0 // TODO: sprawdzic zeby nie wyszlo poza skale
        this.triangle = triangle
        vertices = listOf(
            triangle.v1.point * scale + Point3D(xOffset.toDouble(), yOffset.toDouble(), 0.0),
            triangle.v2.point * scale + Point3D(xOffset.toDouble(), yOffset.toDouble(), 0.0),
            triangle.v3.point * scale + Point3D(xOffset.toDouble(), yOffset.toDouble(), 0.0)
        )
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
        params: PhongParameters
    ): Color {
        // Normalize vectors
        val N = interpolateNormal(point, triangle!!)

        // Calculate light direction vector (L)
        val L = (params.lightPosition - point).normalize()

        // View vector is [0, 0, 1] normalized
        val V = Point3D(0.0, 0.0, -5.0)

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

        return Color(
            red = calculateComponent(params.lightColor.x.toFloat(), params.objectColor.x.toFloat()),
            green = calculateComponent(params.lightColor.y.toFloat(), params.objectColor.y.toFloat()),
            blue = calculateComponent(params.lightColor.z.toFloat(), params.objectColor.z.toFloat()),
        )
    }

    // TODO: DO ZMIANY
    // Interpolate a point using barycentric coordinates
    private fun interpolateBarycentric(
        x: Float,
        y: Float,
        vertices: List<Point3D>,
        triangle: Triangle
    ): Point3D {
        val p = Point3D(x.toDouble(), y.toDouble(), 0.0)
        val v0 = vertices[0]
        val v1 = vertices[1]
        val v2 = vertices[2]

        // Compute barycentric weights
        val denom = (v1.y - v2.y) * (v0.x - v2.x) + (v2.x - v1.x) * (v0.y - v2.y)
        val w0 = ((v1.y - v2.y) * (p.x - v2.x) + (v2.x - v1.x) * (p.y - v2.y)) / denom
        val w1 = ((v2.y - v0.y) * (p.x - v2.x) + (v0.x - v2.x) * (p.y - v2.y)) / denom
        val w2 = 1f - w0 - w1

        // Interpolate 3D position using barycentric weights
        val p0 = triangle.v1.point
        val p1 = triangle.v2.point
        val p2 = triangle.v3.point

        return (p0 * w0) + (p1 * w1) + (p2 * w2)
        //return (v0 * w0) + (v1 * w1) + (v2 * w2)
    }

    // TODO: KONIECZNIE DO ZMIANY
    fun interpolateNormal(
        point: Point3D, // Punkt, dla którego interpolujemy normalną
        triangle: Triangle
    ): Point3D {
        // Wierzchołki trójkąta
        val v0 = triangle.v1.point
        val v1 = triangle.v2.point
        val v2 = triangle.v3.point

        // Wektory normalne wierzchołków trójkąta
        val n0 = triangle.v1.normal
        val n1 = triangle.v2.normal
        val n2 = triangle.v3.normal

        // Oblicz współczynniki barycentryczne dla punktu
        val denom = (v1.y - v2.y) * (v0.x - v2.x) + (v2.x - v1.x) * (v0.y - v2.y)
        val w0 = ((v1.y - v2.y) * (point.x - v2.x) + (v2.x - v1.x) * (point.y - v2.y)) / denom
        val w1 = ((v2.y - v0.y) * (point.x - v2.x) + (v0.x - v2.x) * (point.y - v2.y)) / denom
        val w2 = 1f - w0 - w1

        // Zinterpoluj wektor normalny przy użyciu współczynników barycentrycznych
        val interpolatedNormal = (n0 * w0) + (n1 * w1) + (n2 * w2)

        // Zwróć znormalizowaną normalną
        return interpolatedNormal.normalize()
    }

//    // Helper function to interpolate values using barycentric coordinates
//    fun interpolateWithBarycentric(
//        v1: Point3D,
//        v2: Point3D,
//        v3: Point3D,
//        barycentricCoords: Triple<Double, Double, Double>
//    ): Point3D {
//        val (w1, w2, w3) = barycentricCoords
//        return Point3D(
//            x = w1 * v1.x + w2 * v2.x + w3 * v3.x,
//            y = w1 * v1.y + w2 * v2.y + w3 * v3.y,
//            z = w1 * v1.z + w2 * v2.z + w3 * v3.z
//        )
//    }
//
//    // Calculate barycentric coordinates for a point inside a triangle
//    fun calculateBarycentricCoordinates(
//        point: Point2D,
//        v1: Point3D,
//        v2: Point3D,
//        v3: Point3D
//    ): Triple<Double, Double, Double> {
//        val denominator = (v2.y - v3.y) * (v1.x - v3.x) + (v3.x - v2.x) * (v1.y - v3.y)
//
//        val w1 = ((v2.y - v3.y) * (point.x - v3.x) + (v3.x - v2.x) * (point.y - v3.y)) / denominator
//        val w2 = ((v3.y - v1.y) * (point.x - v3.x) + (v1.x - v3.x) * (point.y - v3.y)) / denominator
//        val w3 = 1 - w1 - w2
//
//        return Triple(w1, w2, w3)
//    }

//    // Extension function to integrate with ScanlinePolygonFiller
//    fun calculatePixelColor(
//        point: Point2D,
//        triangle: Triangle,
//        params: PhongParameters
//    ): Color {
//        // Calculate barycentric coordinates for the point
//        val barycentricCoords = calculateBarycentricCoordinates(
//            point,
//            triangle.v1.point,
//            triangle.v2.point,
//            triangle.v3.point
//        )
//
////        // TODO: zmienilem z vertex1 na v1 zeby zobaczyc co jak wezme normal z 2d zamiast vertex
////        // Interpolate normal vector using barycentric coordinates
////        val interpolatedNormal = interpolateWithBarycentric(
////            triangle.v1.normal,
////            triangle.v2.normal,
////            triangle.v3.normal,
////            barycentricCoords
////        ).normalize()
////
////        // Interpolate z coordinate
////        val interpolatedZ = interpolateWithBarycentric(
////            Point3D(0.0, 0.0, triangle.v1.point.z.toDouble()),
////            Point3D(0.0, 0.0, triangle.v2.point.z.toDouble()),
////            Point3D(0.0, 0.0, triangle.v3.point.z.toDouble()),
////            barycentricCoords
////        ).z
////
////        // Create 3D point from 2D point and interpolated z
////        val point3D = Point3D(point.x, point.y, interpolatedZ)
//
//        var point3D = interpolateWithBarycentric(
//            triangle.v1.point,
//            triangle.v2.point,
//            triangle.v3.point,
//            barycentricCoords
//        )
//
//        var interpolatedNormal = interpolateWithBarycentric(
//            triangle.v1.normal,
//            triangle.v2.normal,
//            triangle.v3.normal,
//            barycentricCoords
//        ).normalize()
//
//        return calculatePhongLighting(point3D, interpolatedNormal, params)
//    }
}