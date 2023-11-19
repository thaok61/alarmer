package com.thao_soft.alarmer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Composable
fun ChipDay(modifier: Modifier, text: String, selected: Boolean, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxWidth()) {
        AssistChip(
            modifier = Modifier
                .size(50.dp)
                .padding(4.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ),
            onClick = { onClick() },
            label = {
                Text(
                    text = text,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = CircleShape,
            border = AssistChipDefaults.assistChipBorder(
                borderColor =
                if (selected) Color.Transparent
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

    }
}
