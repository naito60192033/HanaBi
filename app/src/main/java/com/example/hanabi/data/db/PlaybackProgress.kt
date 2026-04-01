package com.example.hanabi.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 各動画の再生位置を保存するエンティティ */
@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey
    val smbPath: String,          // SMB URLをキーとして使用
    val positionMs: Long,         // 再生位置（ミリ秒）
    val durationMs: Long,         // 動画の長さ（ミリ秒）
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 90%以上視聴済みかどうか */
    val isFinished: Boolean
        get() = durationMs > 0 && positionMs.toDouble() / durationMs >= 0.9
}
