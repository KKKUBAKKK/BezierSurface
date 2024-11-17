package bezier.surface.model

data class Edge(
    val yMaxIdx: Int,
    val yMax: Double,
    var yIdx: Int,
    var y: Double,
    var xIdx: Int,
    var x: Double,
    val slope: Double,
)