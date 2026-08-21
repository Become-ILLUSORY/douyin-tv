# DouyinTV ProGuard rules

# Keep WebView JavaScript interface
-keepclassmembers class com.douyin.tv.JavaScriptInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all activity classes
-keep class com.douyin.tv.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# WebKit
-keep class android.webkit.** { *; }
