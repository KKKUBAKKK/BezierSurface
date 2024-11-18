package bezier.surface

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

import androidx.compose.material.*
import androidx.compose.ui.graphics.Color
import bezier.surface.view.BezierSurfaceViewer
import java.awt.Toolkit

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Bezier Surface Viewer",
        resizable = true
    ) {
        // get the size of the screen
         val screenSize = Toolkit.getDefaultToolkit().screenSize

        this.window.setSize(screenSize.width / 2, screenSize.height / 2)
        MaterialTheme {
            Surface(color = Color.DarkGray) {
                BezierSurfaceViewer()
            }
        }
    }
}
