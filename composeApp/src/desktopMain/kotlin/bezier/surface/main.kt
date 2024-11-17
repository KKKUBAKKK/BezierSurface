package bezier.surface

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

import androidx.compose.material.*
import androidx.compose.ui.graphics.Color
import bezier.surface.view.BezierSurfaceViewer

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Bezier Surface Viewer",
        resizable = true
    ) {
        this.window.setSize(3200, 2400)
        MaterialTheme {
            Surface(color = Color.DarkGray) {
                BezierSurfaceViewer()
            }
        }
    }
}
