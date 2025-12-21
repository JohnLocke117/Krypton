package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.UbuntuSans_Regular

@Composable
@Preview
fun App() {
    val ubuntuFontFamily = FontFamily(
        Font(Res.font.UbuntuSans_Regular)
    )

    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = MaterialTheme.typography.copy(
            displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = ubuntuFontFamily),
            displayMedium = MaterialTheme.typography.displayMedium.copy(fontFamily = ubuntuFontFamily),
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = ubuntuFontFamily),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = ubuntuFontFamily),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = ubuntuFontFamily),
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontFamily = ubuntuFontFamily),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = ubuntuFontFamily),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = ubuntuFontFamily),
            titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = ubuntuFontFamily),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = ubuntuFontFamily),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = ubuntuFontFamily),
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = ubuntuFontFamily),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = ubuntuFontFamily),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = ubuntuFontFamily),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = ubuntuFontFamily)
        )
    ) {
        val state = rememberEditorState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // File Explorer Card
                Card(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    FileExplorer(
                        state = state,
                        onFolderSelected = {
                            openFolderDialog { selectedPath ->
                                selectedPath?.let { path ->
                                    val file = path.toFile()
                                    if (file.isDirectory) {
                                        state.changeDirectory(path)
                                    } else {
                                        state.changeDirectory(file.parentFile?.toPath())
                                        state.openTab(path)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Editor Section Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        TabBar(
                            state = state,
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextEditor(
                            state = state,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

fun openFolderDialog(onResult: (java.nio.file.Path?) -> Unit) {
    val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
    fileChooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
    fileChooser.dialogTitle = "Select Folder or File"
    
    val result = fileChooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val selectedFile = fileChooser.selectedFile
        onResult(selectedFile.toPath())
    } else {
        onResult(null)
    }
}

fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFBB86FC),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF000000),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF6200EE),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFEADDFF),
    secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF000000),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF018786),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFA0E7E1),
    tertiary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF000000),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF018786),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFA0E7E1),
    error = androidx.compose.ui.graphics.Color(0xFFCF6679),
    onError = androidx.compose.ui.graphics.Color(0xFF000000),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFB00020),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    background = androidx.compose.ui.graphics.Color(0xFF121212),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2C),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC4C4C4),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2D2D2D),
    outline = androidx.compose.ui.graphics.Color(0xFF5F5F5F),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF3F3F3F)
)