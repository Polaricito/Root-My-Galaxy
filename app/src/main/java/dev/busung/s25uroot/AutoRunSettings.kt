package dev.busung.s25uroot

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/**
 * Settings surface for the boot auto-apply loop.
 *
 * Every component here is built from the cards and dialogs the rest of the
 * settings screen already uses, so auto-run reads as part of the app instead
 * of a bolted-on panel. The point of these components is visibility: the loop
 * used to fail silently, leaving an armed toggle indistinguishable from a
 * dead one, so each state gets a line the user can act on.
 */

/**
 * Armed/off state for the toggle card. Deliberately says nothing about
 * timing: that belongs to [autoRunStatusLine], which the status card owns.
 * A pause outranks the enabled state so the reason stays visible.
 */
@Composable
internal fun autoRunSwitchSummary(status: AutoRunStatus): String =
    autoRunPauseSummary(status) ?: when {
        !status.enabled -> stringResource(R.string.auto_run_summary_off)
        status.inProgress -> stringResource(R.string.auto_run_summary_running)
        else -> stringResource(R.string.auto_run_summary_on)
    }

@Composable
private fun autoRunPauseSummary(status: AutoRunStatus): String? {
    val suspended = status.suspended ?: return null
    return if (suspended == AutoRunSuspendReason.Bootloop) {
        stringResource(R.string.auto_run_suspended_bootloop)
    } else {
        stringResource(R.string.auto_run_suspended_attempts, status.attempts)
    }
}

/**
 * Reports the loop state and carries the one action that makes sense in it:
 * re-arming a paused loop, or firing a run on demand. The action is spelled
 * out in the trailing slot so a status card is never a hidden button.
 */
@Composable
internal fun AutoRunStatusCard(
    status: AutoRunStatus,
    position: SettingsCardPosition,
    onRunNow: () -> Unit,
    onReset: () -> Unit,
) {
    val paused = status.suspended != null
    SettingsCard(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.auto_run_status),
        description = autoRunStatusLine(status),
        value = stringResource(if (paused) R.string.action_rearm else R.string.action_run_now),
        position = position,
        onClick = if (paused) onReset else onRunNow,
    )
}

/**
 * Resolves the pending alarm into a wall-clock time for display. Exposed
 * separately from [AutoRunManager.nextRunWallClockMillis] so the UI can render
 * a status snapshot it already holds without re-reading preferences.
 */
internal fun autoRunNextRunWallClock(status: AutoRunStatus): Long? {
    val triggerElapsed = status.nextTriggerElapsedMillis ?: return null
    val remaining = triggerElapsed - SystemClock.elapsedRealtime()
    if (remaining <= 0L) return null
    return System.currentTimeMillis() + remaining
}

/**
 * The single sentence both the toggle and the status card show.
 *
 * A pause always outranks the enabled state so the reason stays on screen
 * until the user re-arms, and an armed-but-unarmed-loop state is reported as
 * "waiting for the next boot" rather than looking identical to a dead toggle.
 */
@Composable
internal fun autoRunStatusLine(status: AutoRunStatus): String {
    autoRunPauseSummary(status)?.let { return it }
    if (status.inProgress) return stringResource(R.string.auto_run_summary_running)
    if (!status.enabled) return stringResource(R.string.auto_run_summary_off)
    // Booted but still locked: nothing is scheduled yet, and saying so stops
    // an armed toggle from looking broken while the user has not picked up.
    if (status.awaitingUnlock) return stringResource(R.string.auto_run_awaiting_unlock)
    val nextRun = autoRunNextRunWallClock(status) ?: return stringResource(R.string.auto_run_awaits_boot)
    val time = DateFormat
        .getTimeInstance(DateFormat.SHORT, LocalConfiguration.current.locales[0])
        .format(Date(nextRun))
    // An inexact alarm is still armed, but One UI decides when it fires.
    return if (status.nextTriggerExact) {
        stringResource(R.string.auto_run_next_run_at, time)
    } else {
        stringResource(R.string.auto_run_next_run_at_inexact, time)
    }
}

/** Human readable wait, so the retry policy reads in minutes instead of 7200. */
@Composable
internal fun formatDelayLabel(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val rest = total % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.auto_run_delay_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.auto_run_delay_hours, hours)
        minutes > 0 && rest > 0 -> stringResource(R.string.auto_run_delay_minutes_seconds, minutes, rest)
        minutes > 0 -> stringResource(R.string.auto_run_delay_minutes, minutes)
        else -> stringResource(R.string.auto_run_delay_seconds, rest)
    }
}

@Composable
internal fun AutoRunRetryDialog(
    maxAttempts: Int,
    baseDelaySeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var attemptsValue by remember { mutableStateOf(maxAttempts.toString()) }
    var error by remember { mutableStateOf(false) }
    val rangeText = stringResource(
        R.string.auto_run_retry_range,
        AutoRunPolicy.MIN_ATTEMPTS,
        AutoRunPolicy.MAX_ATTEMPTS,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Replay, contentDescription = null) },
        title = { Text(stringResource(R.string.auto_run_retry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.auto_run_retry_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = attemptsValue,
                    onValueChange = {
                        attemptsValue = it
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error,
                    label = { Text(stringResource(R.string.auto_run_retry_max_attempts)) },
                    supportingText = { Text(rangeText) },
                )
                if (error) {
                    Text(
                        text = rangeText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickAttemptsButton(AutoRunPolicy.MIN_ATTEMPTS) { attemptsValue = it }
                    QuickAttemptsButton(AutoRunPolicy.DEFAULT_MAX_ATTEMPTS) { attemptsValue = it }
                    QuickAttemptsButton(AutoRunPolicy.MAX_ATTEMPTS) { attemptsValue = it }
                }
                Text(
                    text = stringResource(
                        R.string.auto_run_retry_backoff,
                        formatDelayLabel(AutoRunPolicy.retryDelaySeconds(baseDelaySeconds, 1)),
                        formatDelayLabel(AutoRunPolicy.retryDelaySeconds(baseDelaySeconds, maxAttempts)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = attemptsValue.toIntOrNull()
                    if (parsed != null &&
                        parsed in AutoRunPolicy.MIN_ATTEMPTS..AutoRunPolicy.MAX_ATTEMPTS
                    ) {
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
private fun QuickAttemptsButton(attempts: Int, onPick: (String) -> Unit) {
    Button(
        onClick = { onPick(attempts.toString()) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(R.string.auto_run_retry_attempts_value, attempts),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Shown only when notifications are blocked. Without them auto-run still arms,
 * but the user is never told a run is waiting, so the exploit cannot reach the
 * foreground it needs.
 */
@Composable
internal fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
        title = { Text(stringResource(R.string.auto_run_notify_title)) },
        text = {
            Text(
                text = stringResource(R.string.auto_run_notify_permission_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                clickHaptic(view)
                onGrant()
            }) {
                Text(stringResource(R.string.action_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun clickHaptic(view: View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}