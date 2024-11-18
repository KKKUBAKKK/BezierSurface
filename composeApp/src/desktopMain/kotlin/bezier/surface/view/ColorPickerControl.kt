package bezier.surface.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bezier.surface.model.Point3D


@Composable
fun ColorPickerControl(
    initialColor: Point3D,
    onColorSelected: (Point3D) -> Unit
) {
    // Separate red, green, and blue color components
    var red by remember { mutableStateOf(initialColor.x) }
    var green by remember { mutableStateOf(initialColor.y) }
    var blue by remember { mutableStateOf(initialColor.z) }

    Column(modifier = Modifier.padding(16.dp)) {
        // Display color preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(Color(red.toFloat(), green.toFloat(), blue.toFloat()))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Red Slider
        Text("R: $red", fontSize = 12.sp)
        Slider(
            value = red.toFloat(),
            onValueChange = {
                red = it.toDouble()
                onColorSelected(Point3D(red, green, blue))
            },
            valueRange = 0f..1f,
//            colors = SliderDefaults.colors(
//                thumbColor = Color.Red,
//                activeTrackColor = Color.Red
//            )
        )

        // Green Slider
        Text("G: $green", fontSize = 12.sp)
        Slider(
            value = green.toFloat(),
            onValueChange = {
                green = it.toDouble()
                onColorSelected(Point3D(red, green, blue))
            },
            valueRange = 0f..1f,
//            colors = SliderDefaults.colors(
//                thumbColor = Color.Green,
//                activeTrackColor = Color.Green
//            )
        )

        // Blue Slider
        Text("B: $blue", fontSize = 12.sp)
        Slider(
            value = blue.toFloat(),
            onValueChange = {
                blue = it.toDouble()
                onColorSelected(Point3D(red, green, blue))
            },
            valueRange = 0f..1f,
//            colors = SliderDefaults.colors(
//                thumbColor = Color.Blue,
//                activeTrackColor = Color.Blue
//            )
        )
    }
}
