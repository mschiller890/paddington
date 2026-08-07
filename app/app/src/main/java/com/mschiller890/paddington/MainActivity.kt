package com.mschiller890.paddington

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mschiller890.paddington.ui.theme.PaddingtonTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MIN_PADDING_DP = 0
private const val MAX_PADDING_DP = 64
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaddingtonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PaddingSettingsScreen()
                }
            }
        }
    }
}

@Composable
fun PaddingSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(Hook.Settings.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var paddingValue by remember {
        mutableStateOf(
            prefs.getInt(Hook.Settings.KEY_PADDING_DP, Hook.Settings.DEFAULT_PADDING_DP).toFloat()
        )
    }
    val scope = rememberCoroutineScope()
    var showRootWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!checkRootAvailable()) showRootWarning = true
    }

    val density = LocalDensity.current.density
    val cutout = remember { cutoutLeftRight(context) }
    val stockInset = remember { readStockInsetPx(context) }
    val stripHeight = remember { statusBarHeightPx(context) }
    val fallback = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.statusbar_native)
    }

    var strip by remember { mutableStateOf<Bitmap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refresh() {
        scope.launch {
            strip = null
            strip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                captureStatusBarStrip(stripHeight, cutout.first, cutout.second) ?: fallback
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            observer.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_RESUME)
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val preview = remember(strip, paddingValue, stockInset, cutout) {
        val s = strip ?: return@remember null
        buildPreviewBitmap(
            s, density, actualLeftInset(s, cutout.second),
            paddingValue, stockInset, cutout.first, cutout.second
        )
    }

    val activity = context as? Activity
    DisposableEffect(activity, preview != null) {
        val window = activity?.window
        if (window != null) {
            val controller =
                WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            fun sync() {
                if (preview != null) {
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }
            }
            sync()
            val focusObserver = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) sync()
            }
            window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusObserver)
            onDispose {
                window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focusObserver)
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        } else {
            onDispose {}
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val s = strip
        val p = preview
        if (s != null && p != null) {
            Image(
                bitmap = p.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(s.width.toFloat() / s.height.toFloat())
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
        ) {
            Text(
                text = "Paddington",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 28.dp)
            )
            Text(
                text = "Status bar side padding",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "${paddingValue.toInt()}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "dp on each side",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Slider(
                        value = paddingValue,
                        onValueChange = { paddingValue = it },
                        valueRange = MIN_PADDING_DP.toFloat()..MAX_PADDING_DP.toFloat(),
                        steps = MAX_PADDING_DP - MIN_PADDING_DP - 1,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            thumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    //if (s != null) {
                    //    Spacer(modifier = Modifier.height(16.dp))
                    //    Row(
                    //        modifier = Modifier.fillMaxWidth(),
                    //        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    //        verticalAlignment = Alignment.CenterVertically
                    //    ) {
                    //        val stockDp = formatDp(stockInset, density)
                    //        val nowDp = formatDp(actualLeftInset(s, cutout.second), density)
                    //        val newDp =
                    //            formatDp(stockInset + (paddingValue * density).roundToInt(), density)
                    //        LegendChip(
                    //            MaterialTheme.colorScheme.primary,
                    //            "+${paddingValue.roundToInt()}dp → $newDp dp total"
                    //        )
                    //        LegendChip(MaterialTheme.colorScheme.outline, "stock $stockDp dp")
                    //        LegendChip(MaterialTheme.colorScheme.tertiary, "now $nowDp dp")
                    //    }
                    //}
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp, top = 16.dp)
                .navigationBarsPadding()
        ) {
            if (s == null) {
                LoadingBar()
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val dp = save(context, paddingValue, prefs)
                    toast(context, "Saved $dp dp, restarting System UI...")
                    restartSystemUi(context, scope)
                },
                shape = CircleShape,
                contentPadding = PaddingValues(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply & restart System UI", style = MaterialTheme.typography.titleMedium)
            }

            TextButton(
                onClick = {
                    val dp = save(context, paddingValue, prefs)
                    toast(context, "Saved $dp dp. Restart System UI to apply.")
                },
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save only", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showRootWarning) {
        AlertDialog(
            onDismissRequest = { /* not dismissible, must quit */ },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Root not detected") },
            text = {
                Text(
                    "Paddington needs root access to restart System UI and apply " +
                        "changes. No root was detected on this device, so the app can't work."
                )
            },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text("Quit")
                }
            }
        )
    }
}

