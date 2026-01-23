package com.example.myapplication

import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.DpOffset
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.text.DecimalFormat
import kotlin.math.min

// ==========================================
// NETWORK & DATA LAYER
// ==========================================

data class AnimeUpdate(
    val animeId: String,
    val title: String,
    val currentEpisodes: Int,
    val newEpisodes: Int,
    val source: String
)

object ApiRateLimiter {
    private val mutex = Mutex()
    suspend fun <T> executeSafe(block: suspend () -> T): T {
        mutex.withLock {
            delay(1200)
            return block()
        }
    }
}

class AnimeRepository {
    private val tmdbKey = "4f4dc3cd35d58a551162eefe92ff549c"

    suspend fun findTotalEpisodes(title: String): Pair<Int, String>? = withContext(Dispatchers.IO) {
        val shikiResult = checkShikimori(title)
        if (shikiResult != null && shikiResult > 0) return@withContext shikiResult to "Shikimori"

        val jikanResult = checkJikan(title)
        if (jikanResult != null && jikanResult > 0) return@withContext jikanResult to "Jikan"

        val tmdbResult = checkTmdb(title)
        if (tmdbResult != null && tmdbResult > 0) return@withContext tmdbResult to "TMDB"

        return@withContext null
    }

    private suspend fun checkShikimori(query: String): Int? {
        return try {
            ApiRateLimiter.executeSafe {
                val searchUrl = "https://shikimori.one/api/animes?search=${enc(query)}&limit=1"
                val json = getJson(searchUrl).asJsonArray
                if (json.size() == 0) return@executeSafe null
                val id = json[0].asJsonObject.get("id").asInt
                val franchiseUrl = "https://shikimori.one/api/animes/$id/franchise"
                val franchiseJson = getJson(franchiseUrl).asJsonObject
                json[0].asJsonObject.get("episodes").let { if(it.isJsonNull) 0 else it.asInt }
            }
        } catch (e: Exception) { null }
    }

    private suspend fun checkJikan(query: String): Int? {
        return try {
            ApiRateLimiter.executeSafe {
                val searchUrl = "https://api.jikan.moe/v4/anime?q=${enc(query)}&limit=1"
                val resp = getJson(searchUrl).asJsonObject
                val data = resp.getAsJsonArray("data")
                if (data == null || data.size() == 0) return@executeSafe null
                val anime = data[0].asJsonObject
                if (anime.get("episodes").isJsonNull) 0 else anime.get("episodes").asInt
            }
        } catch (e: Exception) { null }
    }

    private suspend fun checkTmdb(query: String): Int? {
        return try {
            ApiRateLimiter.executeSafe {
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$tmdbKey&query=${enc(query)}"
                val results = getJson(searchUrl).asJsonObject.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val id = results[0].asJsonObject.get("id").asInt
                    val detailsUrl = "https://api.themoviedb.org/3/tv/$id?api_key=$tmdbKey"
                    val details = getJson(detailsUrl).asJsonObject
                    val seasons = details.getAsJsonArray("seasons")
                    var total = 0
                    seasons.forEach { s ->
                        val seasonObj = s.asJsonObject
                        val sNum = seasonObj.get("season_number").asInt
                        if (sNum > 0) total += seasonObj.get("episode_count").asInt
                    }
                    return@executeSafe total
                }
                null
            }
        } catch (e: Exception) { null }
    }

    private fun getJson(urlString: String): com.google.gson.JsonElement {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (conn.responseCode in 200..299) {
            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            return JsonParser.parseString(jsonStr)
        } else {
            throw Exception("HTTP ${conn.responseCode}")
        }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

// ==========================================
// THEME & COLORS (UPDATED)
// ==========================================

// Фон всего приложения (глубокий темный)
val AppBackground = Color(0xFF121214)

// Градиент карточки
val CardGradientStart = Color(0xFF252529)
val CardGradientEnd = Color(0xFF18181A)

// Псевдоним для совместимости (FIXED ERROR)
val OneUiCardBg = CardGradientStart

// Другие цвета
val OneUiBlue = Color(0xFF3E82F7)
val OneUiBluePastel = Color(0xFF90CAF9)
val OneUiRed = Color(0xFFFF3B30)

// Текст
val TextPrimary = Color(0xFFF0F0F2)
val TextSecondary = Color(0xFF8E8E93)

// Рейтинг
val StarColor = Color(0xFFFF8C00) // Оранжевый
val RatingChipBg = Color(0xFF000000).copy(alpha = 0.4f)

// Цвета для StarRatingBar (оставим для совместимости)
val RateColor1 = Color(0xFFD32F2F)
val RateColor2 = Color(0xFFE64A19)
val RateColor3 = Color(0xFFF57C00)
val RateColor4 = Color(0xFFFBC02D)
val RateColor5 = Color(0xFF388E3C)
val RateColorEmpty = Color(0xFF424242)

// Кисть для "света" по контуру (кант)
val BorderGradient = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.15f),
        Color.White.copy(alpha = 0.02f),
        Color.Transparent
    ),
    start = Offset(0f, 0f),
    end = Offset(300f, 300f)
)

