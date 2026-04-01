# jcifs-ng SMBクライアント
-keep class jcifs.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
