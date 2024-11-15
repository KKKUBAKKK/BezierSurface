package bezier.surface.model

class Mesh
() {
    // Properties
    val controlPoints: Array<Array<Point3D>>
    var triangles: List<Triangle>

    // Initialize control points and mesh
    init {
        controlPoints = generateControlPoints()
        triangles = generateMesh(20)
    }

    // Generate control points
    fun generateControlPoints(): Array<Array<Point3D>> {
        return Array(4) { i ->
            Array(4) { j ->
                Point3D(
                    x = i.toDouble() - 1.0,
                    y = (if ((i + j) % 2 == 0) 0.5 else -0.5),
                    z = j.toDouble() - 1.0
                )
            }
        }
    }

    // Bernstein polynomial calculation
    private fun bernstein(i: Int, n: Int, t: Double): Double {
        fun binomial(n: Int, k: Int): Int {
            if (k == 0 || k == n) return 1
            return binomial(n - 1, k - 1) + binomial(n - 1, k)
        }

        return binomial(n, i) * Math.pow(t, i.toDouble()) * Math.pow(1 - t, (n - i).toDouble())
    }

    // Calculate point on Bezier surface
    fun calculateSurfacePoint(u: Double, v: Double): Point3D {
        var point = Point3D(0.0, 0.0, 0.0)

        for (i in 0..2) {
            for (j in 0..2) {
                val basis = bernstein(i, 2, u) * bernstein(j, 2, v)
                point += controlPoints[i][j] * basis
            }
        }

        return point
    }

    // Generate mesh of triangles
    fun generateMesh(resolution: Int): List<Triangle> {
        val triangles = mutableListOf<Triangle>()
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
                triangles.add(Triangle(p2, p4, p3))
            }
        }

        return triangles
    }
}