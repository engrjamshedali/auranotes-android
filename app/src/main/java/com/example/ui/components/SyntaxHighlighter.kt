package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

object SyntaxHighlighter {

    private val KEYWORDS = setOf(
        "def", "class", "import", "from", "return", "if", "elif", "else", "for", "while",
        "in", "as", "with", "try", "except", "finally", "raise", "pass", "break", "continue",
        "lambda", "yield", "global", "nonlocal", "assert", "del", "async", "await", "fun",
        "val", "var", "package", "override", "public", "private", "protected", "internal",
        "interface", "object", "companion", "data", "sealed", "enum", "const", "typealias",
        "function", "const", "let", "export", "default", "switch", "case", "new", "this",
        "typeof", "instanceof", "void", "static", "final", "abstract", "extends", "implements",
        "select", "from", "where", "insert", "into", "values", "update", "delete", "create",
        "table", "primary", "key", "null", "not", "join", "group", "by", "order", "desc", "asc"
    )

    private val BUILTINS = setOf(
        "print", "println", "len", "range", "sum", "min", "max", "str", "int", "float",
        "bool", "list", "dict", "set", "tuple", "map", "filter", "console", "log", "document",
        "window", "Math", "String", "Number", "Array", "JSON", "true", "false", "True", "False", "None"
    )

    fun highlight(code: String, language: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)

            val lines = code.lines()
            var currentOffset = 0

            for (line in lines) {
                // Comments
                val commentIdx = when (language.lowercase()) {
                    "python", "py", "bash", "sh" -> line.indexOf('#')
                    "sql" -> line.indexOf("--")
                    else -> line.indexOf("//")
                }

                if (commentIdx != -1) {
                    addStyle(
                        SpanStyle(color = CodeComment),
                        currentOffset + commentIdx,
                        currentOffset + line.length
                    )
                }

                currentOffset += line.length + 1 // +1 for newline
            }

            // Word token match (Keywords & Builtins)
            val regex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
            regex.findAll(code).forEach { match ->
                val word = match.value
                val start = match.range.first
                val end = match.range.last + 1

                if (word in KEYWORDS) {
                    addStyle(
                        SpanStyle(color = CodeKeyword, fontWeight = FontWeight.SemiBold),
                        start,
                        end
                    )
                } else if (word in BUILTINS) {
                    addStyle(
                        SpanStyle(color = CodeFunction, fontWeight = FontWeight.Medium),
                        start,
                        end
                    )
                }
            }

            // Strings: "..." or '...'
            val stringRegex = Regex("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
            stringRegex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = CodeString),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Numbers
            val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
            numberRegex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = CodeNumber),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }
}
