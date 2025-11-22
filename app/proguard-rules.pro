# Add project specific ProGuard rules here.

# --- Android & Compose Defaults ---
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontwarn androidx.compose.**

# --- Room Database ---
# Prevent R8 from stripping Room entities and DAOs
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep your specific Entities and DAOs
-keep class com.xalies.tiktapremote.Profile { *; }
-keep class com.xalies.tiktapremote.data.ProfileDao { *; }
-keep class com.xalies.tiktapremote.data.AppDatabase { *; }

# --- Gson Serialization ---
# If you use Gson to save gestures/actions, you must keep the model classes
# so their field names aren't obfuscated (which would break loading JSON).
-keep class com.xalies.tiktapremote.Action { *; }
-keep class com.xalies.tiktapremote.SerializablePath { *; }
-keep class com.xalies.tiktapremote.Point { *; }
-keep class com.xalies.tiktapremote.TriggerType { *; }
-keep class com.xalies.tiktapremote.ActionType { *; }

# --- Enumerations ---
# Specifically needed for .valueOf() calls
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- AdMob ---
-keep class com.google.android.gms.ads.** { *; }