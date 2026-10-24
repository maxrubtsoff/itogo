package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Stable key → Material icon mapping for cashback categories. The domain stores
 * only the key string (CategoryGroup.iconKey); this layer turns it into a vector,
 * so the domain stays Compose-free. [iconPalette] is what the user chooses from
 * when creating a custom category.
 */
object CategoryIcons {

    private val byKey: Map<String, ImageVector> = mapOf(
        // Preset keys (must cover every key in CategoryGroups).
        "base" to Icons.Filled.Percent,
        "groceries" to Icons.Filled.LocalGroceryStore,
        "restaurant" to Icons.Filled.Restaurant,
        "cafe" to Icons.Filled.LocalCafe,
        "fuel" to Icons.Filled.LocalGasStation,
        "transport" to Icons.Filled.DirectionsBus,
        "taxi" to Icons.Filled.LocalTaxi,
        "pharmacy" to Icons.Filled.LocalPharmacy,
        "health" to Icons.Filled.MedicalServices,
        "beauty" to Icons.Filled.Spa,
        "clothing" to Icons.Filled.Checkroom,
        "electronics" to Icons.Filled.Devices,
        "home" to Icons.Filled.Home,
        "online" to Icons.Filled.ShoppingBag,
        "entertainment" to Icons.Filled.Movie,
        "art" to Icons.Filled.Museum,
        "travel" to Icons.Filled.Flight,
        "auto" to Icons.Filled.CarRepair,
        "mobile" to Icons.Filled.Wifi,
        "kids" to Icons.Filled.ChildCare,
        "pets" to Icons.Filled.Pets,
        "sport" to Icons.Filled.FitnessCenter,
        "all" to Icons.Filled.Payments,
        "other" to Icons.Filled.Category,
        // Extra keys available only via the custom-category picker.
        "car" to Icons.Filled.DirectionsCar,
        "fastfood" to Icons.Filled.Fastfood,
        "bar" to Icons.Filled.LocalBar,
        "music" to Icons.Filled.MusicNote,
        "games" to Icons.Filled.SportsEsports,
        "flowers" to Icons.Filled.LocalFlorist,
        "cake" to Icons.Filled.Cake,
        "repair" to Icons.Filled.Build,
        "work" to Icons.Filled.Work,
        "school" to Icons.Filled.School,
        "gift" to Icons.Filled.CardGiftcard,
        "card" to Icons.Filled.CreditCard,
        "savings" to Icons.Filled.Savings,
        "star" to Icons.Filled.Star
    )

    /** Order shown in the custom-category icon picker. */
    val iconPalette: List<String> = listOf(
        "groceries", "restaurant", "cafe", "fastfood", "bar", "fuel", "car",
        "transport", "taxi", "travel", "pharmacy", "health", "beauty",
        "clothing", "electronics", "home", "repair", "online", "entertainment", "art", "auto",
        "games", "music", "mobile", "kids", "pets", "sport", "flowers", "cake",
        "gift", "card", "savings", "work", "school", "star", "all", "other"
    ).distinct().filter { byKey.containsKey(it) }

    fun vector(iconKey: String): ImageVector =
        byKey[iconKey] ?: byKey.getValue(CategoryGroups.OTHER)
}

/** A category glyph in a soft inset coin, matching the Слиток tokens. */
@Composable
fun CategoryIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    tint: Color = Slitok.Brass2,
    background: Color = Slitok.Inset
) {
    val shape = CircleShape
    Box(
        modifier
            .size(size)
            .background(background, shape)
            .border(1.dp, Slitok.Line, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = CategoryIcons.vector(iconKey),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
