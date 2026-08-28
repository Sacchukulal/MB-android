package com.magicbill.app.core

import java.util.UUID

/**
 * What a call to the cloud or the counter comes back as. Four shapes and no fifth:
 * it worked; it was refused in a sentence a person reads; the other side could not be reached
 * (keep what is cached, say so once); or this phone is no longer signed in / paired there.
 */
sealed interface Answer<out T> {
    data class Ok<T>(val value: T) : Answer<T>
    data class Refused(val sentence: String, val code: String? = null, val retryAfterSeconds: Int? = null) : Answer<Nothing>
    data class Unreachable(val sentence: String) : Answer<Nothing>
    data class SignedOut(val sentence: String) : Answer<Nothing>

    val sentenceOrNull: String?
        get() = when (this) {
            is Ok -> null
            is Refused -> sentence
            is Unreachable -> sentence
            is SignedOut -> sentence
        }
}

inline fun <T, R> Answer<T>.map(f: (T) -> R): Answer<R> = when (this) {
    is Answer.Ok -> Answer.Ok(f(value))
    is Answer.Refused -> this
    is Answer.Unreachable -> this
    is Answer.SignedOut -> this
}

fun <T> Answer<T>.valueOrNull(): T? = (this as? Answer.Ok)?.value

object Sentences {
    const val CLOUD_UNREACHABLE = "Could not reach Magic Bill. Showing what this phone has."
    const val COUNTER_UNREACHABLE = "Could not reach the counter. Is this phone on the shop's WiFi?"
    const val NOT_SIGNED_IN = "Sign in to see this."
    const val SIGN_IN_ENDED = "Your sign-in has ended. Sign in again."
    const val NOT_PAIRED = "This phone is not connected to a counter yet."
}

fun newId(): String = UUID.randomUUID().toString()
