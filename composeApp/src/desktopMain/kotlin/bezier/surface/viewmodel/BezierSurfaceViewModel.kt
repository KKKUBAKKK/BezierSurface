package bezier.surface.viewmodel

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import bezier.surface.model.LightingParameters
import bezier.surface.model.Mesh
import bezier.surface.model.Point2D
import bezier.surface.model.Point3D
import bezier.surface.model.Triangle
import bezier.surface.model.Triangle2D
import bezier.surface.model.Vertex
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class BezierSurfaceViewModel {
    var showWireframe by mutableStateOf(true)
    var showFilled by mutableStateOf(false)
    var lineColor by mutableStateOf(Color.White)
    var fillColor by mutableStateOf(Color.Magenta)
    var lightColor by mutableStateOf(Color.White)
    var rotationX by mutableStateOf(0f)
    var rotationZ by mutableStateOf(0f)
    var resolution by mutableStateOf(3)
    var kd by mutableStateOf(0.5f)
    var ks by mutableStateOf(0.5f)
    var m by mutableStateOf(10f)
    var width by mutableStateOf(1600)
    var height by mutableStateOf(1200)

    private var pixelBuffer by mutableStateOf(IntArray(width * height))
    private var zBuffer = FloatArray(width * height) { Float.NEGATIVE_INFINITY }

    private fun transformToScreen(vertex: Vertex, canvasWidth: Float, canvasHeight: Float): Point2D {
        val rotatedVertex = transformVertex(vertex, mesh?.rotationX ?: 0.0, mesh?.rotationZ ?: 0.0)
        val scale = 200.0
        val perspectiveZ = 1.0 + rotatedVertex.point.z * 0.2

        val screenX = ((rotatedVertex.point.x / perspectiveZ) * scale).toFloat()
        val screenY = ((rotatedVertex.point.y / perspectiveZ) * scale).toFloat()
        val screenZ = rotatedVertex.point.z.toFloat()

        // Transform the normal vector
        val normal = Point3D(
            rotatedVertex.normal.x,
            rotatedVertex.normal.y,
            rotatedVertex.normal.z
        )

        return Point2D(
            x = screenX,
            y = screenY,
            z = screenZ,
            normal = normal
        )
    }

    // You'll also need this helper function to transform the vertex normal
    private fun transformNormal(normal: Point3D, rotationX: Double, rotationZ: Double): Point3D {
        val cosX = cos(rotationX)
        val sinX = sin(rotationX)
        val cosZ = cos(rotationZ)
        val sinZ = sin(rotationZ)

        // Apply X rotation
        val afterX = Point3D(
            x = normal.x,
            y = normal.y * cosX - normal.z * sinX,
            z = normal.y * sinX + normal.z * cosX
        )

        // Apply Z rotation
        val afterZ = Point3D(
            x = afterX.x * cosZ - afterX.y * sinZ,
            y = afterX.x * sinZ + afterX.y * cosZ,
            z = afterX.z
        )

        return Point3D(
            x = afterZ.x,
            y = afterZ.y,
            z = afterZ.z
        )
    }

    private val controlPoints = Array(4) { i ->
        Array(4) { j ->
            Vertex(
                point = Point3D(i.toDouble() - 1.0, (if ((i + j) % 2 == 0) 0.5 else -0.5), j.toDouble() - 1.0),
                pu = Point3D.Zero(),
                pv = Point3D.Zero(),
                normal = Point3D.Zero(),
                u = i.toDouble(),
                v = j.toDouble()
            )
        }
    }

    private var mesh: Mesh? = null

    private fun generateMesh(resolution: Int): Mesh {
        val triangles = mutableListOf<Triangle>()
        val rotatedTriangles = mutableListOf<Triangle2D>()
        val step = 1.0 / resolution

        for (i in 0 until resolution) {
            for (j in 0 until resolution) {
                val u1 = i * step
                val u2 = (i + 1) * step
                val v1 = j * step
                val v2 = (j + 1) * step

                val p1 = calculateSurfacePoint(u1, v1)
                val p2 = calculateSurfacePoint(u2, v1)
                val p3 = calculateSurfacePoint(u1, v2)
                val p4 = calculateSurfacePoint(u2, v2)

                triangles.add(Triangle(p1, p2, p3))
                rotatedTriangles.add(transformTriangle(triangles.last(), width.toFloat(), height.toFloat()))
                triangles.add(Triangle(p2, p4, p3))
                rotatedTriangles.add(transformTriangle(triangles.last(), width.toFloat(), height.toFloat()))
            }
        }

        return Mesh(triangles, rotatedTriangles, width, height, rotationX.toDouble(), rotationZ.toDouble(), resolution)
    }

    private fun calculateSurfacePoint(u: Double, v: Double): Vertex {
        var point = Vertex(
            point = Point3D.Zero(),
            pu = Point3D.Zero(),
            pv = Point3D.Zero(),
            normal = Point3D.Zero(),
            u = u,
            v = v
        )

        for (i in 0..2) {
            for (j in 0..2) {
                val basis = bernstein(i, 2, u) * bernstein(j, 2, v)
                point = point.copy(
                    point = point.point + controlPoints[i][j].point * basis,
                    pu = point.pu + controlPoints[i][j].pu * basis,
                    pv = point.pv + controlPoints[i][j].pv * basis,
                    normal = point.normal + calculateNormal(controlPoints[i][j].point, controlPoints[i+1][j].point, controlPoints[i][j+1].point) * basis
                )
            }
        }

        return point
    }

    private fun bernstein(i: Int, n: Int, t: Double): Double {
        fun binomial(n: Int, k: Int): Int {
            if (k == 0 || k == n) return 1
            return binomial(n - 1, k - 1) + binomial(n - 1, k)
        }

        return binomial(n, i) * Math.pow(t, i.toDouble()) * Math.pow(1 - t, (n - i).toDouble())
    }

    private fun calculateNormal(p1: Point3D, p2: Point3D, p3: Point3D): Point3D {
        val v1 = Point3D(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z)
        val v2 = Point3D(p3.x - p1.x, p3.y - p1.y, p3.z - p1.z)
        return Point3D(
            v1.y * v2.z - v1.z * v2.y,
            v1.z * v2.x - v1.x * v2.z,
            v1.x * v2.y - v1.y * v2.x
        ).normalized()
    }

    private fun transformPoint(vertex: Vertex, canvasWidth: Float, canvasHeight: Float): Offset {
        val rotatedVertex = transformVertex(vertex, mesh?.rotationX ?: 0.0, mesh?.rotationZ ?: 0.0)
        val scale = 200.0
        val perspectiveZ = 1.0 + rotatedVertex.point.z * 0.2

        return Offset(
            ((rotatedVertex.point.x / perspectiveZ) * scale).toFloat(),
            ((rotatedVertex.point.y / perspectiveZ) * scale).toFloat()
        )
    }

    private fun transformVertex(vertex: Vertex, rotationX: Double, rotationZ: Double): Vertex {
        val rotatedP = transformPoint3D(vertex.point, rotationX, rotationZ)
        val rotatedPu = transformPoint3D(vertex.pu, rotationX, rotationZ)
        val rotatedPv = transformPoint3D(vertex.pv, rotationX, rotationZ)
        val rotatedN = transformPoint3D(vertex.normal, rotationX, rotationZ)

        return vertex.copy(
            point = rotatedP,
            pu = rotatedPu,
            pv = rotatedPv,
            normal = rotatedN
        )
    }

    private fun transformPoint3D(point: Point3D, rotationX: Double, rotationZ: Double): Point3D {
        val rotatedY1 = point.y * cos(rotationX) - point.z * sin(rotationX)
        val rotatedZ1 = point.y * sin(rotationX) + point.z * cos(rotationX)
        val rotatedX2 = point.x * cos(rotationZ) - rotatedY1 * sin(rotationZ)
        val rotatedY2 = point.x * sin(rotationZ) + rotatedY1 * cos(rotationZ)
        val rotatedZ2 = rotatedZ1
        return Point3D(rotatedX2, rotatedY2, rotatedZ2)
    }

    private fun transformTriangle(triangle: Triangle, canvasWidth: Float, canvasHeight: Float): Triangle2D {
        val p1 = transformPoint(triangle.v1, canvasWidth, canvasHeight)
        val p2 = transformPoint(triangle.v2, canvasWidth, canvasHeight)
        val p3 = transformPoint(triangle.v3, canvasWidth, canvasHeight)
        return Triangle2D(p1, p2, p3)
    }

    private fun adjustColor(color: Color, normal: Point3D): Color {
        val lightDirection = Point3D(0.0, 0.0, 1.0)
        val dotProduct = normal.x * lightDirection.x + normal.y * lightDirection.y + normal.z * lightDirection.z
        val diffuseIntensity = max(dotProduct, 0.0)
        val specularIntensity = if (dotProduct > 0) Math.pow(dotProduct, m.toDouble()) else 0.0
        val intensity = kd * diffuseIntensity + ks * specularIntensity
        return color.copy(red = (color.red * intensity).toFloat(),
            green = (color.green * intensity).toFloat(),
            blue = (color.blue * intensity).toFloat())
    }

    private fun interpolate(y: Float, pA: Offset, pB: Offset): Offset {
        val t = (y - pA.y) / (pB.y - pA.y)
        val x = pA.x + t * (pB.x - pA.x)
        return Offset(x, y)
    }

    private fun fillTriangle(triangle: Triangle2D, normal: Point3D, drawScope: DrawScope) {
        val vertices = listOf(triangle.v1, triangle.v2, triangle.v3).sortedBy { it.y }
        val (top, middle, bottom) = vertices

        for (y in top.y.toInt()..<bottom.y.toInt()) {
            val left = interpolate(y.toFloat(), top, middle)
            val right = interpolate(y.toFloat(), top, bottom)

            for (x in left.x.toInt()..right.x.toInt()) {
                val offset = Offset(x.toFloat(), y.toFloat())
                val color = adjustColor(fillColor, normal)
                drawScope.drawRect(color = color, topLeft = offset, size = Size(1f, 1f))
            }
        }
    }

//    fun drawSurface(drawScope: DrawScope, width: Int, height: Int) {
//        this.width = width
//        this.height = height
//
//        if (mesh == null || mesh?.resolution != resolution) {
//            mesh = generateMesh(resolution)
//        }
//
//        if (mesh?.rotationX != rotationX.toDouble() || mesh?.rotationZ != rotationZ.toDouble()) {
//            mesh?.rotatedTriangles = mesh?.triangles?.map { triangle ->
//                transformTriangle(triangle, width.toFloat(), height.toFloat())
//            } ?: emptyList()
//        }
//
//        mesh?.canvasWidth = width
//        mesh?.canvasHeight = height
//        mesh?.rotationX = rotationX.toDouble()
//        mesh?.rotationZ = rotationZ.toDouble()
//
//        with(drawScope) {
//            if (showFilled) {
//                mesh?.rotatedTriangles?.forEach { triangle ->
////                    fillTriangle(triangle, ,drawScope)
//                }
//            }
//
//            if (showWireframe) {
//                mesh?.rotatedTriangles?.forEach { triangle ->
//                    drawLine(lineColor, triangle.v1, triangle.v2)
//                    drawLine(lineColor, triangle.v2, triangle.v3)
//                    drawLine(lineColor, triangle.v3, triangle.v1)
//                }
//            }
//        }
//    }

    fun drawSurface(drawScope: DrawScope, width: Int, height: Int) {
        if (mesh == null || mesh?.canvasWidth != width || mesh?.canvasHeight != height ||
            mesh?.rotationX != rotationX.toDouble() || mesh?.rotationZ != rotationZ.toDouble()) {
            mesh = generateMesh(resolution)
        }

        // Reset buffers
        zBuffer = FloatArray(width * height) { Float.NEGATIVE_INFINITY }
        pixelBuffer = IntArray(width * height)

        with(drawScope) {
            if (showFilled) {
                val light = LightingParameters(kd.toDouble(), ks.toDouble(), m.toDouble(), Point3D(0.0, 0.0, 1.0), Point3D(0.0, 0.0, 1.0), fillColor)
                val filler = ScanlinePolygonFiller(width, height, zBuffer, pixelBuffer, light)

                mesh?.triangles?.forEach { triangle ->
                    val points = listOf(
                        transformToScreen(triangle.v1, width.toFloat(), height.toFloat()),
                        transformToScreen(triangle.v2, width.toFloat(), height.toFloat()),
                        transformToScreen(triangle.v3, width.toFloat(), height.toFloat())
                    )

                    filler.fillPolygon(points, width, height)
                }

                // Draw the filled polygons from the pixel buffer
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val color = Color(pixelBuffer[y * width + x])
                        if (color != Color.Transparent) {
                            val tx = (x - width / 2).toFloat()
                            val ty = (y - height / 2).toFloat()
                            drawRect(
                                color = color,
                                topLeft = Offset(tx, ty),
                                size = Size(1f, 1f)
                            )
                        }
                    }
                }
            }

            if (showWireframe) {
                mesh?.triangles?.forEach { triangle ->
                    val p1 = transformToScreen(triangle.v1, width.toFloat(), height.toFloat())
                    val p2 = transformToScreen(triangle.v2, width.toFloat(), height.toFloat())
                    val p3 = transformToScreen(triangle.v3, width.toFloat(), height.toFloat())

                    drawLine(lineColor, Offset(p1.x, p1.y), Offset(p2.x, p2.y))
                    drawLine(lineColor, Offset(p2.x, p2.y), Offset(p3.x, p3.y))
                    drawLine(lineColor, Offset(p3.x, p3.y), Offset(p1.x, p1.y))
                }
            }
        }
    }
}