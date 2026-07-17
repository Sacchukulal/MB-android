# Modern AndroidX / Kotlin libraries ship consumer ProGuard rules; only
# project-specific keeps belong here.

# kotlinx.serialization — keep serializers for our own @Serializable models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.magicbill.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.magicbill.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces resolved via reflection on generic signatures.
-keepattributes Signature

# Tink (via androidx.security-crypto) references compile-only ErrorProne
# annotations that aren't on the runtime classpath.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
