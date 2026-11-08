package com.yachiqo.spotgo.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoError
import com.yachiqo.spotgo.ui.theme.SpotGoOnAccent
import com.yachiqo.spotgo.ui.theme.SpotGoSecondaryText
import com.yachiqo.spotgo.ui.theme.SpotGoSurface
import com.yachiqo.spotgo.ui.theme.SpotGoText

/**
 * Bottom sheet used by the Figma state screens (invalid login, invalid form,
 * duplicate e-mail, session expired, reservation exceptions…): title,
 * supporting message, coloured tag, primary accent button and secondary
 * outlined button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateBottomSheet(
    title: String,
    message: String,
    tag: String? = null,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    tagColor: Color = SpotGoError,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = SpotGoSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = SpotGoText)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = SpotGoSecondaryText)
            if (tag != null) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = tagColor)
            }
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotGoAccent,
                    contentColor = SpotGoOnAccent,
                ),
            ) {
                Text(primaryText, style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotGoText),
            ) {
                Text(secondaryText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
