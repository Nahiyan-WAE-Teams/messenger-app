package com.nahiyan.miramessenger

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LovelyColors = darkColorScheme(
    primary = Color(0xFFFF789A),
    onPrimary = Color(0xFF260B16),
    primaryContainer = Color(0xFF472333),
    onPrimaryContainer = Color(0xFFFFDCE6),
    secondary = Color(0xFFE9B18E),
    onSecondary = Color(0xFF24150E),
    tertiary = Color(0xFFB7CDBB),
    background = Color(0xFF0D0B10),
    onBackground = Color(0xFFF8F2F6),
    surface = Color(0xFF19151D),
    onSurface = Color(0xFFF8F2F6),
    surfaceVariant = Color(0xFF29232E),
    onSurfaceVariant = Color(0xFFB9AFBC),
    outline = Color(0xFF514653),
    error = Color(0xFFFF7D86)
)

private val LovelyType = Typography(
    displayLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=52.sp,lineHeight=56.sp,letterSpacing=(-1.8).sp),
    headlineLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=31.sp,lineHeight=37.sp,letterSpacing=(-.8).sp),
    headlineMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=26.sp,lineHeight=32.sp,letterSpacing=(-.5).sp),
    titleLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=20.sp,lineHeight=26.sp),
    titleMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
    bodyLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=15.sp,lineHeight=23.sp),
    bodyMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=13.sp,lineHeight=20.sp),
    bodySmall = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=11.sp,lineHeight=16.sp),
    labelLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=13.sp,letterSpacing=.2.sp)
)

@Composable
fun LovelyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme=LovelyColors,
        typography=LovelyType,
        shapes=Shapes(
            extraSmall=RoundedCornerShape(10.dp),
            small=RoundedCornerShape(14.dp),
            medium=RoundedCornerShape(20.dp),
            large=RoundedCornerShape(28.dp),
            extraLarge=RoundedCornerShape(34.dp)
        ),
        content=content
    )
}
