package com.example.myapplication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phnem.vetro.R
import com.example.myapplication.ui.shared.theme.BrandBlue
import com.example.myapplication.ui.shared.theme.DarkSurface
import com.example.myapplication.ui.shared.theme.SnProFamily

@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onGuestClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    val cardBackgroundColor = if (isDark) DarkSurface else Color(0xFFF0F2F5)

    val titleColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val bodyColor = if (isDark) Color(0xFF9898A0) else Color(0xFF636366)

    val cardBorder = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_waves),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
        ) {
            Card(
                modifier = Modifier
                    .padding(top = 40.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 30.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = Color.Black.copy(alpha = 0.4f)
                    ),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
                border = cardBorder
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 56.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.welcome_title) + " ✨",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = SnProFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        color = titleColor,
                        lineHeight = 32.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(id = R.string.welcome_body) + " 😎",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = SnProFamily,
                        textAlign = TextAlign.Center,
                        color = bodyColor,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlue,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.btn_login),
                            fontSize = 18.sp,
                            fontFamily = SnProFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(id = R.string.btn_guest),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = SnProFamily,
                        fontWeight = FontWeight.Bold,
                        color = bodyColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onGuestClick() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(80.dp)
                    .shadow(elevation = 10.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(cardBackgroundColor)
                    .padding(4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ico),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}