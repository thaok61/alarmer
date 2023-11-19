package com.thao_soft.alarmer.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester


@Composable
fun LabelDialog(
    defaultInput: String = "",
    onDismissRequest: () -> Unit,
    onConfirmation: (textInput: String) -> Unit,
) {
    var text by remember { mutableStateOf(defaultInput) }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        text = {
            OutlinedTextField(
                text,
                onValueChange = { text = it },
                label = { Text("Label") },
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation(text)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Cancel")
            }
        }
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}