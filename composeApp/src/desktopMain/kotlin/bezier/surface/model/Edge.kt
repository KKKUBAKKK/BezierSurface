package bezier.surface.model

data class Edge(
    var yMax: Int,
    var xMin: Float,
    var slopeInverse: Float,
    var normal: Point3D,  // Normal vector for lighting calculation
    var zValue: Float
)