@Composable
fun OneUiTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = AppBackground,
        surface = CardGradientEnd,
        primary = OneUiBlue,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        secondary = TextSecondary,
        error = OneUiRed
    )

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            titleMedium = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                letterSpacing = 0.5.sp
            ),
            bodyMedium = TextStyle(
                fontSize = 13.sp,
                color = TextSecondary
            )
        ),
        content = content
    )
}

// ==========================================
// DATA & VIEWMODEL
// ==========================================

data class Anime(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val episodes: Int,
    val rating: Int,
    val imageFileName: String?,
    val orderIndex: Int,
    val dateAdded: Long = System.currentTimeMillis()
)

class AnimeViewModel : ViewModel() {
    private val _animeList = mutableStateListOf<Anime>()
    val animeList: List<Anime> get() = _animeList
    var searchQuery by mutableStateOf("")
    var sortOption by mutableStateOf(SortOption.DATE_NEWEST)

    private val _updates = mutableStateListOf<AnimeUpdate>()
    val updates: List<AnimeUpdate> get() = _updates
    var isCheckingUpdates by mutableStateOf(false)
    var needsUpdateCheck by mutableStateOf(true)

    private val repository = AnimeRepository()
    private val ROOT = "MyAnimeList"
    private val IMG_DIR = "collection"
    private val FILE_NAME = "list.json"
    private val IGNORED_FILE_NAME = "ignored.json"

    private var ignoredUpdatesMap = mutableMapOf<String, Int>()

    private fun getRoot(): File {
        val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val f = File(d, ROOT); if (!f.exists()) f.mkdirs(); return f
    }
    private fun getImgDir(): File { val f = File(getRoot(), IMG_DIR); if(!f.exists()) f.mkdirs(); return f }
    private fun getDataFile(): File = File(getRoot(), FILE_NAME)
    private fun getIgnoredFile(): File = File(getRoot(), IGNORED_FILE_NAME)

    fun loadAnime() {
        val f = getDataFile()
        if (f.exists()) {
            try {
                val json = f.readText()
                val list: List<Anime> = Gson().fromJson(json, object : TypeToken<List<Anime>>() {}.type) ?: emptyList()
                _animeList.clear(); _animeList.addAll(list.sortedBy { it.orderIndex })
            } catch (e: Exception) { e.printStackTrace() }
        }

        val fIgnored = getIgnoredFile()
        if (fIgnored.exists()) {
            try {
                val json = fIgnored.readText()
                val map: Map<String, Int> = Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
                ignoredUpdatesMap.putAll(map)
            } catch (e: Exception) { e.printStackTrace() }
        }

        needsUpdateCheck = true
        checkForUpdates()
    }

    fun checkForUpdates(force: Boolean = false) {
        if (!force && !needsUpdateCheck && _updates.isEmpty()) return
        if (isCheckingUpdates) return
        isCheckingUpdates = true

        viewModelScope.launch {
            try {
                val newUpdates = mutableListOf<AnimeUpdate>()
                val currentList = _animeList.toList()

                currentList.forEach { anime ->
                    val result = repository.findTotalEpisodes(anime.title)
                    if (result != null) {
                        val (remoteEps, source) = result
                        val isIgnored = ignoredUpdatesMap[anime.id] == remoteEps
                        if (remoteEps > anime.episodes && !isIgnored) {
                            newUpdates.add(AnimeUpdate(anime.id, anime.title, anime.episodes, remoteEps, source))
                        }
                    }
                }
                _updates.clear()
                _updates.addAll(newUpdates)
                if (_updates.isEmpty()) needsUpdateCheck = false
            } catch (e: Exception) { e.printStackTrace() }
            finally { isCheckingUpdates = false }
        }
    }

    fun acceptUpdate(update: AnimeUpdate, ctx: Context) {
        val anime = getAnimeById(update.animeId) ?: return
        updateAnime(ctx, anime.id, anime.title, update.newEpisodes, anime.rating, null)
        _updates.remove(update)
    }

    fun dismissUpdate(update: AnimeUpdate) {
        ignoredUpdatesMap[update.animeId] = update.newEpisodes
        saveIgnored()
        _updates.remove(update)
        if (_updates.isEmpty()) needsUpdateCheck = false
    }

    fun getAnimeById(id: String): Anime? = _animeList.find { it.id == id }

    fun addAnime(ctx: Context, title: String, ep: Int, rate: Int, uri: Uri?) {
        val id = UUID.randomUUID().toString()
        val img = if (uri != null) saveImg(ctx, uri, id) else null
        _animeList.add(0, Anime(id, title, ep, rate, img, (_animeList.maxOfOrNull { it.orderIndex }?:0)+1))
        save()
        needsUpdateCheck = true
    }

