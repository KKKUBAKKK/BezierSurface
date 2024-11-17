package bezier.surface.model

import androidx.compose.ui.graphics.Color

data class PhongParameters(
    val kd: Double,  // diffuse reflection coefficient
    val ks: Double,  // specular reflection coefficient
    val m: Double,   // shininess coefficient
    val lightColor: Color,  // IL - light color
    val objectColor: Color, // IO - object color
    val lightPosition: Point3D  // light source position
)