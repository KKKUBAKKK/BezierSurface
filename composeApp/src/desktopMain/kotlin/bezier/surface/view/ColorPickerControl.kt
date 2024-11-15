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


@Composable
fun ColorPickerControl(
    initialColor: Color,
    onColorSelected: (Color) -> Unit
) {
    // Separate red, green, and blue color components
    var red by remember { mutableStateOf((initialColor.red * 255).toInt()) }
    var green by remember { mutableStateOf((initialColor.green * 255).toInt()) }
    var blue by remember { mutableStateOf((initialColor.blue * 255).toInt()) }

    Column(modifier = Modifier.padding(16.dp)) {
        // Display color preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(Color(red / 255f, green / 255f, blue / 255f))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Red Slider
        Text("R: $red", fontSize = 12.sp)
        Slider(
            value = red.toFloat(),
            onValueChange = {
                red = it.toInt()
                onColorSelected(Color(red / 255f, green / 255f, blue / 255f))
            },
            valueRange = 0f..255f,
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
                green = it.toInt()
                onColorSelected(Color(red / 255f, green / 255f, blue / 255f))
            },
            valueRange = 0f..255f,
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
                blue = it.toInt()
                onColorSelected(Color(red / 255f, green / 255f, blue / 255f))
            },
            valueRange = 0f..255f,
//            colors = SliderDefaults.colors(
//                thumbColor = Color.Blue,
//                activeTrackColor = Color.Blue
//            )
        )
    }
}
