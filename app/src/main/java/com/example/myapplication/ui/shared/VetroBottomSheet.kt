package com.example.myapplication.ui.shared

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Универсальная шторка приложения: инкапсулирует физику (InertialCollisionState),
 * единый стиль (скругление всех 4 углов) и Slot API для контента.
 *
 * Решает:
 * - DRY: не дублировать LaunchedEffect и rememberInertialCollisionState на каждом экране.
 * - Issue #2: [RoundedCornerShape(24.dp)] задаётся у [ModalBottomSheet], а не только у контента —
 *   скругляется сама поверхность шторки M3, а не только внутренний контент.
 * - Единообразие: одна точка настройки физики и формы для всех шторок.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetroBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    impactForce: Float = 45f,
    stiffness: Float = 200f,
    dampingRatio: Float = 0.5f,
    content: @Composable (InertialCollisionState) -> Unit
) {
    val collisionState = rememberInertialCollisionState()

    LaunchedEffect(Unit) {
        collisionState.triggerCollision(
            impactForce = impactForce,
            stiffness = stiffness,
            dampingRatio = dampingRatio
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(24.dp)
    ) {
        content(collisionState)
    }
}