    fun updateAnime(ctx: Context, id: String, title: String, ep: Int, rate: Int, uri: Uri?) {
        val idx = _animeList.indexOfFirst { it.id == id }
        if (idx == -1) return
        var img = _animeList[idx].imageFileName
        if (uri != null) {
            val newImg = saveImg(ctx, uri, id)
            if (newImg != null) {
                _animeList[idx].imageFileName?.let { File(getImgDir(), it).delete() }
                img = newImg
            }
        }
        _animeList[idx] = _animeList[idx].copy(title=title, episodes=ep, rating=rate, imageFileName=img)
        save()
        needsUpdateCheck = true
        if (ignoredUpdatesMap.containsKey(id)) {
            ignoredUpdatesMap.remove(id)
            saveIgnored()
        }
    }

    fun deleteAnime(id: String) {
        val anime = _animeList.find { it.id == id } ?: return
        anime.imageFileName?.let { File(getImgDir(), it).delete() }
        _animeList.remove(anime)
        save()
        needsUpdateCheck = true
    }

    private fun save() { try { getDataFile().writeText(Gson().toJson(_animeList)) } catch(e:Exception){e.printStackTrace()} }
    private fun saveIgnored() { try { getIgnoredFile().writeText(Gson().toJson(ignoredUpdatesMap)) } catch(e:Exception){e.printStackTrace()} }

    private fun saveImg(ctx: Context, uri: Uri, id: String): String? {
        return try {
            val name = "img_${id}_${System.currentTimeMillis()}.jpg"
            ctx.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(File(getImgDir(), name)).use { o -> i.copyTo(o) } }
            name
        } catch(e: Exception) { null }
    }

    fun getImgPath(name: String?): File? = if(name!=null) File(getImgDir(), name).let { if(it.exists()) it else null } else null

    fun getDisplayList(): List<Anime> {
        val rawQuery = searchQuery.trim()
        if (rawQuery.isBlank()) return sortList(_animeList)
        val queryTokens = tokenize(rawQuery)
        val filtered = _animeList.filter { anime ->
            val titleTokens = tokenize(anime.title)
            queryTokens.all { qToken -> titleTokens.any { tToken -> isTokenMatch(qToken, tToken) } }
        }
        return sortList(filtered)
    }

    private fun sortList(list: List<Anime>): List<Anime> {
        return when(sortOption) {
            SortOption.DATE_NEWEST -> list.sortedByDescending { it.dateAdded }
            SortOption.RATING_HIGH -> list.sortedByDescending { it.rating }
            SortOption.AZ -> list.sortedBy { it.title }
        }
    }

    private fun tokenize(text: String): List<String> = text.lowercase().replace(Regex("[^a-zа-я0-9]"), " ").split(" ").filter { it.isNotBlank() }

    private fun isTokenMatch(query: String, target: String): Boolean {
        if (target.contains(query)) return true
        val qStem = getStem(query)
        val tStem = getStem(target)
        if (tStem.startsWith(qStem)) return true
        val maxDist = if (query.length > 6) 2 else if (query.length > 3) 1 else 0
        if (levenshtein(query, target) <= maxDist) return true
        if (query.length > 3 && levenshtein(qStem, tStem) <= maxDist) return true
        return false
    }

    private fun getStem(word: String): String {
        var res = word
        val endings = listOf("ями", "ами", "ов", "ев", "ей", "ий", "ой", "ый", "ая", "ое", "ые", "ие", "ом", "ем", "ам", "ах", "ях", "у", "ю", "а", "я", "о", "е", "ы", "и", "ь")
        for (end in endings) {
            if (res.length > end.length + 2 && res.endsWith(end)) return res.substring(0, res.length - end.length)
        }
        return res
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[lhsLength]
    }
}

enum class SortOption(val label: String) {
    DATE_NEWEST("Newest First"),
    RATING_HIGH("Highest Rated"),
    AZ("Name (A-Z)")
}

// ==========================================
// UI COMPONENTS
// ==========================================

fun performHaptic(view: android.view.View, isStrong: Boolean = false) {
    if (isStrong) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

val StarIconVector: ImageVector
    get() = ImageVector.Builder(name = "Star", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null, strokeAlpha = 1f, pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero) {
        moveTo(8.243f, 7.34f); lineTo(1.863f, 8.265f); lineTo(1.75f, 8.288f); arcTo(1f, 1f, 0f, false, false, 1.31f, 9.972f); lineTo(5.932f, 14.471f); lineTo(4.842f, 20.826f); lineTo(4.829f, 20.936f); arcTo(1f, 1f, 0f, false, false, 6.293f, 21.88f); lineTo(12.0f, 18.88f); lineTo(17.693f, 21.88f); lineTo(17.793f, 21.926f); arcTo(1f, 1f, 0f, false, false, 19.145f, 20.826f); lineTo(18.054f, 14.471f); lineTo(22.678f, 9.971f); lineTo(22.756f, 9.886f); arcTo(1f, 1f, 0f, false, false, 22.123f, 8.266f); lineTo(15.743f, 7.34f); lineTo(12.891f, 1.56f); arcTo(1f, 1f, 0f, false, false, 11.097f, 1.56f); lineTo(8.243f, 7.34f); close()
    }.build()

@Composable
fun StarRatingBar(rating: Int, onRatingChanged: (Int) -> Unit) {
    val colors = listOf(RateColor1, RateColor2, RateColor3, RateColor4, RateColor5)
    val activeColor = if (rating > 0) colors[rating - 1] else RateColorEmpty

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val isSelected = i <= rating
            val animatedColor by animateColorAsState(targetValue = if (isSelected) activeColor else RateColorEmpty, animationSpec = tween(300), label = "color")
            val scale by animateFloatAsState(targetValue = if (isSelected) 1.2f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")
            Icon(imageVector = StarIconVector, contentDescription = "Rate $i", tint = animatedColor, modifier = Modifier.size(54.dp).scale(scale).padding(4.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onRatingChanged(i) })
        }
    }
}

