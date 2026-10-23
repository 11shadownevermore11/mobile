@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.kodnovorossii.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import ru.kodnovorossii.app.R
import ru.kodnovorossii.app.data.Logic
import ru.kodnovorossii.app.data.PlaceDto
import ru.kodnovorossii.app.ui.theme.Brand
import ru.kodnovorossii.app.ui.theme.LocalDark
import kotlin.math.sin

fun hex(s: String?, fallback: Color = Brand.Blue): Color =
    runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(fallback)

/** Иконки вертикалей и категорий задаются строкой в админке — сопоставляем с Material Icons. */
fun iconFor(name: String?): ImageVector = when (name) {
    "landmark" -> Icons.Filled.Museum
    "palette" -> Icons.Filled.Palette
    "factory" -> Icons.Filled.Engineering
    "briefcase" -> Icons.Filled.BusinessCenter
    "anchor" -> Icons.Filled.DirectionsBoat
    "camera" -> Icons.Filled.CameraAlt
    "trees" -> Icons.Filled.Park
    "drama" -> Icons.Filled.TheaterComedy
    "utensils" -> Icons.Filled.Restaurant
    "coffee" -> Icons.Filled.LocalCafe
    "bed" -> Icons.Filled.Hotel
    "telescope" -> Icons.Filled.Science
    "blocks" -> Icons.Filled.Widgets
    "music" -> Icons.Filled.MusicNote
    "clapperboard" -> Icons.Filled.Movie
    "gamepad-2" -> Icons.Filled.SportsEsports
    "book-open" -> Icons.Filled.MenuBook
    "building-2" -> Icons.Filled.Apartment
    "megaphone" -> Icons.Filled.Campaign
    "shirt" -> Icons.Filled.Checkroom
    "pen-tool" -> Icons.Filled.Brush
    "shopping-bag" -> Icons.Filled.ShoppingBag
    "hard-hat" -> Icons.Filled.Build
    "handshake" -> Icons.Filled.People
    "ship" -> Icons.Filled.DirectionsBoat
    "waves" -> Icons.Filled.Waves
    "bike" -> Icons.Filled.PedalBike
    "mountain" -> Icons.Filled.Terrain
    "star" -> Icons.Filled.Star
    "trophy" -> Icons.Filled.EmojiEvents
    "siren" -> Icons.Filled.LocalHospital
    "shield" -> Icons.Filled.Shield
    "bus" -> Icons.Filled.DirectionsBus
    "wallet" -> Icons.Filled.AccountBalanceWallet
    "droplets" -> Icons.Filled.Opacity
    "info" -> Icons.Filled.Info
    "church" -> Icons.Filled.AccountBalance
    "leaf" -> Icons.Filled.Spa
    "graduation-cap" -> Icons.Filled.School
    else -> Icons.Filled.Place
}

fun fmtValue(v: Any?): String = when (v) {
    null -> ""
    is Boolean -> if (v) "Да" else "Нет"
    is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    else -> v.toString()
}

@Composable
fun Logo(size: Dp = 32.dp) {
    val dark = LocalDark.current
    Image(painterResource(if (dark) R.drawable.mark_white else R.drawable.mark_black), contentDescription = "ФСП ДНР", modifier = Modifier.size(size))
}

