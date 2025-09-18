// file: com/example/m/ui/common/ClickableHtmlText.kt
package com.example.m.ui.common

import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.text.HtmlCompat

private const val URL_TAG = "URL"

fun Spanned.toAnnotatedString(
    primaryColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(this@toAnnotatedString.toString())
        val urlSpans = getSpans(0, length, URLSpan::class.java)
        urlSpans.forEach { span ->
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            addStyle(
                style = SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.None
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = URL_TAG,
                annotation = span.url,
                start = start,
                end = end
            )
        }
    }
}

@Composable
fun ClickableHtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onClick: (url: String) -> Unit
) {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val annotatedString = spanned.toAnnotatedString(primaryColor = MaterialTheme.colorScheme.primary)

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotatedString.getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()?.let { annotation ->
                    onClick(annotation.item)
                }
        },
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout
    )
}