@Composable
fun EpisodeSuggestions(onSelect: (String) -> Unit) {
    val suggestions = listOf("12", "13", "24", "36", "48", "100")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        suggestions.forEach { num ->
            Box(modifier = Modifier.weight(1f).clip(CircleShape).background(CardGradientEnd).border(1.dp, TextSecondary, CircleShape).clickable { onSelect(num) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(text = num, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AnimatedSaveFab(isEnabled: Boolean, onClick: () -> Unit) {
    var isSaved by remember { mutableStateOf(false) }
    FloatingActionButton(
        onClick = { if (isEnabled && !isSaved) { isSaved = true; onClick() } },
        containerColor = if (isEnabled) OneUiBlue else Color(0xFF424242),
        contentColor = if (isEnabled) Color.White else Color.Gray,
        shape = CircleShape,
        modifier = Modifier.size(64.dp)
    ) {
        AnimatedContent(targetState = isSaved, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "save_animation") { saved ->
            if (saved) Icon(imageVector = Icons.Default.Check, contentDescription = "Saved", modifier = Modifier.size(28.dp))
            else Icon(imageVector = Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun AnimatedCopyButton(textToCopy: String) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    val view = LocalView.current

    Box(contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isCopied,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp).zIndex(10f)
        ) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Copied!", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        IconButton(onClick = {
            if (textToCopy.isNotEmpty()) {
                performHaptic(view, false)
                clipboardManager.setText(AnnotatedString(textToCopy))
                isCopied = true
            }
        }) {
            AnimatedContent(
                targetState = isCopied,
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "copy_icon"
            ) { copied ->
                if (copied) {
                    LaunchedEffect(Unit) { delay(2000); isCopied = false }
                    Icon(Icons.Default.Check, null, tint = OneUiBlue, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun OneUiTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, maxLines: Int = 1) {
    BasicTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(20.dp)).background(CardGradientStart).padding(horizontal = 24.dp, vertical = 16.dp),
        textStyle = TextStyle(fontSize = 18.sp, color = Color.White), singleLine = singleLine, maxLines = maxLines, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, color = Color.Gray, fontSize = 18.sp); innerTextField() } }
    )
}

@Composable
fun RatingChip(rating: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RatingChipBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = StarColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = rating.toString(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun OneUiAnimeCard(
    anime: Anime,
    viewModel: AnimeViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val imageFile = remember(anime.imageFileName) { viewModel.getImgPath(anime.imageFileName) }
    val view = LocalView.current
    val cardShape = RoundedCornerShape(24.dp)

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .sharedBounds(
                    rememberSharedContentState(key = "container_${anime.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize
                )
                .shadow(elevation = 12.dp, shape = cardShape, spotColor = Color.Black.copy(alpha = 0.6f), ambientColor = Color.Black.copy(alpha = 0.3f))
                .background(brush = Brush.verticalGradient(colors = listOf(CardGradientStart, CardGradientEnd)), shape = cardShape)
                .border(width = 1.dp, brush = BorderGradient, shape = cardShape)
                .clip(cardShape)
                .combinedClickable(onClick = { performHaptic(view, false); onClick() }, onLongClick = { performHaptic(view, true); onLongClick() })
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.aspectRatio(1f).fillMaxHeight()
                        .sharedElement(rememberSharedContentState(key = "image_${anime.id}"), animatedVisibilityScope = animatedVisibilityScope)
                        .clip(RoundedCornerShape(18.dp)).background(Color.Black)
                ) {
                    if (imageFile != null) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageFile).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C2E))) {
                            Text(text = anime.title.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
                        Text(text = anime.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextPrimary, lineHeight = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(text = "${anime.episodes} episodes", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    if (anime.rating > 0) RatingChip(rating = anime.rating)
                }
            }
        }
    }
}

@Composable
fun SimpleAnimePreviewOverlay(anime: Anime, viewModel: AnimeViewModel, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val imageFile = remember(anime.imageFileName) { viewModel.getImgPath(anime.imageFileName) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val view = LocalView.current
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!showConfirmDialog) { performHaptic(view, false); onDismiss() } }.zIndex(50f), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = !showConfirmDialog, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(0.7f).clip(RoundedCornerShape(32.dp)).background(CardGradientEnd)) {
                    if (imageFile != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageFile).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(anime.title.take(1), fontSize = 60.sp, color = Color.Gray) }
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 300f)))
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(text = anime.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = OneUiBlue.copy(alpha = 0.8f), shape = CircleShape) { Text(text = "${anime.episodes} EP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                            Spacer(Modifier.width(12.dp))
                            if (anime.rating > 0) RatingChip(rating = anime.rating)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                AnimatedTrashButton(onClick = { performHaptic(view, true); showConfirmDialog = true })
            }
        }
        if (showConfirmDialog) DeleteConfirmationDialog(onConfirm = { performHaptic(view, true); onDelete() }, onCancel = { performHaptic(view, false); showConfirmDialog = false })
    }
}

