package dev.rouzbeh.hybridvpn.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.rouzbeh.hybridvpn.core.designsystem.BackgroundDark
import dev.rouzbeh.hybridvpn.core.designsystem.PrimaryCyan

/**
 * Reusable full-screen scaffold providing a minimal, high-contrast Cyberpunk/Glass settings layout.
 *
 * Enforces:
 * - Strict LTR layout direction locking ([LocalLayoutDirection]).
 * - Interception of system Back gestures via [BackHandler].
 * - Full-screen edge-to-edge system insets (status bars, display cutout, navigation bars, IME).
 * - Background touch consumption to prevent gestures/clicks bleeding into the dashboard underneath.
 * - Subtle ambient radial glow behind content.
 * - Sticky bottom action bar slot cleanly positioned above the keyboard.
 * - Optional bottom glass toast slot.
 */
@Composable
fun SettingsFullScreenScaffold(
    title: String,
    subtitle: String? = null,
    accentColor: Color = PrimaryCyan,
    iconRes: Int? = null,
    iconVector: ImageVector? = null,
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    toastMessage: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BackHandler(enabled = true) { onBack() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume touches to prevent touch pass-through */ }
        ) {
            // Subtle ambient top radial glow
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.TopCenter)
            ) {
                val center = Offset(size.width / 2f, 0f)
                val radius = size.width * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.09f),
                            accentColor.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Top Header Bar
                SettingsTopHeaderBar(
                    title = title,
                    subtitle = subtitle,
                    accentColor = accentColor,
                    iconRes = iconRes,
                    iconVector = iconVector,
                    onBack = onBack
                )

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                        .then(
                            if (bottomBar == null) Modifier.navigationBarsPadding()
                            else Modifier
                        ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    content = content
                )

                // Sticky Bottom Action Bar Slot
                if (bottomBar != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A1020).copy(alpha = 0.96f))
                            .drawBehind {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        bottomBar()
                    }
                }
            }

            // Embedded Toast Overlay Slot
            if (toastMessage != null) {
                CustomGlassToast(
                    message = toastMessage,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun SettingsTopHeaderBar(
    title: String,
    subtitle: String? = null,
    accentColor: Color = PrimaryCyan,
    iconRes: Int? = null,
    iconVector: ImageVector? = null,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular glass back button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = SoraFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Optional trailing protocol/feature badge
            if (iconRes != null || iconVector != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Thin hairline bottom divider
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.07f)
        )
    }
}

/**
 * Grouped section card with tracked uppercase header and dark glass background.
 */
@Composable
fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    accentColor: Color = PrimaryCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SoraFamily,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = if (subtitle != null) 2.dp else 8.dp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

/**
 * Ergonomic toggle row with clear title, description, and themed Switch.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = PrimaryCyan
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SoraFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                lineHeight = 17.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.35f),
                checkedBorderColor = accentColor,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B),
                uncheckedBorderColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

/**
 * Technical text input with forced LTR direction, optional caption, and preset quick-select chips.
 */
@Composable
fun SettingsTextFieldWithPresets(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    caption: String? = null,
    presets: List<Pair<String, String>> = emptyList(),
    keyboardType: KeyboardType = KeyboardType.Text,
    accentColor: Color = PrimaryCyan
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    text = label,
                    color = Color(0xFF94A3B8),
                    fontFamily = SoraFamily,
                    fontSize = 12.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = accentColor,
                focusedContainerColor = Color.White.copy(alpha = 0.02f),
                unfocusedContainerColor = Color.Transparent
            ),
            textStyle = TextStyle(
                fontFamily = SoraFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textDirection = TextDirection.Ltr
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )

        if (caption != null) {
            Text(
                text = caption,
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (chipLabel, chipValue) ->
                    val isSelected = value.trim() == chipValue.trim()
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onValueChange(chipValue) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chipLabel,
                            color = if (isSelected) accentColor else Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = SoraFamily
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ergonomic sticky footer bar with a ghost Reset button and primary Cancel/Save buttons.
 */
@Composable
fun SettingsActionFooter(
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    accentColor: Color = PrimaryCyan,
    saveLabel: String = "Save Changes",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onReset,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF94A3B8)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Reset",
                fontFamily = SoraFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.06f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = saveLabel,
                    color = BackgroundDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
                )
            }
        }
    }
}

/**
 * Informational note card for technical hints and default override rules.
 */
@Composable
fun SettingsInfoBanner(
    text: String,
    accentColor: Color = PrimaryCyan,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            lineHeight = 17.sp
        )
    }
}

/**
 * Reusable dark cyberpunk glass dialog prompting the user when unsaved settings exist.
 */
@Composable
fun SettingsUnsavedChangesDialog(
    accentColor: Color = PrimaryCyan,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Unsaved Changes",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "You have unsaved changes. Save them before leaving?",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontFamily = SoraFamily,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF94A3B8)
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = SoraFamily
                                )
                            }

                            Button(
                                onClick = onDiscard,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    contentColor = Color(0xFFFCA5A5)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "Discard",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = SoraFamily
                                )
                            }

                            Button(
                                onClick = onSave,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "Save",
                                    color = BackgroundDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
