package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

class WebAppInterface(private val onConsoleMessage: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(type: String, message: String) {
        onConsoleMessage("[$type] $message")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSandboxView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    onConsoleLog: (String) -> Unit
) {
    AndroidView(
        modifier = modifier.fillMaxSize().background(Color.White),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = WebViewClient()

                val jsInterface = WebAppInterface(onConsoleLog)
                addJavascriptInterface(jsInterface, "AuraAndroid")
            }
        },
        update = { webView ->
            // Inject console interception wrapper
            val wrappedHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <script>
                        (function() {
                            const originalLog = console.log;
                            const originalWarn = console.warn;
                            const originalError = console.error;

                            console.log = function(...args) {
                                originalLog.apply(console, args);
                                if (window.AuraAndroid) {
                                    window.AuraAndroid.postMessage('LOG', args.join(' '));
                                }
                            };
                            console.warn = function(...args) {
                                originalWarn.apply(console, args);
                                if (window.AuraAndroid) {
                                    window.AuraAndroid.postMessage('WARN', args.join(' '));
                                }
                            };
                            console.error = function(...args) {
                                originalError.apply(console, args);
                                if (window.AuraAndroid) {
                                    window.AuraAndroid.postMessage('ERROR', args.join(' '));
                                }
                            };
                            window.onerror = function(msg, url, line) {
                                if (window.AuraAndroid) {
                                    window.AuraAndroid.postMessage('ERROR', msg + ' at line ' + line);
                                }
                            };
                        })();
                    </script>
                </head>
                <body>
                    $htmlContent
                </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL(null, wrappedHtml, "text/html", "utf-8", null)
        }
    )
}
