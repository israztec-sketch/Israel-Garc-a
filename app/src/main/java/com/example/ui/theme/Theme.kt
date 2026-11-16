package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

val MohanaDarkColorScheme: ColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = AmberGoldDark,
    primaryContainer = AmberGoldContainer,
    onPrimaryContainer = AmberGoldText,
    inversePrimary = AmberGoldContainer,
    secondary = CrimsonSuspense,
    onSecondary = CrimsonDark,
    secondaryContainer = CrimsonContainer,
    onSecondaryContainer = CrimsonText,
    tertiary = NeuralTeal,
    onTertiary = ObsidianDeep,
    tertiaryContainer = NeuralTealContainer,
    onTertiaryContainer = NeuralTealText,
    background = ObsidianDeep,
    onBackground = AlabasterText,
    surface = ObsidianSurface,
    onSurface = AlabasterText,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = MutedSlateText,
    surfaceTint = AmberGold,
    surfaceDim = ObsidianDeep,
    surfaceBright = ObsidianSurfaceBright,
    surfaceContainerLowest = ObsidianLowest,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianElevated,
    surfaceContainerHighest = ObsidianHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = ErrorCoral,
    onError = ErrorInk,
    errorContainer = ErrorCrimsonContainer,
    onErrorContainer = ErrorCrimsonText,
    inverseSurface = AlabasterText,
    inverseOnSurface = ObsidianDeep,
    scrim = ObsidianScrim
)

private val MohanaTextSelectionColors = TextSelectionColors(
    handleColor = AmberGold,
    backgroundColor = AmberGold.copy(alpha = 0.28f)
)

val MohanaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun mohanaOutlinedTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    cursorColor = MaterialTheme.colorScheme.primary,
    errorCursorColor = MaterialTheme.colorScheme.error,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun MohanaAiTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTextSelectionColors provides MohanaTextSelectionColors) {
        MaterialTheme(
            colorScheme = MohanaDarkColorScheme,
            typography = MohanaTypography,
            shapes = MohanaShapes,
            content = content
        )
    }
}
