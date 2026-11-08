package com.yachiqo.spotgo.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R

// Google's own account picker uses a light sheet regardless of the app theme.
private val GoogleSheetBackground = Color.White
private val GooglePrimaryText = Color(0xFF202124)
private val GoogleSecondaryText = Color(0xFF5F6368)
private val GoogleBlue = Color(0xFF1A73E8)

/**
 * Simulated Google "Choose an account" bottom sheet (no backend): lists the
 * available Google accounts and lets the driver pick one to continue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleAccountChooserSheet(
    accounts: List<String>,
    onSelectAccount: (String) -> Unit,
    onUseAnotherAccount: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = GoogleSheetBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFDADCE0)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.google_choose_title),
                style = MaterialTheme.typography.titleLarge,
                color = GooglePrimaryText,
            )
            Text(
                stringResource(R.string.google_choose_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = GoogleSecondaryText,
            )
            Spacer(Modifier.height(8.dp))
            accounts.forEach { account ->
                GoogleAccountRow(
                    primaryText = account,
                    secondaryText = null,
                    avatarText = account.firstOrNull()?.uppercase() ?: "?",
                    onClick = { onSelectAccount(account) },
                )
            }
            GoogleAccountRow(
                primaryText = stringResource(R.string.google_use_another),
                secondaryText = null,
                avatarText = "+",
                onClick = onUseAnotherAccount,
            )
        }
    }
}

@Composable
private fun GoogleAccountRow(
    primaryText: String,
    secondaryText: String?,
    avatarText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (avatarText == "+" ) Color(0xFFF1F3F4) else GoogleBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                avatarText,
                style = MaterialTheme.typography.titleMedium,
                color = if (avatarText == "+") GoogleSecondaryText else Color.White,
            )
        }
        Column {
            Text(primaryText, style = MaterialTheme.typography.bodyLarge, color = GooglePrimaryText)
            secondaryText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = GoogleSecondaryText)
            }
        }
    }
}
