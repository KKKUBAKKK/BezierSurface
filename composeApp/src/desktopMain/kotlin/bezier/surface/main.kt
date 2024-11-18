package bezier.surface

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

import androidx.compose.material.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import bezier.surface.view.BezierSurfaceViewer
import java.awt.Toolkit

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Bezier Surface Viewer",
        resizable = false,
        state = rememberWindowState(width = 1500.dp, height = 900.dp)
    ) {
        MaterialTheme {
            Surface(color = Color.DarkGray) {
                BezierSurfaceViewer()
            }
        }
    }
}
