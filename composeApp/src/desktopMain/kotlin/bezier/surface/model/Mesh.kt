package bezier.surface.model

data class Mesh(
    val triangles: List<Triangle>,
    var rotatedTriangles: List<Triangle2D> = emptyList(),
    var canvasWidth: Int = 0,
    var canvasHeight: Int = 0,
    var rotationX: Double = 0.0,
    var rotationZ: Double = 0.0,
    var resolution: Int = 0
)