@Composable
fun AnimatedTrashButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.8f else 1f, label = "scale")
    Box(modifier = Modifier.size(70.dp).scale(scale).shadow(12.dp, CircleShape, spotColor = OneUiRed).clip(CircleShape).background(OneUiRed).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isPressed = true; onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = onCancel) {
        AnimatedVisibility(visible = visible, enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(modifier = Modifier.clip(RoundedCornerShape(32.dp)).background(CardGradientStart).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)).padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(OneUiRed.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.DeleteForever, null, tint = OneUiRed, modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.height(16.dp))
                    Text("Delete title?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("This action cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { onCancel() }, contentAlignment = Alignment.Center) { Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(OneUiRed).clickable { onConfirm() }, contentAlignment = Alignment.Center) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREENS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalistTopBar(currentSort: SortOption, onSortSelected: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).statusBarsPadding().padding(top = 12.dp), shape = CircleShape, color = CardGradientStart.copy(alpha = 0.95f), shadowElevation = 8.dp) {
        CenterAlignedTopAppBar(
            title = { Text("MAList", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White) },
            actions = {
                Box {
                    IconButton(onClick = { expanded = true }) { Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort", tint = Color.White) }
                    MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))) {
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = CardGradientEnd.copy(alpha = 0.9f), offset = DpOffset(x = 12.dp, y = 8.dp)) {
                            SortOption.values().forEach { option ->
                                val isSelected = currentSort == option
                                DropdownMenuItem(text = { Text(text = option.label, color = if (isSelected) OneUiBluePastel else TextPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp) }, trailingIcon = { val icon = when (option) { SortOption.DATE_NEWEST -> Icons.Default.DateRange; SortOption.RATING_HIGH -> Icons.Default.Star; SortOption.AZ -> Icons.AutoMirrored.Filled.Sort }; Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) OneUiBluePastel else TextSecondary) }, onClick = { onSortSelected(option); expanded = false }, colors = MenuDefaults.itemColors(textColor = TextPrimary, trailingIconColor = TextSecondary))
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun StatsCard(title: String, icon: ImageVector, isExpanded: Boolean, onClick: () -> Unit, rankColor: Color? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onClick() }.animateContentSize(), colors = CardDefaults.cardColors(containerColor = CardGradientStart)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = icon, contentDescription = null, tint = OneUiBluePastel, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold) }
            if (isExpanded) { Spacer(modifier = Modifier.height(16.dp)); Box(modifier = Modifier.fillMaxWidth()) { content() } }
        }
    }
}

@Composable
fun WatchStatsContent(animeList: List<Anime>) {
    val totalEpisodes = remember(animeList) { animeList.sumOf { it.episodes } }
    val totalMinutes = totalEpisodes * 22L
    val formatter = DecimalFormat("#,###")
    val formattedMinutes = formatter.format(totalMinutes).replace(",", " ")
    val formattedHours = formatter.format(totalMinutes / 60).replace(",", " ")
    val formattedDays = formatter.format(totalMinutes / (60 * 24)).replace(",", " ")
    val avgRating = remember(animeList) { if (animeList.isEmpty()) 0.0 else animeList.map { it.rating }.average() }
    val (rankName, rankColor) = remember(totalEpisodes) { when { totalEpisodes >= 1200 -> "Legend" to RateColor5; totalEpisodes >= 800 -> "Veteran" to RateColor4; totalEpisodes >= 500 -> "Dedicated" to RateColor3; totalEpisodes >= 300 -> "Casual" to RateColor2; else -> "Rookie" to RateColor1 } }
    var expandedIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Watch Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary); Spacer(modifier = Modifier.height(4.dp)); Text("Everything you’ve watched so far", style = MaterialTheme.typography.bodyMedium, color = TextSecondary); Spacer(modifier = Modifier.height(24.dp))
        StatsCard(title = "Watched episodes", icon = Icons.Default.Visibility, isExpanded = expandedIndex == 0, onClick = { expandedIndex = if (expandedIndex == 0) -1 else 0 }) { Text(text = "$totalEpisodes Episodes watched", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary) }
        Spacer(modifier = Modifier.height(12.dp))
        StatsCard(title = "Time spent watching", icon = Icons.Default.Schedule, isExpanded = expandedIndex == 1, onClick = { expandedIndex = if (expandedIndex == 1) -1 else 1 }) { Column { Text(text = "$formattedMinutes min", style = MaterialTheme.typography.titleLarge, color = TextPrimary); Text(text = "$formattedHours h", style = MaterialTheme.typography.titleLarge, color = TextSecondary); Text(text = "$formattedDays days", style = MaterialTheme.typography.titleLarge, color = OneUiBluePastel) } }
        Spacer(modifier = Modifier.height(12.dp))
        StatsCard(title = "Average rating", icon = Icons.Default.Star, isExpanded = expandedIndex == 2, onClick = { expandedIndex = if (expandedIndex == 2) -1 else 2 }) { Text(text = String.format("%.1f / 5⭐", avgRating), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary) }
        Spacer(modifier = Modifier.height(12.dp))
        StatsCard(title = "Your rank:", icon = Icons.Default.MilitaryTech, isExpanded = expandedIndex == 3, onClick = { expandedIndex = if (expandedIndex == 3) -1 else 3 }) { Text(text = rankName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = rankColor) }
    }
}

