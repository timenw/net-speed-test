package com.netspeedtest.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.netspeedtest.engine.SpeedTestEngine
import com.netspeedtest.data.TestDatabase
import com.netspeedtest.ui.theme.*
import com.netspeedtest.billing.BillingManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { TestDatabase(context) }
    val billingManager = remember { BillingManager(context) }

    var currentTab by remember { mutableIntStateOf(0) }
    var isPremium by remember { mutableStateOf(billingManager.isPremium) }
    var testResult by remember { mutableStateOf<SpeedTestEngine.TestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testProgress by remember { mutableIntStateOf(0) }
    var testStatus by remember { mutableStateOf("") }
    var historyResults by remember { mutableStateOf(db.getRecentResults()) }
    var resultCount by remember { mutableStateOf(db.getResultCount()) }

    val engine = remember { SpeedTestEngine() }

    LaunchedEffect(Unit) {
        billingManager.startConnection()
        billingManager.setPremiumCallback { premium ->
            isPremium = premium
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📡 ", fontSize = 20.sp)
                        Text(
                            "NetSpeed Test",
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Blue800)
            )
        },
        bottomBar = {
            Column {
                // Banner ad for non-premium
                if (!isPremium) {
                    AndroidView(
                        factory = { ctx ->
                            AdView(ctx).apply {
                                setAdSize(AdSize.BANNER)
                                adUnitId = "ca-app-pub-1212786513185567/1371799713"
                                loadAd(AdRequest.Builder().build())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )
                }
                // Bottom navigation
                NavigationBar(containerColor = White) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Test") },
                        label = { Text("Test") },
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = "History") },
                        label = { Text("History") },
                        selected = currentTab == 1,
                        onClick = {
                            currentTab = 1
                            historyResults = db.getRecentResults()
                            resultCount = db.getResultCount()
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 }
                    )
                }
                // Purchase bar
                if (!isPremium) {
                    Surface(
                        color = Orange500,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                billingManager.launchPurchaseFlow(context as android.app.Activity)
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            "🚫 Remove Ads - $0.99",
                            color = White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (currentTab) {
            0 -> TestTab(
                padding = padding,
                testResult = testResult,
                isTesting = isTesting,
                testProgress = testProgress,
                testStatus = testStatus,
                onStartTest = {
                    if (!isTesting) {
                        scope.launch {
                            isTesting = true
                            testProgress = 0
                            testStatus = "Starting..."
                            testResult = null

                            val networkType = getNetworkType(context)
                            val result = engine.runFullTest { status, progress ->
                                testStatus = status
                                testProgress = progress
                            }

                            testResult = result
                            db.saveResult(result, networkType)
                            historyResults = db.getRecentResults()
                            resultCount = db.getResultCount()
                            isTesting = false
                        }
                    }
                }
            )
            1 -> HistoryTab(padding = padding, results = historyResults, resultCount = resultCount)
            2 -> SettingsTab(
                padding = padding,
                isPremium = isPremium,
                resultCount = resultCount,
                onClearHistory = {
                    db.clearHistory()
                    historyResults = emptyList()
                    resultCount = 0
                }
            )
        }
    }
}

@Composable
fun TestTab(
    padding: PaddingValues,
    testResult: SpeedTestEngine.TestResult?,
    isTesting: Boolean,
    testProgress: Int,
    testStatus: String,
    onStartTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Start test button or progress
        if (isTesting) {
            TestingView(progress = testProgress, status = testStatus)
        } else {
            TestButton(testResult = testResult, onStartTest = onStartTest)
        }

        // Results
        if (testResult != null && !isTesting) {
            Spacer(modifier = Modifier.height(20.dp))
            ResultCard(testResult!!)
        }
    }
}

