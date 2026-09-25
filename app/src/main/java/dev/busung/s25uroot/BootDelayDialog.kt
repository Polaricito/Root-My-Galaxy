package dev.busung.s25uroot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun BootDelayDialog(
    currentDelay: Int,
    minDelay: Int,
    maxDelay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var delayValue by remember { mutableStateOf(currentDelay.toString()) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
        title = {
            Text(stringResource(R.string.boot_delay))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.boot_delay_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = delayValue,
                    onValueChange = {
                        delayValue = it
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error,
                    label = { Text(stringResource(R.string.boot_delay)) },
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.boot_delay_range,
                                minDelay,
                                maxDelay,
                            )
                        )
                    },
                )
                if (error) {
                    Text(
                        stringResource(R.string.boot_delay_range, minDelay, maxDelay),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickDelayButton(
                        delay = minDelay,
                        selected = currentDelay == minDelay,
                        onClick = { delayValue = minDelay.toString(); error = false }
                    )
                    QuickDelayButton(
                        delay = (minDelay + maxDelay) / 2,
                        selected = currentDelay == (minDelay + maxDelay) / 2,
                        onClick = { delayValue = ((minDelay + maxDelay) / 2).toString(); error = false }
                    )
                    QuickDelayButton(
                        delay = maxDelay,
                        selected = currentDelay == maxDelay,
                        onClick = { delayValue = maxDelay.toString(); error = false }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = delayValue.toIntOrNull()
                    if (parsed != null && parsed in minDelay..maxDelay) {
                        onConfirm(parsed)
                    } else {
                        error = true
                    }
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun QuickDelayButton(
    delay: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(R.string.boot_delay_seconds, delay),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
