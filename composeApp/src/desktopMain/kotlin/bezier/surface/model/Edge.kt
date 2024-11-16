package bezier.surface.model

data class Edge(
    var yMax: Int,
    var x: Float,  // current x position
    var slopeInverse: Float,
    var normal: Point3D,
    var zValue: Float
)