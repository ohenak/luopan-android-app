# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# Apache Commons Math3 — Levenberg-Marquardt optimizer
-keep class org.apache.commons.math3.fitting.leastsquares.** { *; }
-keep class org.apache.commons.math3.linear.** { *; }
-keep class org.apache.commons.math3.optim.** { *; }
-dontwarn org.apache.commons.math3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
