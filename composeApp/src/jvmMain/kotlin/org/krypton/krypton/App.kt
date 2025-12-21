package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
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

    // Initialize settings repository
    val settingsRepository = remember { SettingsRepositoryImpl() }
    val settings by settingsRepository.settingsFlow.collectAsState()
    val colorScheme = buildColorSchemeFromSettings(settings)
    val theme = rememberObsidianTheme(settings)

    MaterialTheme(
        colorScheme = colorScheme,
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
                .background(theme.Background)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                        if (isCtrlOrCmd && keyEvent.key == Key.Comma) {
                            state.openSettingsDialog()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left Ribbon
                LeftRibbon(
                    state = state,
                    modifier = Modifier.fillMaxHeight()
                )

                // Left Sidebar
                LeftSidebar(
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
                    modifier = Modifier.fillMaxHeight()
                )

                // Center Editor Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TabBar(
                        state = state,
                        settings = settings,
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextEditor(
                        state = state,
                        settingsRepository = settingsRepository,
                        onOpenFolder = {
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
                        modifier = Modifier.weight(1f)
                    )
                }

                // Right Sidebar
                RightSidebar(
                    state = state,
                    modifier = Modifier.fillMaxHeight()
                )

                // Right Ribbon
                RightRibbon(
                    state = state,
                    modifier = Modifier.fillMaxHeight()
                )
            }
            
            // Settings Dialog (overlay)
            SettingsDialog(
                state = state,
                settingsRepository = settingsRepository,
                onOpenSettingsJson = {
                    state.openSettingsJson()
                }
            )
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
