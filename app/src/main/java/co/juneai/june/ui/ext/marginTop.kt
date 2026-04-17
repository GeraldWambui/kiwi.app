package co.juneai.june.ui.ext

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

fun Modifier.marginTop(value: Dp) = padding(top = value)
fun Modifier.marginBottom(value: Dp) = padding(bottom = value)
fun Modifier.marginStart(value: Dp) = padding(start = value)
fun Modifier.marginEnd(value: Dp) = padding(end = value)