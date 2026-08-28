# kotlinx.serialization — keep the generated serializers and companions.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.magicbill.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.magicbill.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# security-crypto pulls Tink, which references error-prone annotations that are not on the classpath.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# BouncyCastle: only Argon2 is used; R8 strips the rest. It references JDK-only classes.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