@Composable
private fun LoadingBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Capturing status bar…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LegendChip(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatDp(px: Int, density: Float): String = "%.1f".format(px / density)

private fun readStockInsetPx(context: Context): Int {
    val id = context.resources.getIdentifier("status_bar_indicator_corner_padding", "dimen", "android")
    val px = if (id != 0) context.resources.getDimensionPixelSize(id) else 44
    Log.i("Paddington", "preview stock inset id=$id px=$px")
    return px
}

private fun cutoutLeftRight(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val cutout = wm?.defaultDisplay?.cutout
    if (cutout != null) {
        val rect = cutout.getBoundingRectTop()
        if (!rect.isEmpty) {
            Log.i("Paddington", "preview cutout ${rect.left}-${rect.right}")
            return rect.left to rect.right
        }
    }
    Log.i("Paddington", "preview cutout fallback 506-574")
    return 506 to 574
}

private fun statusBarHeightPx(context: Context): Int {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val cutout = wm?.defaultDisplay?.cutout
    if (cutout != null && cutout.safeInsetTop > 0) {
        Log.i("Paddington", "preview strip height=${cutout.safeInsetTop}")
        return cutout.safeInsetTop
    }
    Log.i("Paddington", "preview strip height fallback 92")
    return 92
}

private fun captureStatusBarStrip(heightPx: Int, cutoutL: Int, cutoutR: Int): Bitmap? {
    repeat(3) { attempt ->
        try {
            if (attempt == 0) Thread.sleep(900L) else Thread.sleep(600L)
            val process = ProcessBuilder("su", "-c", "screencap -p").start()
            val bytes = process.inputStream.use { it.readBytes() }
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroy()
            }
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val w = full.width
            val cropH = minOf(heightPx, full.height)
            val strip = Bitmap.createBitmap(full, 0, 0, w, cropH)
            full.recycle()
            if (looksLikeStatusBar(strip, cutoutL, cutoutR)) {
                Log.i("Paddington", "preview captured ${w}x$cropH (attempt $attempt)")
                return strip
            }
            strip.recycle()
        } catch (t: Throwable) {
            Log.i("Paddington", "preview capture attempt $attempt failed: $t")
        }
    }
    return null
}

private fun looksLikeStatusBar(strip: Bitmap, cutoutL: Int, cutoutR: Int): Boolean {
    var left = 0
    var right = 0
    var x = 0
    while (x < cutoutL - 8) {
        var y = 0
        while (y < strip.height) {
            val c = strip.getPixel(x, y)
            val sum = (c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)
            if (sum > 300) left++
            y += 4
        }
        x += 4
    }
    x = cutoutR + 8
    while (x < strip.width) {
        var y = 0
        while (y < strip.height) {
            val c = strip.getPixel(x, y)
            val sum = (c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)
            if (sum > 300) right++
            y += 4
        }
        x += 4
    }
    return left > 10 && right > 10
}

private fun actualLeftInset(strip: Bitmap, cutoutR: Int): Int =
    (strip.width - rightClusterEnd(strip, cutoutR)).coerceAtLeast(0)

private fun rightClusterEnd(strip: Bitmap, cutoutR: Int): Int {
    for (x in strip.width - 1 downTo cutoutR + 8) {
        var y = 0
        while (y < strip.height) {
            val c = strip.getPixel(x, y)
            val sum = (c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)
            if (sum > 300) return x
            y += 2
        }
    }
    return cutoutR + 8
}