/** Генеративная обложка: цвет вертикали + узор «узлы и связи» (как в знаке) + иконка категории. Работает офлайн. */
@Composable
fun Cover(color: Color, icon: String?, seed: Int, modifier: Modifier = Modifier, iconSize: Dp = 40.dp, photo: String? = null) {
    Box(
        modifier.background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f).compositeOver(Color(0xFF0C1120))))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun rnd(i: Int): Float { val x = sin(seed * 97.3 + i * 13.7) * 10000; return (x - kotlin.math.floor(x)).toFloat() }
            val pts = (0 until 6).map { Offset(rnd(it) * size.width, rnd(it + 20) * size.height) }
            pts.zipWithNext().forEach { (a, b) -> drawLine(Color.White.copy(alpha = 0.35f), a, b, strokeWidth = 2f) }
            pts.forEachIndexed { i, p -> drawCircle(Color.White.copy(alpha = 0.4f), radius = if (i % 3 == 0) 9f else 6f, center = p) }
        }
        Icon(iconFor(icon), null, tint = Color.White, modifier = Modifier.size(iconSize))
        if (!photo.isNullOrBlank()) {
            // Фото встроено в приложение → берём из assets (работает без сети); иначе — с сервера (кэшируется Coil)
            AsyncImage(model = photoUrl(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Фото, встроенные в приложение скриптом mobile/make_offline_bundle.py (assets/site). */
object EmbeddedPhotos {
    @Volatile private var index: Set<String>? = null
    fun has(ctx: android.content.Context, path: String): Boolean {
        val idx = index ?: runCatching { ctx.assets.open("site/index.txt").bufferedReader().readLines().toSet() }.getOrDefault(emptySet()).also { index = it }
        return path in idx
    }
}

@Composable
fun photoUrl(photo: String): String {
    if (photo.startsWith("http")) return photo
    val path = photo.substringBefore("?")
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return if (EmbeddedPhotos.has(ctx, path)) "file:///android_asset/site$path" else ru.kodnovorossii.app.data.ApiConfig.url(photo)
}

/** Шапка экрана с фотографией города, затемнением и заголовком — как первый экран сайта. */
@Composable
fun PhotoHeader(title: String, subtitle: String, photo: String = "/img/hero-main.jpg", height: Dp = 190.dp, content: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(height)) {
        AsyncImage(model = photoUrl(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99040A16), Color(0x33040A16), MaterialTheme.colorScheme.background))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            content()
        }
    }
}

/** Большая карточка места: фото сверху, рейтинг и избранное поверх фото, текст под фото. */
@Composable
fun PlaceCard(p: PlaceDto, isFav: Boolean, distance: Double?, onFav: () -> Unit, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(170.dp)) {
                Cover(hex(p.vertical?.color), p.category?.icon, p.id, Modifier.fillMaxSize(), iconSize = 44.dp, photo = p.photos?.firstOrNull())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent, Color(0x66000000)))))
                Row(Modifier.align(Alignment.TopStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(hex(p.vertical?.color)), contentAlignment = Alignment.Center) {
                        Icon(iconFor(p.category?.icon), null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    HGap(6.dp)
                    Text(p.category?.name.orEmpty(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(40.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onFav), contentAlignment = Alignment.Center) {
                    Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Избранное", tint = if (isFav) Color(0xFFFF4B5E) else Color.White)
                }
                if (p.rating > 0) Row(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    Text(String.format("%.1f", p.rating), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!p.short.isNullOrBlank()) Text(p.short!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpenStatus(p)
                    distance?.let { Text(Logic.fmtDist(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

@Composable
fun Rating(value: Double, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = Brand.Star, modifier = Modifier.size(15.dp))
        Text(if (value > 0) String.format("%.1f", value) else "—", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 2.dp))
        if (count != null) Text(" ($count)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OpenStatus(p: PlaceDto) {
    val (text, color) = when (p.status) {
        "temporarily_unavailable" -> "Временно недоступно" to Brand.Warn
        "closed" -> "Закрыто" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> if (p.hours.isNullOrEmpty()) ("График уточняйте" to MaterialTheme.colorScheme.onSurfaceVariant) else if (Logic.isOpenNow(p)) ("Открыто · ${Logic.todayHours(p)}" to Brand.Ok) else ("Закрыто · ${Logic.todayHours(p)}" to MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, bg: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun PlaceRow(p: PlaceDto, isFav: Boolean, distance: Double?, onFav: () -> Unit, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(hex(p.vertical?.color), p.category?.icon, p.id, Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)), iconSize = 28.dp, photo = p.photos?.firstOrNull())
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(p.category?.name, distance?.let { Logic.fmtDist(it) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Rating(p.rating); OpenStatus(p) }
                if (p.made_in_region) Tag("Сделано в регионе", Color.White, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f))
            }
            IconButton(onClick = onFav) {
                Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Избранное", tint = if (isFav) Brand.Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = modifier.padding(top = 18.dp, bottom = 10.dp))
}

@Composable
fun Empty(icon: ImageVector, title: String, text: String = "") {
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 1.dp, modifier = if (onClick != null) m.clickable(onClick = onClick) else m) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun VNode(color: Color, icon: String?, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(iconFor(icon), null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun QrImage(text: String, size: Dp = 200.dp) {
    val bmp = remember(text) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val b = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) for (y in 0 until 512) b.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        b.asImageBitmap()
    }
    Image(bmp, "QR-код", modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)))
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp = 8.dp) = Spacer(Modifier.width(w))
