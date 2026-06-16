package com.netspeedtest.engine

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Network speed test engine — pure client-side, no server needed.
 * Uses public endpoints for measurement.
 */
class SpeedTestEngine {

    companion object {
        // Public endpoints for testing
        private const val PING_HOST = "8.8.8.8"  // Google DNS
        private const val PING_PORT = 53

        // Download test files (small, from public CDNs)
        private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=200000"

        // Upload endpoint (httpbin — free, reliable)
        private const val UPLOAD_URL = "https://httpbin.org/post"

        // Packet loss test host
        private const val LOSS_HOST = "1.1.1.1"  // Cloudflare DNS
        private const val LOSS_PORT = 53

        private const val TIMEOUT_MS = 5000
        private const val DOWNLOAD_DURATION_MS = 3000L
        private const val UPLOAD_DURATION_MS = 3000L
    }

    data class TestResult(
        val pingMs: Double = 0.0,
        val jitterMs: Double = 0.0,
        val packetLossPercent: Double = 0.0,
        val downloadMbps: Double = 0.0,
        val uploadMbps: Double = 0.0,
        val stabilityScore: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .build()

    /**
     * Run complete speed test
     */
    suspend fun runFullTest(onProgress: (String, Int) -> Unit = { _, _ -> }): TestResult {
        return withContext(Dispatchers.IO) {
            // Step 1: Ping test
            onProgress("Testing latency...", 10)
            val (ping, jitter) = measurePingAndJitter()

            // Step 2: Packet loss test
            onProgress("Testing packet loss...", 30)
            val loss = measurePacketLoss()

            // Step 3: Download speed
            onProgress("Testing download speed...", 50)
            val download = measureDownloadSpeed { progress ->
                onProgress("Testing download...", 50 + (progress * 0.2).toInt())
            }

            // Step 4: Upload speed
            onProgress("Testing upload speed...", 75)
            val upload = measureUploadSpeed { progress ->
                onProgress("Testing upload...", 75 + (progress * 0.2).toInt())
            }

            // Step 5: Calculate score
            onProgress("Calculating score...", 95)
            val score = calculateStabilityScore(ping, jitter, loss, download)

            onProgress("Test complete!", 100)

            TestResult(
                pingMs = ping,
                jitterMs = jitter,
                packetLossPercent = loss,
                downloadMbps = download,
                uploadMbps = upload,
                stabilityScore = score
            )
        }
    }

    /**
     * Measure ping (latency) and jitter using TCP connection to Google DNS.
     * 5 samples, returns median ping and jitter (max - min).
     */
    private fun measurePingAndJitter(): Pair<Double, Double> {
        val times = mutableListOf<Long>()
        repeat(5) {
            try {
                val start = System.nanoTime()
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(PING_HOST, PING_PORT), TIMEOUT_MS)
                val rtt = (System.nanoTime() - start) / 1_000_000.0
                socket.close()
                times.add(rtt.toLong())
                Thread.sleep(100)
            } catch (_: Exception) {
                // Connection failed = very high latency
                times.add(TIMEOUT_MS.toLong())
            }
        }
        if (times.isEmpty()) return Pair(999.0, 999.0)
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2].toDouble()
        val jitter = (sorted.last() - sorted.first()).toDouble()
        return Pair(median, jitter)
    }

    /**
     * Measure packet loss using UDP packets to Cloudflare DNS.
     * Sends 20 UDP packets, counts responses.
     */
    private fun measurePacketLoss(): Double {
        var sent = 0
        var received = 0
        try {
            val socket = DatagramSocket()
            socket.soTimeout = 500
            val address = InetAddress.getByName(LOSS_HOST)

            repeat(20) { i ->
                try {
                    val data = "ping$i".toByteArray()
                    val packet = DatagramPacket(data, data.size, address, LOSS_PORT)
                    socket.send(packet)
                    sent++

                    val buffer = ByteArray(1024)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    received++
                } catch (_: SocketTimeoutException) {
                    // Packet lost
                } catch (_: IOException) {
                    // Network error
                }
                try { Thread.sleep(50) } catch (_: InterruptedException) {}
            }
            socket.close()
        } catch (_: Exception) {
            return 100.0  // Complete failure
        }

        return if (sent == 0) 100.0 else ((sent - received) * 100.0 / sent)
    }

    /**
     * Measure download speed by fetching a file from Cloudflare.
     * Downloads for up to 3 seconds, calculates Mbps.
     */
    private fun measureDownloadSpeed(onProgress: (Float) -> Unit = {}): Double {
        return try {
            val request = Request.Builder()
                .url(DOWNLOAD_URL)
                .header("Cache-Control", "no-cache")
                .build()

            val start = System.nanoTime()
            var bytesReceived = 0L

            httpClient.newCall(request).execute().use { response ->
                val body = response.body ?: return 0.0
                val source = body.source()
                val buffer = okio.Buffer()
                var elapsed: Long

                while (true) {
                    val read = source.read(buffer, 8192)
                    if (read == -1L) break
                    bytesReceived += read
                    elapsed = (System.nanoTime() - start) / 1_000_000
                    if (elapsed >= DOWNLOAD_DURATION_MS) break
                    onProgress((elapsed.toFloat() / DOWNLOAD_DURATION_MS).coerceIn(0f, 1f))
                }
            }

            val duration = (System.nanoTime() - start) / 1_000_000_000.0
            if (duration < 0.1) return 0.0
            (bytesReceived * 8.0) / (duration * 1_000_000.0)  // Mbps
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * Measure upload speed by POSTing data to httpbin.org.
     * Uploads 100KB, measures time.
     */
    private fun measureUploadSpeed(onProgress: (Float) -> Unit = {}): Double {
        return try {
            val uploadData = ByteArray(100 * 1024) { (it % 256).toByte() }  // 100KB
            val requestBody = okhttp3.RequestBody.Companion.create(
                uploadData,
                "application/octet-stream".toMediaType()
            )
            val request = Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build()

            val start = System.nanoTime()
            httpClient.newCall(request).execute().use { response ->
                response.body?.string()  // Read response to complete
            }
            val duration = (System.nanoTime() - start) / 1_000_000_000.0
            if (duration < 0.1) return 0.0
            (uploadData.size * 8.0) / (duration * 1_000_000.0)  // Mbps
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * Calculate stability score (0-100) based on all metrics.
     */
    private fun calculateStabilityScore(
        ping: Double, jitter: Double, loss: Double, download: Double
    ): Int {
        // Ping score (max 30): <30ms=30, <100ms=20, <200ms=10, else=0
        val pingScore = when {
            ping < 30 -> 30
            ping < 100 -> 20
            ping < 200 -> 10
            else -> 0
        }

        // Jitter score (max 25): <5ms=25, <20ms=15, <50ms=5, else=0
        val jitterScore = when {
            jitter < 5 -> 25
            jitter < 20 -> 15
            jitter < 50 -> 5
            else -> 0
        }

        // Loss score (max 25): 0%=25, <2%=15, <5%=5, else=0
        val lossScore = when {
            loss == 0.0 -> 25
            loss < 2 -> 15
            loss < 5 -> 5
            else -> 0
        }

        // Speed score (max 20): >50Mbps=20, >20Mbps=15, >5Mbps=10, >1Mbps=5, else=0
        val speedScore = when {
            download > 50 -> 20
            download > 20 -> 15
            download > 5 -> 10
            download > 1 -> 5
            else -> 0
        }

        return (pingScore + jitterScore + lossScore + speedScore).coerceIn(0, 100)
    }
}
