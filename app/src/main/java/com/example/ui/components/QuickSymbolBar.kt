package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple iPadOS / Xcode Accessory Bar style Quick Symbol Bar:
 * Each key responds immediately on pointer-down with spring scaling and haptic feedback.
 */
@Composable
fun QuickSymbolBar(
    modifier: Modifier = Modifier,
    onInsertSymbol: (String) -> Unit
) {
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
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        symbols.forEach { (label, insertValue) ->
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = 34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .appleSpringClickable(
                        pressedScale = 0.92f,
                        enableHaptics = true,
                        onClick = { onInsertSymbol(insertValue) }
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
}