@Composable
fun TestButton(testResult: SpeedTestEngine.TestResult?, onStartTest: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Big circular start button
        Box(
            modifier = Modifier
                .size(140.dp)
                .then(
                    if (testResult == null) Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                    else Modifier
                )
                .clip(CircleShape)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Blue400, Blue700)
                    )
                )
                .clickable(onClick = onStartTest),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Start Test",
                    tint = White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "START",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (testResult != null) {
            Text(
                "Tap to test again",
                color = Grey600,
                fontSize = 14.sp
            )
        } else {
            Text(
                "Tap to start speed test",
                color = Grey600,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Low data mode: < 500KB per test",
                color = Teal500,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TestingView(progress: Int, status: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Circular progress
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.size(140.dp),
                color = Blue500,
                strokeWidth = 8.dp,
                trackColor = Grey200,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$progress%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue700
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            status,
            color = Grey700,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Using minimal data...",
            color = Teal500,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ResultCard(result: SpeedTestEngine.TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stability score
            StabilityScoreWidget(result.stabilityScore)

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Grey200, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Ping", "${result.pingMs.toInt()}ms", getPingColor(result.pingMs))
                MetricItem("Jitter", "${result.jitterMs.toInt()}ms", getJitterColor(result.jitterMs))
                MetricItem("Loss", "${result.packetLossPercent.toInt()}%", getLossColor(result.packetLossPercent))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Grey200, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Download", "${String.format("%.1f", result.downloadMbps)} Mbps", getSpeedColor(result.downloadMbps))
                MetricItem("Upload", "${String.format("%.1f", result.uploadMbps)} Mbps", getSpeedColor(result.uploadMbps))
            }
        }
    }
}

@Composable
fun StabilityScoreWidget(score: Int) {
    val color = when {
        score >= 80 -> ScoreExcellent
        score >= 60 -> ScoreGood
        score >= 40 -> ScoreFair
        score >= 20 -> ScorePoor
        else -> ScoreBad
    }
    val label = when {
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        score >= 20 -> "Poor"
        else -> "Bad"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Stability Score", color = Grey600, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$score",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Grey500, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HistoryTab(padding: PaddingValues, results: List<SpeedTestEngine.TestResult>, resultCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        Text(
            "History ($resultCount tests)",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Grey900
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Grey300,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No tests yet", color = Grey400, fontSize = 14.sp)
                    Text("Run your first speed test!", color = Grey400, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { result ->
                    HistoryItem(result)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(result: SpeedTestEngine.TestResult) {
    val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(result.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(dateStr, color = Grey500, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⬇ ${String.format("%.1f", result.downloadMbps)}M", fontSize = 12.sp, color = Grey700)
                    Text("⬆ ${String.format("%.1f", result.uploadMbps)}M", fontSize = 12.sp, color = Grey700)
                    Text("📶 ${result.pingMs.toInt()}ms", fontSize = 12.sp, color = Grey700)
                }
            }
            val scoreColor = when {
                result.stabilityScore >= 80 -> ScoreExcellent
                result.stabilityScore >= 60 -> ScoreGood
                result.stabilityScore >= 40 -> ScoreFair
                else -> ScorePoor
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(scoreColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${result.stabilityScore}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }
        }
    }
}

@Composable
fun SettingsTab(
    padding: PaddingValues,
    isPremium: Boolean,
    resultCount: Int,
    onClearHistory: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Grey900)
        Spacer(modifier = Modifier.height(16.dp))

        // Stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Statistics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total tests", color = Grey600, fontSize = 13.sp)
                    Text("$resultCount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Data per test", color = Grey600, fontSize = 13.sp)
                    Text("< 500KB", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Teal500)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Premium status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPremium) Green500.copy(alpha = 0.1f) else Orange500.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isPremium) Icons.Default.CheckCircle else Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isPremium) Green500 else Orange500,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (isPremium) "Premium Active" else "Free Version",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        if (isPremium) "No ads, unlimited history" else "Ads shown, limited features",
                        color = Grey600,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Clear history
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showClearDialog = true },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Red500, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Clear History", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Red500)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About
        Text(
            "NetSpeed Test v1.0.0\nLow data usage speed test for Africa\nNo server required",
            color = Grey400,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Delete all test history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearDialog = false
                }) { Text("Clear", color = Red500) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// Helper functions
fun getNetworkType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return "unknown"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            else -> "other"
        }
    } else {
        @Suppress("DEPRECATION")
        val info = cm.activeNetworkInfo
        return when (info?.type) {
            ConnectivityManager.TYPE_WIFI -> "WiFi"
            ConnectivityManager.TYPE_MOBILE -> "Mobile"
            else -> "unknown"
        }
    }
}

fun getPingColor(ping: Double) = when {
    ping < 50 -> Green500
    ping < 100 -> Orange500
    else -> Red500
}

fun getJitterColor(jitter: Double) = when {
    jitter < 10 -> Green500
    jitter < 30 -> Orange500
    else -> Red500
}

fun getLossColor(loss: Double) = when {
    loss == 0.0 -> Green500
    loss < 3 -> Orange500
    else -> Red500
}

fun getSpeedColor(speed: Double) = when {
    speed > 20 -> Green500
    speed > 5 -> Orange500
    speed > 1 -> Color(0xFFFF9800)
    else -> Red500
}
