package bezier.surface.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bezier.surface.viewmodel.BezierSurfaceViewModel
import java.awt.FileDialog
import java.awt.Frame


@Composable
fun BezierSurfaceViewer() {
    val viewModel = remember { BezierSurfaceViewModel() }

    Row(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        // Surface display
        Canvas(modifier = Modifier
            .weight(2f)
            .fillMaxHeight()
            .padding(end = 16.dp),
        ) {
//            this.translate(this.size.width / 2, this.size.height / 2) {
                viewModel.drawSurface(this, width = this.size.width.toInt(), height = this.size.height.toInt())
//            }
        }

        // Controls
        Column(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(8.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Display mode controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = viewModel.showWireframe,
                        onCheckedChange = {
                            if (it || viewModel.showFilled) {
                                viewModel.showWireframe = it
                            }
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp)) // Adjust spacing between checkbox and label
                    Text("Wireframe", fontSize = 12.sp)
//                }

                Spacer(modifier = Modifier.width(16.dp)) // Adjust spacing between checkboxes

//                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = viewModel.showFilled,
                        onCheckedChange = {
                            if (it || viewModel.showWireframe) {
                                viewModel.showFilled = it
                            }
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp)) // Adjust spacing between checkbox and label
                    Text("Filled", fontSize = 12.sp)
//                }

                Spacer(modifier = Modifier.width(16.dp)) // Adjust spacing between checkboxes

//                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = viewModel.lightMove,
                        onCheckedChange = {
                            if (it || viewModel.showFilled) {
                                viewModel.lightMove = it
                            }
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp)) // Adjust spacing between checkbox and label
                    Text("Light move", fontSize = 12.sp)
//                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {

                Button(
                    onClick = {
                        if (viewModel.showFilled)
                            viewModel.newMapping(openFileDialog("src/Resources/normal_maps"))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
                ) {
                    Text("Choose normal map", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (viewModel.showFilled)
                            viewModel.newTexture(openFileDialog("src/Resources/textures"))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
                ) {
                    Text("Choose texture", fontSize = 12.sp)
                }
            }

            // Rotation controls
            Text("Alpha", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = viewModel.rotationZAlpha,
                onValueChange = { viewModel.rotationZAlpha = it },
                valueRange = 0f..3.14f,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )

            Text("Beta", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = viewModel.rotationXBeta,
                onValueChange = { viewModel.rotationXBeta = it },
                valueRange = 0f..3.14f,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )

            // Resolution control
            Text("Triangulation", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = viewModel.resolution.toFloat(),
                onValueChange = { viewModel.resolution = it.toInt() },
                valueRange = 3f..50f,
                steps = 20,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )

            // Resolution control
            Text("kd", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = viewModel.kd,
                onValueChange = { viewModel.kd = it },
                valueRange = 0f..0.1f,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )

            // Resolution control
            Text("ks", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = viewModel.ks,
                onValueChange = { viewModel.ks = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )

            // Resolution control
            Text("m", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = viewModel.m,
                onValueChange = { viewModel.m = it },
                valueRange = 1f..100f,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )

            // Color picker
            Text("Light Color", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            ColorPickerControl(
                initialColor = viewModel.lightColor,
                onColorSelected = { viewModel.lightColor = it }
            )
        }
    }
}

fun openFileDialog(defaultDirectory: String = System.getProperty("user.home")): String? {
    val fileDialog = FileDialog(null as Frame?, "Select a File", FileDialog.LOAD)
    fileDialog.directory = defaultDirectory
    fileDialog.isVisible = true
    return fileDialog.file?.let { fileDialog.directory + it }
}
