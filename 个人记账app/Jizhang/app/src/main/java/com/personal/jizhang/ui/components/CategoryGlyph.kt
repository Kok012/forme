package com.personal.jizhang.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.personal.jizhang.model.Category

fun CategoryIcon(key: String): ImageVector = when (key) {
    "food" -> Icons.Outlined.Restaurant
    "transport" -> Icons.Outlined.DirectionsBus
    "shopping" -> Icons.Outlined.ShoppingBag
    "home" -> Icons.Outlined.Home
    "daily" -> Icons.Outlined.CleaningServices
    "fun" -> Icons.Outlined.SportsEsports
    "health" -> Icons.Outlined.LocalHospital
    "study" -> Icons.AutoMirrored.Outlined.MenuBook
    "phone" -> Icons.Outlined.PhoneAndroid
    "gift" -> Icons.Outlined.CardGiftcard
    "travel" -> Icons.Outlined.Flight
    else -> Icons.Outlined.MoreHoriz
}

fun categoryColor(key: String): Color = when (key) {
    "blue" -> Color(0xFF487BB2)
    "orange" -> Color(0xFFB97935)
    "purple" -> Color(0xFF8971B4)
    "pink" -> Color(0xFFB96C89)
    "yellow" -> Color(0xFF9B8426)
    else -> Color(0xFF147D73)
}

@Composable
fun CategoryGlyph(category: Category, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val color = if (dark) lerp(categoryColor(category.colorKey), Color.White, .35f) else categoryColor(category.colorKey)
    Surface(modifier = modifier.size(44.dp), color = color.copy(alpha = if (dark) .15f else .10f), shape = RoundedCornerShape(15.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(CategoryIcon(category.iconKey), contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
        }
    }
}
