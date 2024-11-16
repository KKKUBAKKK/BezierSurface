package bezier.surface.model

import androidx.compose.ui.graphics.Color

data class LightingParameters(
    val kd: Double, // diffuse coefficient
    val ks: Double, // specular coefficient
    val m: Double,  // specular power (1-100)
    val lightDirection: Point3D, // L vector
    val viewerPosition: Point3D, // V vector
    val objectColor: Color // IO color
)