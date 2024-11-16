package bezier.surface.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.Point

// Edge class to represent a polygon edge
data class Edge(
    var yMax: Int,
    var xMin: Float,
    var slopeInverse: Float,
    var color: Color,  // Added to support color interpolation
    var zValue: Float  // Added for z-buffer
)

data class Point2D(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val color: Color = Color.White
)

class ScanlinePolygonFiller {
    private lateinit var edgeTable: Array<MutableList<Edge>>
    private val activeEdgeTable = mutableListOf<Edge>()
    private var zBuffer: FloatArray
    private var pixelBuffer: IntArray
    private var yOffset: Int = 0  // Offset to translate y-coordinates
    private var height: Int = 0

    constructor(width: Int, height: Int, zBuffer: FloatArray, pixelBuffer: IntArray) {
        this.zBuffer = zBuffer
        this.pixelBuffer = pixelBuffer
        this.height = height
        this.yOffset = height / 2  // Center offset
        initializeEdgeTable(height)
    }

    private fun initializeEdgeTable(height: Int) {
        edgeTable = Array(height) { mutableListOf() }
    }

    // Convert from centered Y to array index
    private fun yToIndex(y: Float): Int {
        return (y + yOffset).toInt().coerceIn(0, height - 1)
    }

    // Convert from array index to centered Y
    private fun indexToY(index: Int): Float {
        return (index - yOffset).toFloat()
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
                color = upperPoint.color,
                zValue = upperPoint.z
            )

            // Use translated y-coordinate for edge table indexing
            val yIndex = yToIndex(upperPoint.y)
            edgeTable[yIndex].add(edge)
        }
    }

    private fun fillScanline(yIndex: Int, width: Int) {
        // Convert back to centered coordinates for actual drawing
        val centeredY = indexToY(yIndex)

        for (i in 0 until activeEdgeTable.size step 2) {
            if (i + 1 >= activeEdgeTable.size) break

            val startX = activeEdgeTable[i].xMin.toInt().coerceIn(-width / 2, width / 2)
            val endX = activeEdgeTable[i + 1].xMin.toInt().coerceIn(-width / 2, width / 2)

            val startZ = activeEdgeTable[i].zValue
            val endZ = activeEdgeTable[i + 1].zValue
            val zStep = if (endX != startX) (endZ - startZ) / (endX - startX) else 0f

            var currentZ = startZ

            for (x in startX..endX) {
                // Convert x to centered coordinates if needed
//                val centeredX = x - width / 2
                val bufferIndex = yIndex * width + (x + width / 2)

                if (currentZ > zBuffer[bufferIndex]) {
                    zBuffer[bufferIndex] = currentZ
                    pixelBuffer[bufferIndex] = interpolateColors(
                        activeEdgeTable[i].color,
                        activeEdgeTable[i + 1].color,
                        (x - startX).toFloat() / (endX - startX).toFloat()
                    ).toArgb()
                }

                currentZ += zStep
            }
        }
    }

    private fun findFirstScanline(): Int {
        return edgeTable.indexOfFirst { it.isNotEmpty() }
    }

    private fun findLastScanline(): Int {
        return edgeTable.indexOfLast { it.isNotEmpty() }
    }

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

    private fun interpolateColors(c1: Color, c2: Color, t: Float): Color {
        return Color(
            red = c1.red * (1 - t) + c2.red * t,
            green = c1.green * (1 - t) + c2.green * t,
            blue = c1.blue * (1 - t) + c2.blue * t,
            alpha = c1.alpha * (1 - t) + c2.alpha * t
        )
    }
}