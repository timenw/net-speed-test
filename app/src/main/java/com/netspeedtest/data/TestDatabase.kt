package com.netspeedtest.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.netspeedtest.engine.SpeedTestEngine

class TestDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "speedtest.db"
        private const val DB_VERSION = 1
        private const val TABLE_RESULTS = "results"

        private const val COL_ID = "id"
        private const val COL_PING = "ping_ms"
        private const val COL_JITTER = "jitter_ms"
        private const val COL_LOSS = "loss_percent"
        private const val COL_DOWNLOAD = "download_mbps"
        private const val COL_UPLOAD = "upload_mbps"
        private const val COL_SCORE = "stability_score"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_NETWORK_TYPE = "network_type"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_RESULTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PING REAL,
                $COL_JITTER REAL,
                $COL_LOSS REAL,
                $COL_DOWNLOAD REAL,
                $COL_UPLOAD REAL,
                $COL_SCORE INTEGER,
                $COL_TIMESTAMP INTEGER,
                $COL_NETWORK_TYPE TEXT
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RESULTS")
        onCreate(db)
    }

    fun saveResult(result: SpeedTestEngine.TestResult, networkType: String = "unknown") {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PING, result.pingMs)
            put(COL_JITTER, result.jitterMs)
            put(COL_LOSS, result.packetLossPercent)
            put(COL_DOWNLOAD, result.downloadMbps)
            put(COL_UPLOAD, result.uploadMbps)
            put(COL_SCORE, result.stabilityScore)
            put(COL_TIMESTAMP, result.timestamp)
            put(COL_NETWORK_TYPE, networkType)
        }
        db.insert(TABLE_RESULTS, null, values)
    }

    fun getRecentResults(limit: Int = 50): List<SpeedTestEngine.TestResult> {
        val db = readableDatabase
        val results = mutableListOf<SpeedTestEngine.TestResult>()
        val cursor = db.query(
            TABLE_RESULTS, null, null, null, null, null,
            "$COL_TIMESTAMP DESC", limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    SpeedTestEngine.TestResult(
                        pingMs = it.getDouble(it.getColumnIndexOrThrow(COL_PING)),
                        jitterMs = it.getDouble(it.getColumnIndexOrThrow(COL_JITTER)),
                        packetLossPercent = it.getDouble(it.getColumnIndexOrThrow(COL_LOSS)),
                        downloadMbps = it.getDouble(it.getColumnIndexOrThrow(COL_DOWNLOAD)),
                        uploadMbps = it.getDouble(it.getColumnIndexOrThrow(COL_UPLOAD)),
                        stabilityScore = it.getInt(it.getColumnIndexOrThrow(COL_SCORE)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                    )
                )
            }
        }
        return results
    }

    fun getResultCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_RESULTS", null)
        cursor.use {
            it.moveToFirst()
            return it.getInt(0)
        }
    }

    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_RESULTS, null, null)
    }
}
