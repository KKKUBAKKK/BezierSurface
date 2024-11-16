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

    private fun calculateLighting(normal: Point3D, position: Point3D): Color {
        // Normalize vectors
        val N = normal.normalize()
        val L = lightingParams.lightDirection.normalize()
        val V = lightingParams.viewerPosition.normalize()

        // Calculate reflection vector R = 2(N·L)N - L
        val NdotL = N.dot(L)
        val R = Point3D(
            2 * NdotL * N.x - L.x,
            2 * NdotL * N.y - L.y,
            2 * NdotL * N.z - L.z
        ).normalize()

        // Calculate diffuse component
        val diffuse = max(0.0, NdotL)

        // Calculate specular component
        val specular = max(0.0, R.dot(V)).pow(lightingParams.m)

        // Calculate final lighting using Lambert's model with specular component
        val intensity = lightingParams.kd * diffuse + lightingParams.ks * specular

        // Apply lighting to object color (for each RGB component)
        val red = (lightingParams.objectColor.red * intensity).coerceIn(0.0, 1.0)
        val green = (lightingParams.objectColor.green * intensity).coerceIn(0.0, 1.0)
        val blue = (lightingParams.objectColor.blue * intensity).coerceIn(0.0, 1.0)
        val alpha = lightingParams.objectColor.alpha

        // Apply lighting to object color (for each RGB component)s
        return Color(red.toFloat(), green.toFloat(), blue.toFloat(), alpha)
    }

    fun fillPolygon(points: List<Point2D>, width: Int, height: Int) {
        if (points.size < 3) return

        buildEdgeTable(points)

        var yIndex = findFirstScanline()
        val lastYIndex = findLastScanline()

        while (yIndex <= lastYIndex) {
            moveEdgesToAET(yIndex)
            removeEdgesFromAET(yIndex)
            activeEdgeTable.sortBy { it.xMin }
            fillScanline(yIndex, width)
            yIndex++
            updateAETEdges()
        }
    }

    private fun fillScanline(yIndex: Int, width: Int) {
        val centeredY = indexToY(yIndex)

        for (i in 0 until activeEdgeTable.size step 2) {
            if (i + 1 >= activeEdgeTable.size) break

            val startX = activeEdgeTable[i].xMin.toInt().coerceIn(-width / 2, width / 2)
            val endX = activeEdgeTable[i + 1].xMin.toInt().coerceIn(-width / 2, width / 2)

            val startZ = activeEdgeTable[i].zValue
            val endZ = activeEdgeTable[i + 1].zValue
            val zStep = if (endX != startX) (endZ - startZ) / (endX - startX) else 0f

            // Interpolate normals
            val startNormal = activeEdgeTable[i].normal
            val endNormal = activeEdgeTable[i + 1].normal

            var currentZ = startZ

            for (x in startX..endX) {
                val bufferIndex = yIndex * width + (x + width / 2)

                if (currentZ > zBuffer[bufferIndex]) {
                    // Interpolate normal at current point
                    val t = (x - startX).toFloat() / (endX - startX).toFloat()
                    val interpolatedNormal = Point3D(
                        startNormal.x * (1 - t) + endNormal.x * t,
                        startNormal.y * (1 - t) + endNormal.y * t,
                        startNormal.z * (1 - t) + endNormal.z * t
                    ).normalize()

                    // Calculate position for lighting
                    val position = Point3D(x.toDouble(), centeredY.toDouble(), currentZ.toDouble())

                    // Calculate color using lighting model
                    val color = calculateLighting(interpolatedNormal, position)

                    zBuffer[bufferIndex] = currentZ
                    pixelBuffer[bufferIndex] = color.toArgb()
                }

                currentZ += zStep
            }
        }
    }

    private fun buildEdgeTable(points: List<Point2D>) {
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]

            if (current.y == next.y) continue

            val (upperPoint, lowerPoint) = if (current.y < next.y) {
                Pair(current, next)
            } else {
                Pair(next, current)
            }

            val slopeInverse = (next.x - current.x) / (next.y - current.y)

            val edge = Edge(
                yMax = yToIndex(lowerPoint.y),
                xMin = upperPoint.x,
                slopeInverse = slopeInverse,
                normal = upperPoint.normal,
                zValue = upperPoint.z
            )

            val yIndex = yToIndex(upperPoint.y)
            edgeTable[yIndex].add(edge)
        }
    }

    // ... rest of the helper methods remain the same ...
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
            edge.xMin += edge.slopeInverse
        }
    }
}