@Composable
fun NotificationContent(
    updates: List<AnimeUpdate>,
    isChecking: Boolean,
    onAccept: (AnimeUpdate) -> Unit,
    onDismiss: (AnimeUpdate) -> Unit
) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Updates", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (isChecking) {
            Spacer(Modifier.height(24.dp)); CircularProgressIndicator(color = OneUiBlue); Spacer(Modifier.height(8.dp))
            Text(text = "Checking APIs (Shikimori, Jikan, TMDB)...\nIt will take about 10 minutes.", color = TextSecondary, textAlign = TextAlign.Center)
        } else if (updates.isEmpty()) {
            Spacer(Modifier.height(24.dp)); Icon(Icons.Default.CheckCircle, null, tint = OneUiBluePastel, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(8.dp))
            Text("You are up to date!", color = TextPrimary, fontSize = 18.sp)
        } else {
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(updates) { update ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(CardGradientStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(update.title, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
                            Text("Found ${update.newEpisodes} eps (You: ${update.currentEpisodes})", color = OneUiBluePastel, fontSize = 14.sp)
                            Text("Source: ${update.source}", color = Color.Gray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDismiss(update) }) { Icon(Icons.Default.Close, null, tint = OneUiRed) }
                        IconButton(onClick = { onAccept(update); Toast.makeText(ctx, "Updated ${update.title}!", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Check, null, tint = OneUiBlue) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: NavController, vm: AnimeViewModel, sharedTransitionScope: SharedTransitionScope, animatedVisibilityScope: AnimatedVisibilityScope) {
    val kbd = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    var showSortSheet by remember { mutableStateOf(false) }
    var showCSheet by remember { mutableStateOf(false) }
    var showNotifSheet by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var selectedAnimeForPreview by remember { mutableStateOf<Anime?>(null) }
    var currentPreviewAnime by remember { mutableStateOf<Anime?>(null) }

    // BUG FIX #3: Проверка обновлений в фоне
    LaunchedEffect(Unit) { vm.checkForUpdates() }

    if (selectedAnimeForPreview != null) currentPreviewAnime = selectedAnimeForPreview
    BackHandler(enabled = isSearchVisible || vm.searchQuery.isNotEmpty() || selectedAnimeForPreview != null) {
        if (selectedAnimeForPreview != null) { performHaptic(view, false); selectedAnimeForPreview = null }
        else if (isSearchVisible) { performHaptic(view, false); isSearchVisible = false; vm.searchQuery = ""; focusManager.clearFocus(); kbd?.hide() }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Scaffold(containerColor = Color.Transparent, bottomBar = {}, floatingActionButton = {}) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Новый темный фон вместо точек
            Box(modifier = Modifier.fillMaxSize().background(AppBackground))

            Box(modifier = Modifier.zIndex(5f).align(Alignment.TopCenter)) { MalistTopBar(currentSort = vm.sortOption, onSortSelected = { newOption -> performHaptic(view, false); vm.sortOption = newOption }) }
            val shouldBlur = (isSearchVisible && vm.searchQuery.isBlank()) || selectedAnimeForPreview != null
            val blurAmount by animateDpAsState(targetValue = if (shouldBlur) 10.dp else 0.dp, label = "blur")

            Column(modifier = Modifier.fillMaxSize().blur(blurAmount)) {
                Box(modifier = Modifier.fillMaxSize().weight(1f).background(AppBackground)) {
                    val list = vm.getDisplayList()
                    if (list.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("List is empty (or no matches)", color = Color.Gray) } }
                    else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(top = 120.dp, bottom = 120.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(list, key = { it.id }) { anime ->
                                Box(modifier = Modifier.animateItem()) {
                                    OneUiAnimeCard(anime = anime, viewModel = vm, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope, onClick = { nav.navigate("add_anime?animeId=${anime.id}") }, onLongClick = { performHaptic(view, true); selectedAnimeForPreview = anime })
                                }
                            }
                        }
                    }
                }
            }

            if (selectedAnimeForPreview == null && !isSearchVisible) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).navigationBarsPadding().zIndex(3f)) {
                    Surface(shape = CircleShape, color = Color(0xFF3E3E40).copy(alpha = 0.6f), shadowElevation = 10.dp, tonalElevation = 5.dp) {
                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            with(sharedTransitionScope) {
                                Box(modifier = Modifier.size(28.dp).sharedBounds(rememberSharedContentState(key = "fab_container"), animatedVisibilityScope = animatedVisibilityScope, resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds).clickable { performHaptic(view, false); nav.navigate("add_anime") }, contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "fab_icon"), animatedVisibilityScope = animatedVisibilityScope))
                                }
                            }
                            Box(modifier = Modifier.size(26.dp).border(1.5.dp, Color.White, CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { performHaptic(view, false); showCSheet = true }, contentAlignment = Alignment.Center) { Text(text = "C", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }

                            Box {
                                Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White, modifier = Modifier.size(26.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { performHaptic(view, false); showNotifSheet = true })
                                if (vm.updates.isNotEmpty()) { Box(modifier = Modifier.align(Alignment.TopEnd).size(10.dp).background(OneUiRed, CircleShape)) }
                            }
                        }
                    }
                }
            }

            if (selectedAnimeForPreview == null) {
                FloatingActionButton(onClick = { performHaptic(view, false); isSearchVisible = !isSearchVisible; if (!isSearchVisible) { vm.searchQuery = ""; focusManager.clearFocus(); kbd?.hide() } }, containerColor = (if (isSearchVisible) OneUiBlue else Color(0xFF333333)).copy(alpha = 0.6f), contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 40.dp, end = 16.dp).zIndex(3f)) { Icon(imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search") }
            }

            AnimatedVisibility(visible = isSearchVisible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).windowInsetsPadding(WindowInsets.ime).padding(bottom = 16.dp).zIndex(10f)) {
                val glowColor = Color.White.copy(alpha = 0.15f); val borderColor = Color.White.copy(alpha = 0.1f)
                BasicTextField(value = vm.searchQuery, onValueChange = { vm.searchQuery = it; if(it.isNotEmpty()) performHaptic(view, false) }, modifier = Modifier.fillMaxWidth().height(56.dp).shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = glowColor, spotColor = glowColor).border(1.dp, borderColor, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(CardGradientStart.copy(alpha = 0.95f)).padding(horizontal = 20.dp).focusRequester(searchFocusRequester), singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Color.White), cursorBrush = SolidColor(OneUiBlue), decorationBox = { innerTextField -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = OneUiBlue); Spacer(Modifier.width(12.dp)); Box { if (vm.searchQuery.isEmpty()) { Text("Search collection...", color = Color.Gray, fontSize = 16.sp) }; innerTextField() } } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { kbd?.hide() }))
                LaunchedEffect(Unit) { searchFocusRequester.requestFocus(); kbd?.show() }
            }

            if (shouldBlur && selectedAnimeForPreview == null) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus(); isSearchVisible = false; kbd?.hide() }.zIndex(2f)) }
            AnimatedVisibility(visible = selectedAnimeForPreview != null, enter = fadeIn() + scaleIn(initialScale = 0.9f) + slideInVertically { it / 6 }, exit = fadeOut() + scaleOut(targetScale = 0.9f) + slideOutVertically { it / 6 }, modifier = Modifier.zIndex(50f)) { currentPreviewAnime?.let { anime -> SimpleAnimePreviewOverlay(anime = anime, viewModel = vm, onDismiss = { selectedAnimeForPreview = null }, onDelete = { scope.launch { selectedAnimeForPreview = null; delay(250); vm.deleteAnime(anime.id) } }) } }
            AnimatedVisibility(visible = showScrollToTop && !isSearchVisible && selectedAnimeForPreview == null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 160.dp, end = 24.dp).zIndex(1f)) { Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF333333).copy(alpha = 0.8f)).clickable { performHaptic(view, false); scope.launch { listState.animateScrollToItem(0) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White) } }
        }

        if (showSortSheet) { ModalBottomSheet(onDismissRequest = { showSortSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) { Column(modifier = Modifier.padding(bottom = 32.dp)) { Text("Sort by", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)); SortOption.values().forEach { option -> NavigationDrawerItem(label = { Text(option.label) }, icon = { }, selected = vm.sortOption == option, onClick = { performHaptic(view, false); vm.sortOption = option; showSortSheet = false }, modifier = Modifier.padding(horizontal = 12.dp), colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent, selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), selectedTextColor = MaterialTheme.colorScheme.primary)) } } } }
        if (showCSheet) { ModalBottomSheet(onDismissRequest = { showCSheet = false }, containerColor = OneUiCardBg) { WatchStatsContent(animeList = vm.animeList) } }

        if (showNotifSheet) {
            ModalBottomSheet(onDismissRequest = { showNotifSheet = false }, containerColor = OneUiCardBg) {
                NotificationContent(updates = vm.updates, isChecking = vm.isCheckingUpdates, onAccept = { vm.acceptUpdate(it, view.context) }, onDismiss = { vm.dismissUpdate(it) })
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AddEditScreen(nav: NavController, vm: AnimeViewModel, id: String?, sharedTransitionScope: SharedTransitionScope, animatedVisibilityScope: AnimatedVisibilityScope) {
    val anime = remember(id) { id?.let { vm.getAnimeById(it) } }
    var title by remember { mutableStateOf(anime?.title ?: "") }
    var ep by remember { mutableStateOf(anime?.episodes?.toString() ?: "") }
    var rate by remember { mutableIntStateOf(anime?.rating ?: 0) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val hasChanges by remember(title, ep, rate, uri) { derivedStateOf { if (id == null) { title.isNotBlank() } else { (anime != null) && (title != anime.title || ep != anime.episodes.toString() || rate != anime.rating || uri != null) } } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    val ctx = LocalContext.current
    val view = LocalView.current

    with(sharedTransitionScope) {
        val sharedModifier = if (id == null) { Modifier.sharedBounds(rememberSharedContentState(key = "fab_container"), animatedVisibilityScope = animatedVisibilityScope, resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds) } else { Modifier.sharedBounds(rememberSharedContentState(key = "card_${id}"), animatedVisibilityScope = animatedVisibilityScope, resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds) }
        Scaffold(modifier = Modifier.fillMaxSize().then(sharedModifier), containerColor = Color.Transparent, topBar = { Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = if (id == null) Modifier.sharedElement(rememberSharedContentState(key = "fab_icon"), animatedVisibilityScope = animatedVisibilityScope) else Modifier) }; Spacer(Modifier.width(16.dp)); Text(text = if (id == null) "Add title" else "Edit title", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary) } }, floatingActionButton = { AnimatedSaveFab(isEnabled = hasChanges, onClick = { performHaptic(view, false); if (title.isNotEmpty()) { scope.launch { delay(600); if (id != null) vm.updateAnime(ctx, id, title, ep.toIntOrNull()?:0, rate, uri) else vm.addAnime(ctx, title, ep.toIntOrNull()?:0, rate, uri); nav.popBackStack() } } else { Toast.makeText(ctx, "Enter title", Toast.LENGTH_SHORT).show() } }) }) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(AppBackground))
                Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 24.dp).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.width(180.dp).aspectRatio(0.7f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp)).clickable { performHaptic(view, false); launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
                        val imageModifier = if (id != null) Modifier.sharedElement(rememberSharedContentState(key = "image_${id}"), animatedVisibilityScope = animatedVisibilityScope) else Modifier
                        Box(modifier = Modifier.fillMaxSize().then(imageModifier).clip(RoundedCornerShape(32.dp))) {
                            if (uri != null) AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else if (anime?.imageFileName != null) AsyncImage(vm.getImgPath(anime.imageFileName), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary); Text("Add Photo", color = MaterialTheme.colorScheme.secondary) }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.weight(1f)) { OneUiTextField(value = title, onValueChange = { title = it }, placeholder = "Title", singleLine = false, maxLines = 4) }; if (title.isNotEmpty()) { Spacer(Modifier.width(8.dp)); AnimatedCopyButton(textToCopy = title) } }
                    Spacer(Modifier.height(16.dp))
                    OneUiTextField(value = ep, onValueChange = { if (it.all { c -> c.isDigit() }) ep = it }, placeholder = "Episodes", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(12.dp))
                    EpisodeSuggestions { selectedEp -> performHaptic(view, false); ep = selectedEp }
                    Spacer(Modifier.height(24.dp))
                    StarRatingBar(rating = rate) { newRate -> performHaptic(view, false); rate = newRate }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { window.attributes.preferredDisplayModeId = 0 }
        checkPerms()
        setContent {
            OneUiTheme {
                val navController = rememberNavController()
                val viewModel: AnimeViewModel = viewModel()
                LaunchedEffect(Unit) { viewModel.loadAnime() }
                SharedTransitionLayout {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { HomeScreen(navController, viewModel, this@SharedTransitionLayout, this) }
                        composable("add_anime?animeId={animeId}", arguments = listOf(navArgument("animeId") { nullable = true })) { AddEditScreen(navController, viewModel, it.arguments?.getString("animeId"), this@SharedTransitionLayout, this) }
                    }
                }
            }
        }
    }
    private fun checkPerms() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")); startActivity(intent); Toast.makeText(this, "Need file access", Toast.LENGTH_LONG).show()
        }
    }
}