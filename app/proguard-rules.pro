# Add project specific ProGuard rules here.
-keepclassmembers class com.kairo.assistant.nlu.models.** { *; }
-keep class com.kairo.assistant.nlu.models.IntentType { *; }

# Strip all Log calls from release builds for security and performance
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
