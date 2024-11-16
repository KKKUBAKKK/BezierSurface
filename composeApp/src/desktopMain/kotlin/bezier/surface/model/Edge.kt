package bezier.surface.model

data class Edge(
    var yMax: Int,
    var xMin: Float,
    var slopeInverse: Float,
    var normal: Point3D,  // Normal vector for lighting calculation
    var zValue: Float,
    // Adding fields needed for proper sorting
    var currentX: Float,
    var startY: Int
)