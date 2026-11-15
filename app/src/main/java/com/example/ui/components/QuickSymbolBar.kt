package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAuraNeumorphicPalette

/**
 * Tactile Neumorphic Quick Symbol Keycap Bar for Code Studio.
 */
@Composable
fun QuickSymbolBar(
    modifier: Modifier = Modifier,
    onInsertSymbol: (String) -> Unit
) {
    val neu = LocalAuraNeumorphicPalette.current
    val symbols = listOf(
        "TAB" to "    ",
        "(" to "(",
        ")" to ")",
        "{" to "{",
        "}" to "}",
        "[" to "[",
        "]" to "]",
        "<" to "<",
        ">" to ">",
        "\"" to "\"",
        "'" to "'",
        "=" to " = ",
        ":" to ":",
        ";" to ";",
        "." to ".",
        "," to ", ",
        "/" to "/",
        "\\" to "\\",
        "!" to "!",
        "?" to "?",
        "+" to " + ",
        "-" to " - ",
        "*" to " * ",
        "#" to "# ",
        "_" to "_",
        "$" to "$"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(neu.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        symbols.forEach { (label, insertValue) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = 34.dp)
                    .neuRaised(
                        cornerRadius = 10.dp,
                        backgroundColor = neu.surfaceElevated,
                        shadowOffset = 2.5.dp,
                        blurRadius = 5.dp
                    )
                    .appleSpringClickable(
                        pressedScale = 0.92f,
                        enableHaptics = true,
                        onClick = { onInsertSymbol(insertValue) }
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
