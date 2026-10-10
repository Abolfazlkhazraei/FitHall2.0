# R8 rules for release builds. Most libraries (kotlinx.serialization, Ktor, OkHttp, Play
# services, Compose) ship their own rules; these cover FitHall's code.

# Everything sent to or from the server, or saved on the device, is a @Serializable class.
# Keep their generated serializers so JSON keeps working after shrinking and obfuscation.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class com.fithall.app.**$$serializer { *; }
-keepclassmembers class com.fithall.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.fithall.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Enum names are stored in saved data and sent to the server.
-keepclassmembers enum com.fithall.app.** { *; }

# Ktor references JVM-only classes that don't exist on Android.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
