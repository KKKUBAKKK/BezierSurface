package bezier.surface.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import bezier.surface.model.Edge
import bezier.surface.model.LightingParameters
import bezier.surface.model.Point2D
import bezier.surface.model.Point3D
import kotlin.math.max
import kotlin.math.pow

class ScanlinePolygonFiller {
    private lateinit var edgeTable: Array<MutableList<Edge>>
    private val activeEdgeTable = mutableListOf<Edge>()
    private var zBuffer: FloatArray
    private var pixelBuffer: IntArray
    private var xOffset: Int = 0
    private var yOffset: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private lateinit var lightingParams: LightingParameters

    constructor(
        width: Int,
        height: Int,
        zBuffer: FloatArray,
        pixelBuffer: IntArray,
        lightingParams: LightingParameters
    ) {
        this.zBuffer = zBuffer
        this.pixelBuffer = pixelBuffer
        this.width = width
        this.height = height
        this.xOffset = width / 2
        this.yOffset = height / 2
        this.lightingParams = lightingParams
        initializeEdgeTable(height)
    }

    private fun initializeEdgeTable(height: Int) {
        edgeTable = Array(height) { mutableListOf() }
    }

    private fun yToIndex(y: Float): Int {
        return (y + yOffset).toInt().coerceIn(0, height - 1)
    }

    // Lighting calculation remains the same as before
    private fun calculateLighting(normal: Point3D, position: Point3D): Color {
        val N = normal.normalize()
        val L = lightingParams.lightDirection.normalize()
        val V = lightingParams.viewerPosition.normalize()

        val NdotL = max(0.0, N.dot(L))

        val R = Point3D(
            2 * NdotL * N.x - L.x,
            2 * NdotL * N.y - L.y,
            2 * NdotL * N.z - L.z
        ).normalize()

        val specular = max(0.0, R.dot(V)).pow(lightingParams.m)
        val intensity = (lightingParams.kd * NdotL + lightingParams.ks * specular)
            .coerceIn(0.0, 1.0)

        return Color(
            red = (lightingParams.objectColor.red * intensity).coerceIn(0.0, 1.0).toFloat(),
            green = (lightingParams.objectColor.green * intensity).coerceIn(0.0, 1.0).toFloat(),
            blue = (lightingParams.objectColor.blue * intensity).coerceIn(0.0, 1.0).toFloat(),
            alpha = lightingParams.objectColor.alpha
        )
    }

    // TODO: something is wrong here with the points coordinates (they come in good, come out bad)
    fun fillPolygon(points: List<Point2D>, width: Int, height: Int) {
        if (points.size < 3) return

        buildEdgeTable(points)

        var scanline = findFirstScanline()
        if (scanline == -1) return
        val lastScanline = findLastScanline()

        while (scanline <= lastScanline) {
            // Add new edges to AET
            activeEdgeTable.addAll(edgeTable[scanline])
            edgeTable[scanline].clear()

            // Bucket sort edges by x coordinate
            if (activeEdgeTable.size >= 2) {
                bucketSortEdges()
                fillScanline(scanline, width)
            }

            // Remove edges that end at current scanline
            activeEdgeTable.removeAll { it.yMax == scanline }

            // TODO: update also normal and z values
            // Update x coordinates for next scanline
            for (edge in activeEdgeTable) {
                edge.x += edge.slopeInverse
            }

            scanline++
        }
    }

    private fun bucketSortEdges() {
        // Simple bucket sort implementation for edges based on x coordinate
        val tmin = activeEdgeTable.minByOrNull { it.x }?.x ?: return
        val tmax = activeEdgeTable.maxByOrNull { it.x }?.x ?: return

        val min = tmin + xOffset
        val max = tmax + xOffset

        if (max == min) return

        val bucketCount = activeEdgeTable.size
        val bucketSize = (max - min) / bucketCount + 1
        val buckets = Array(bucketCount) { mutableListOf<Edge>() }

        // Distribute edges to buckets
        for (edge in activeEdgeTable) {
            val index = (((edge.x + xOffset) - min) / bucketSize).toInt().coerceIn(0, bucketCount - 1)
            buckets[index].add(edge)
        }

        // Collect edges back in sorted order
        activeEdgeTable.clear()
        for (bucket in buckets) {
            if (bucket.isNotEmpty()) {
//                bucket.sortBy { it.x } // chyba niepotraebne
                activeEdgeTable.addAll(bucket)
            }
        }
    }

    private fun buildEdgeTable(points: List<Point2D>) {
        for (i in points.indices) {
            val start = points[i]
            val end = points[(i + 1) % points.size]

            // Skip horizontal edges
            if (yToIndex(start.y) == yToIndex(end.y)) continue

            // Determine upper and lower points
            val (upper, lower) = if (start.y > end.y) Pair(start, end) else Pair(end, start)

            val slopeInverse = (upper.x - lower.x) / (upper.y - lower.y)

            val edge = Edge(
                yMax = yToIndex(upper.y),
                x = lower.x,
                slopeInverse = slopeInverse,
                normal = lower.normal,
                zValue = lower.z
            )

            val yStart = yToIndex(lower.y)
            edgeTable[yStart].add(edge)
        }
    }

    private fun fillScanline(scanline: Int, width: Int) {
        for (i in 0 until activeEdgeTable.size - 1 step 2) {
            val leftEdge = activeEdgeTable[i]
            val rightEdge = activeEdgeTable[i + 1]

            val xStart = (leftEdge.x).toInt().coerceIn(-width/2, width/2)
            val xEnd = (rightEdge.x).toInt().coerceIn(-width/2, width/2)

            val zStep = if (xEnd != xStart)
                (rightEdge.zValue - leftEdge.zValue) / (xEnd - xStart)
            else
                0f

            var currentZ = leftEdge.zValue

            for (x in xStart..xEnd) {
                val bufferIndex = scanline * width + (x + width/2)

                if (bufferIndex >= 0 && bufferIndex < zBuffer.size && currentZ > zBuffer[bufferIndex]) {
                    val t = if (xEnd != xStart) (x - xStart).toFloat() / (xEnd - xStart) else 0f

                    val interpolatedNormal = Point3D(
                        leftEdge.normal.x * (1 - t) + rightEdge.normal.x * t,
                        leftEdge.normal.y * (1 - t) + rightEdge.normal.y * t,
                        leftEdge.normal.z * (1 - t) + rightEdge.normal.z * t
                    ).normalize()

                    val position = Point3D(x.toDouble(), (scanline - yOffset).toDouble(), currentZ.toDouble())
                    val color = calculateLighting(interpolatedNormal, position)

                    zBuffer[bufferIndex] = currentZ
                    pixelBuffer[bufferIndex] = color.toArgb()
                }

                currentZ += zStep
            }
        }
    }

    private fun findFirstScanline(): Int = edgeTable.indexOfFirst { it.isNotEmpty() }
    private fun findLastScanline(): Int = edgeTable.indexOfLast { it.isNotEmpty() }
}