package com.example.myapplication.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.shared.components.IosDialogAnimatedContent
import com.example.myapplication.ui.shared.theme.SnProFamily

/**
 * TEMP V3.3.3 player promo. Removal checklist:
 * 1) delete this file; 2) remove its call from HomeScreen;
 * 3) remove TEMP_PLAYER_PROMO_V333_DISMISSED and the two HomeViewModel members.
 */
@Composable
fun PlayerPowerPromoDialog(
    language: AppLanguage,
    onTry: () -> Unit,
    onLater: () -> Unit,
) {
    val ru = language == AppLanguage.RU
    val title = if (ru) {
        "Уровень силы плеера: больше 9000! ⚡"
    } else {
        "Player power level: It’s over 9000! ⚡"
    }
    val body = if (ru) {
        "Попробуй новый плеер: теперь стримим на лету и качаем прямо в оперативку. " +
            "Смотри аниме где угодно — хоть в метро, хоть на скучной лекции."
    } else {
        "Try out the new player: now streaming on the fly and caching directly into RAM. " +
            "Watch anime anywhere — whether on the subway or in a boring lecture."
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        IosDialogAnimatedContent {
            val shape = RoundedCornerShape(30.dp)
            Box(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .widthIn(max = 420.dp)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF4A1008),
                            0.38f to Color(0xFF23100E),
                            1f to MaterialTheme.colorScheme.surface,
                        ),
                    )
                    .border(1.dp, Color(0xFFFF6A24).copy(alpha = 0.42f), shape)
                    .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5A16).copy(alpha = 0.18f))
                            .border(1.dp, Color(0xFFFF6A24).copy(alpha = 0.48f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = Color(0xFFFF6A24),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = SnProFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp,
                            lineHeight = 28.sp,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = body,
                        color = Color.White.copy(alpha = 0.76f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = SnProFamily,
                            lineHeight = 23.sp,
                        ),
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            onClick = onLater,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (ru) "Позже" else "Later")
                        }
                        Button(
                            onClick = onTry,
                            modifier = Modifier.weight(1.35f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF5A16),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = if (ru) "Попробовать" else "Try it",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}