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
    private var yOffset: Int = 0
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
        this.height = height
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

    private fun indexToY(index: Int): Float {
        return (index - yOffset).toFloat()
    }

    // Lighting calculation remains the same
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

    fun fillPolygon(points: List<Point2D>, width: Int, height: Int) {
        if (points.size < 3) return

        // Sort vertices by y-coordinate (for surnames L-Z)
        val sortedPoints = points.sortedBy { it.y }

        buildEdgeTable(sortedPoints)

        var yIndex = findFirstScanline()
        val lastYIndex = findLastScanline()

        while (yIndex <= lastYIndex) {
            moveEdgesToAET(yIndex)

            // Sort active edges by x coordinate (for surnames A-K)
            activeEdgeTable.sortBy { it.currentX }

            fillScanline(yIndex, width)
            removeEdgesFromAET(yIndex)
            yIndex++
            updateAETEdges()
        }
    }

    private fun buildEdgeTable(points: List<Point2D>) {
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]

            // Skip horizontal edges
            if (current.y == next.y) continue

            val (upperPoint, lowerPoint) = if (current.y < next.y) {
                Pair(current, next)
            } else {
                Pair(next, current)
            }

            val dx = next.x - current.x
            val dy = next.y - current.y
            val slopeInverse = dx / dy

            val startY = yToIndex(upperPoint.y)
            val edge = Edge(
                yMax = yToIndex(lowerPoint.y),
                xMin = upperPoint.x,
                slopeInverse = slopeInverse,
                normal = upperPoint.normal,
                zValue = upperPoint.z,
                currentX = upperPoint.x,
                startY = startY
            )

            edgeTable[startY].add(edge)
        }
    }

    private fun fillScanline(yIndex: Int, width: Int) {
        val centeredY = indexToY(yIndex)

        for (i in 0 until activeEdgeTable.size step 2) {
            if (i + 1 >= activeEdgeTable.size) break

            val leftEdge = activeEdgeTable[i]
            val rightEdge = activeEdgeTable[i + 1]

            val startX = leftEdge.currentX.toInt().coerceIn(-width / 2, width / 2)
            val endX = rightEdge.currentX.toInt().coerceIn(-width / 2, width / 2)

            // Calculate z interpolation
            val dx = endX - startX
            val startZ = leftEdge.zValue
            val endZ = rightEdge.zValue
            val zStep = if (dx != 0) (endZ - startZ) / dx else 0f

            var currentZ = startZ

            for (x in startX..endX) {
                val bufferIndex = yIndex * width + (x + width / 2)

                if (currentZ > zBuffer[bufferIndex]) {
                    // Interpolate normal
                    val t = if (dx != 0) (x - startX).toFloat() / dx else 0f
                    val interpolatedNormal = Point3D(
                        leftEdge.normal.x * (1 - t) + rightEdge.normal.x * t,
                        leftEdge.normal.y * (1 - t) + rightEdge.normal.y * t,
                        leftEdge.normal.z * (1 - t) + rightEdge.normal.z * t
                    ).normalize()

                    val position = Point3D(x.toDouble(), centeredY.toDouble(), currentZ.toDouble())
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

    private fun moveEdgesToAET(yIndex: Int) {
        activeEdgeTable.addAll(edgeTable[yIndex])
        edgeTable[yIndex].clear()
    }

    private fun removeEdgesFromAET(yIndex: Int) {
        activeEdgeTable.removeAll { it.yMax == yIndex }
    }

    private fun updateAETEdges() {
        for (edge in activeEdgeTable) {
            edge.currentX += edge.slopeInverse
        }
    }
}