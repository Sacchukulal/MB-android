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
