package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        symbols.forEach { (label, insertValue) ->
            OutlinedButton(
                onClick = { onInsertSymbol(insertValue) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
