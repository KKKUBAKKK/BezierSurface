package bezier.surface.viewmodel

import org.jetbrains.skia.Point

// Edge class to represent a polygon edge
data class Edge(
    var yMax: Int,
    var xMin: Float,
    var slopeInverse: Float
)

class ScanlinePolygonFiller {
    // Edge Table - Array of buckets (LinkedLists) for each y-coordinate
    private lateinit var edgeTable: Array<MutableList<Edge>>
    // Active Edge Table - Contains edges that intersect with current scanline
    private val activeEdgeTable = mutableListOf<Edge>()

    /**
     * Fills a polygon defined by a list of points using the scanline algorithm
     * @param points List of points defining the polygon vertices
     * @param width Canvas width
     * @param height Canvas height
     * @return IntArray representing the filled polygon pixels
     */
    fun fillPolygon(points: List<Point>, width: Int, height: Int): IntArray {
        if (points.size < 3) return IntArray(width * height)

        // Initialize edge table
        initializeEdgeTable(height)

        // Build edge table from polygon vertices
        buildEdgeTable(points)

        // Create pixel buffer
        val pixelBuffer = IntArray(width * height)

        // Process each scanline
        var y = findFirstScanline()
        val lastScanline = findLastScanline()

        while (y <= lastScanline) {
            // Move edges from ET to AET if their yMin equals current scanline
            moveEdgesToAET(y)

            // Remove edges from AET if their yMax equals current scanline
            removeEdgesFromAET(y)

            // Sort AET by x-coordinate
            activeEdgeTable.sortBy { it.xMin }

            // Fill pixels between edge pairs
            fillScanline(y, pixelBuffer, width)

            // Increment y
            y++

            // Update x-coordinates for edges in AET
            updateAETEdges()
        }

        return pixelBuffer
    }

    private fun initializeEdgeTable(height: Int) {
        edgeTable = Array(height) { mutableListOf() }
    }

    private fun buildEdgeTable(points: List<Point>) {
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]

            // Skip horizontal edges
            if (current.y == next.y) continue

            // Determine upper and lower vertices
            val (upperPoint, lowerPoint) = if (current.y < next.y) {
                Pair(current, next)
            } else {
                Pair(next, current)
            }

            // Calculate inverse slope
            val slopeInverse = (next.x - current.x) / (next.y - current.y)

            // Create and add edge to edge table
            val edge = Edge(
                yMax = lowerPoint.y.toInt(),
                xMin = upperPoint.x,
                slopeInverse = slopeInverse
            )

            edgeTable[upperPoint.y.toInt()].add(edge)
        }
    }

    private fun findFirstScanline(): Int {
        return edgeTable.indexOfFirst { it.isNotEmpty() }
    }

    private fun findLastScanline(): Int {
        return edgeTable.indexOfLast { it.isNotEmpty() }
    }

    private fun moveEdgesToAET(y: Int) {
        activeEdgeTable.addAll(edgeTable[y])
        edgeTable[y].clear()
    }

    private fun removeEdgesFromAET(y: Int) {
        activeEdgeTable.removeAll { it.yMax == y }
    }

    private fun fillScanline(y: Int, pixelBuffer: IntArray, width: Int) {
        for (i in 0 until activeEdgeTable.size step 2) {
            if (i + 1 >= activeEdgeTable.size) break

            val startX = activeEdgeTable[i].xMin.toInt().coerceIn(0, width - 1)
            val endX = activeEdgeTable[i + 1].xMin.toInt().coerceIn(0, width - 1)

            for (x in startX..endX) {
                pixelBuffer[y * width + x] = 0xFF000000.toInt() // Black color
            }
        }
    }

    private fun updateAETEdges() {
        for (edge in activeEdgeTable) {
            edge.xMin += edge.slopeInverse
        }
    }

    companion object {
        /**
         * Helper function to create a polygon from a list of x,y coordinates
         */
        fun createPolygon(vararg coordinates: Float): List<Point> {
            require(coordinates.size % 2 == 0) { "Coordinates must be pairs of x,y values" }
            return coordinates.toList()
                .chunked(2)
                .map { Point(it[0], it[1]) }
        }
    }
}