private fun buildPreviewBitmap(
    strip: Bitmap,
    density: Float,
    curLeft: Int,
    paddingDp: Float,
    stockInset: Int,
    cutoutL: Int,
    cutoutR: Int
): Bitmap {
    val w = strip.width
    val h = strip.height
    val newLeft = stockInset + (paddingDp * density).roundToInt()
    val delta = newLeft - curLeft

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(strip, 0f, 0f, null)

    if (delta != 0) {
        val sampleY = (h - 6).coerceAtLeast(0)
        val leftBandStart = curLeft
        val leftBandEnd = cutoutL - 8
        val rightBandStart = cutoutR + 8
        val rightBandEnd = w - curLeft
        if (leftBandStart < leftBandEnd) {
            val bg = strip.getPixel((cutoutL - 24).coerceIn(0, w - 1), sampleY)
            val erase = Paint().apply { color = bg }
            canvas.drawRect(leftBandStart.toFloat(), 0f, leftBandEnd.toFloat(), h.toFloat(), erase)
            val leftSrc = Bitmap.createBitmap(strip, leftBandStart, 0, leftBandEnd - leftBandStart, h)
            canvas.drawBitmap(leftSrc, (leftBandStart + delta).toFloat(), 0f, null)
        }
        if (rightBandStart < rightBandEnd) {
            val bg = strip.getPixel((cutoutR + 24).coerceIn(0, w - 1), sampleY)
            val erase = Paint().apply { color = bg }
            canvas.drawRect(rightBandStart.toFloat(), 0f, rightBandEnd.toFloat(), h.toFloat(), erase)
            val rightSrc = Bitmap.createBitmap(strip, rightBandStart, 0, rightBandEnd - rightBandStart, h)
            canvas.drawBitmap(rightSrc, (rightBandStart - delta).toFloat(), 0f, null)
        }
    }

    val accent = Paint()
    accent.color = 0x55FFB300.toInt()
    if (delta > 0) {
        canvas.drawRect(curLeft.toFloat(), 0f, newLeft.toFloat(), h.toFloat(), accent)
        canvas.drawRect((w - newLeft).toFloat(), 0f, (w - curLeft).toFloat(), h.toFloat(), accent)
    }

    val nowPaint = Paint()
    nowPaint.style = Paint.Style.STROKE
    nowPaint.strokeWidth = 2f
    nowPaint.color = 0x66FFFFFF.toInt()
    val stockPaint = Paint()
    stockPaint.style = Paint.Style.STROKE
    stockPaint.strokeWidth = 2f
    stockPaint.color = 0x80FFFFFF.toInt()
    val newPaint = Paint()
    newPaint.style = Paint.Style.STROKE
    newPaint.strokeWidth = 3f
    newPaint.color = android.graphics.Color.WHITE

    canvas.drawLine(stockInset.toFloat(), 0f, stockInset.toFloat(), h.toFloat(), stockPaint)
    canvas.drawLine((w - stockInset).toFloat(), 0f, (w - stockInset).toFloat(), h.toFloat(), stockPaint)
    canvas.drawLine(curLeft.toFloat(), 0f, curLeft.toFloat(), h.toFloat(), nowPaint)
    canvas.drawLine((w - curLeft).toFloat(), 0f, (w - curLeft).toFloat(), h.toFloat(), nowPaint)
    if (newLeft != curLeft) {
        canvas.drawLine(newLeft.toFloat(), 0f, newLeft.toFloat(), h.toFloat(), newPaint)
        canvas.drawLine((w - newLeft).toFloat(), 0f, (w - newLeft).toFloat(), h.toFloat(), newPaint)
    }

    return out
}

/** Clamps, saves to prefs, and returns the stored value. */
private fun save(
    context: Context,
    value: Float,
    prefs: android.content.SharedPreferences
): Int {
    val dp = value.toInt().coerceIn(MIN_PADDING_DP, MAX_PADDING_DP)
    prefs.edit().putInt(Hook.Settings.KEY_PADDING_DP, dp).apply()
    return dp
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
}

/** Checks whether root access is available on this device. */
private suspend fun checkRootAvailable(): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").start()
            process.waitFor() == 0
        } catch (t: Throwable) {
            false
        }
    }

/**
 * Restarts the System UI process so the module re-reads the settings.
 * Tries to kill it as root first (LSPosed modules are expected to run on
 * a rooted device), and falls back to `am force-stop`.
 */
private fun restartSystemUi(context: Context, scope: CoroutineScope) {
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val ok = killAsRoot() || forceStop()
        val message = if (ok) {
            "System UI restarted!"
        } else {
            "Could not restart System UI (no root?); restart manually."
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            toast(context, message)
        }
    }
}

private fun killAsRoot(): Boolean = try {
    val process = ProcessBuilder("su", "-c", "pkill -f $SYSTEM_UI_PACKAGE").start()
    process.waitFor() == 0
} catch (t: Throwable) {
    false
}

private fun forceStop(): Boolean = try {
    val process = ProcessBuilder("am", "force-stop", SYSTEM_UI_PACKAGE).start()
    process.waitFor() == 0
} catch (t: Throwable) {
    false
}