package com.yachiqo.spotgo.presentation.driver.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.common.SpotGoWordmark
import com.yachiqo.spotgo.ui.theme.SpotGoError

internal val DriverAvailable = Color(0xFF44E38B)
internal val DriverAvailableContainer = Color(0xFF18332A)
internal val DriverCardOutline = Color(0xFFCAC4D0)
internal val DriverMapBackground = Color(0xFF171B27)

@Composable
internal fun DriverPage(
    title: String,
    subtitle: String? = null,
    onProfile: (() -> Unit)? = null,
    prominentTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 134.dp).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                SpotGoWordmark(Modifier.weight(1f))
                if (onProfile != null) {
                    IconButton(onClick = onProfile) {
                        Icon(painterResource(R.drawable.ic_profile), stringResource(R.string.driver_open_profile),
                            Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (prominentTitle) 27.sp else 24.sp, lineHeight = if (prominentTitle) 34.sp else 32.sp))
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun DriverPanel(
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (outlined) 1.dp else 0.6.dp,
            if (outlined) DriverCardOutline else MaterialTheme.colorScheme.outline),
    ) { Column(content = content) }
}

@Composable
internal fun StatusBadge(label: String, positive: Boolean = true) {
    Surface(
        color = if (positive) DriverAvailableContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(100.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(if (positive) R.drawable.status_available else R.drawable.status_upcoming),
                contentDescription = null, modifier = Modifier.size(8.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (positive) DriverAvailable else MaterialTheme.colorScheme.primary))
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = if (positive) DriverAvailable else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun DriverPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp)) { Text(label, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
internal fun DriverOutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun DriverSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
}

@Composable
internal fun FavoriteButton(saved: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Saved state completes the fill: the heart goes from outline to solid
    // and the circle behind it fills in, so the press is always visible.
    val tint = animateColorAsState(
        targetValue = if (saved) SpotGoError else LocalContentColor.current,
        label = "favoriteTint",
    ).value
    val container = animateColorAsState(
        targetValue = if (saved) SpotGoError.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
        label = "favoriteContainer",
    ).value
    Surface(modifier = modifier.size(48.dp), shape = RoundedCornerShape(24.dp), color = container) {
        IconButton(onClick = onClick) {
            FigmaIcon(
                if (saved) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
                stringResource(if (saved) R.string.driver_remove_saved_parking else R.string.driver_save_parking),
                tint = tint,
            )
        }
    }
}
