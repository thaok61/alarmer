package com.thao_soft.alarmer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp


@Composable
fun TextAndIcon(
    modifier: Modifier,
    text: String,
    @DrawableRes icon: Int,
    iconVisible: Boolean,
    content: @Composable (() -> Unit) = { },
    onClick: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier
                .weight(5f)
                .clickable {
                    if (onClick != null) {
                        onClick()
                    }
                }) {
            AnimatedVisibility(iconVisible) {
                Image(
                    painterResource(icon),
                    "Label",
                    colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(text = text)
        }
        content()

    }
}
