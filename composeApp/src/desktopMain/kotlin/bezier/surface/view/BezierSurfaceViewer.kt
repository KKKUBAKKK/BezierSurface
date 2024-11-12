package bezier.surface.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bezier.surface.viewmodel.BezierSurfaceViewModel

@Composable
fun BezierSurfaceViewer() {
    val viewModel = remember { BezierSurfaceViewModel() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Surface display
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            viewModel.drawSurface(this)
        }

        // Controls
        Column(modifier = Modifier.fillMaxWidth()) {
            // Display mode controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Checkbox(
                    checked = viewModel.showWireframe,
                    onCheckedChange = {
                        if (it || viewModel.showFilled) {
                            viewModel.showWireframe = it
                        }
                    }
                )
                Text("Wireframe")

                Checkbox(
                    checked = viewModel.showFilled,
                    onCheckedChange = {
                        if (it || viewModel.showWireframe) {
                            viewModel.showFilled = it
                        }
                    }
                )
                Text("Filled")
            }

            // Rotation controls
            Text("X Rotation")
            Slider(
                value = viewModel.rotationX,
                onValueChange = { viewModel.rotationX = it },
                valueRange = -3.14f..3.14f
            )

            Text("Z Rotation")
            Slider(
                value = viewModel.rotationZ,
                onValueChange = { viewModel.rotationZ = it },
                valueRange = -3.14f..3.14f
            )

            // Resolution control
            Text("Mesh Resolution")
            Slider(
                value = viewModel.resolution.toFloat(),
                onValueChange = { viewModel.resolution = it.toInt() },
                valueRange = 5f..30f,
                steps = 25
            )
        }
    }
}
