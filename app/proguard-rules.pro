# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Baseline Profile Generation - Keep profile installer classes
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**

# Keep baseline profile classes
-keep class androidx.startup.** { *; }
-dontwarn androidx.startup.**

# Rules for Mozilla Rhino, a dependency of NewPipeExtractor
# Rhino has references to java.beans and javax.script which don't exist on Android
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.engine.**

# JDK 9+ dynamic linker classes - don't exist on Android
-dontwarn jdk.dynalink.**

# Keep Rhino classes needed by NewPipeExtractor
-keep class org.mozilla.javascript.** { *; }

# Ignore missing service classes
-dontwarn META-INF.services.javax.script.ScriptEngineFactory

# Keep NewPipeExtractor classes
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
