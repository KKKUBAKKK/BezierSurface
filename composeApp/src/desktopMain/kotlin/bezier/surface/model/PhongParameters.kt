package bezier.surface.model

import androidx.compose.ui.graphics.Color

data class PhongParameters(
    val kd: Double,  // diffuse reflection coefficient
    val ks: Double,  // specular reflection coefficient
    val m: Double,   // shininess coefficient
    val lightColor: Point3D,  // IL - light color
    val objectColor: Point3D, // IO - object color
    val lightPosition: Point3D  // light source position
)