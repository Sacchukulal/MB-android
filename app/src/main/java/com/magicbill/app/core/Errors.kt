package com.magicbill.app.core

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Central error → user-message mapping. Every catch block in the app funnels
 * through here so the message always matches what actually went wrong.
 * Never returns a raw exception message.
 */
/** An exception whose message is already user-facing — mappers pass it through. */
class FriendlyException(message: String) : Exception(message)

object MBErrors {

    const val NO_INTERNET =
        "No internet connection. Check your Wi-Fi or mobile data and try again."
    const val TIMEOUT =
        "The connection timed out. Please check your internet and try again."
    const val SERVER_DOWN =
        "Our servers are having issues. Please try again in a few minutes."
    const val SESSION_EXPIRED =
        "Your session has expired. Please log in again."
    const val UNKNOWN =
        "Something went wrong. Please try again. If this continues, contact support."

    /** Errors from Supabase Auth sign-in (owner login). */
    fun signIn(e: Exception): String = when (e) {
        is AuthRestException -> when (e.errorCode) {
            AuthErrorCode.InvalidCredentials ->
                "Wrong email or password. New to Magic Bill? Sign up at magicbill.in"
            AuthErrorCode.EmailNotConfirmed ->
                "Please verify your email first. Check your inbox for a confirmation link."
            AuthErrorCode.UserNotFound ->
                "No account found with this email. Sign up at magicbill.in"
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit ->
                "Too many login attempts. Please wait a few minutes and try again."
            AuthErrorCode.UserBanned ->
                "This account has been suspended. Contact support at magicbill.in"
            else ->
                if (e.error.contains("invalid", ignoreCase = true)) {
                    "Wrong email or password. New to Magic Bill? Sign up at magicbill.in"
                } else {
                    network(e)
                }
        }
        else -> network(e)
    }

    /** Errors from data calls (postgrest, edge functions, plain HTTP). */
    fun network(e: Exception): String = when {
        e is FriendlyException -> e.message ?: UNKNOWN
        e is UnknownHostException -> NO_INTERNET
        e is SocketTimeoutException || e is HttpRequestTimeoutException -> TIMEOUT
        e.message?.contains("timeout", ignoreCase = true) == true -> TIMEOUT
        e is RestException -> when (e.statusCode) {
            401, 403 -> SESSION_EXPIRED
            404 -> "This feature is temporarily unavailable. Please try again later."
            in 500..599 -> SERVER_DOWN
            else -> UNKNOWN
        }
        e is SerializationException -> UNKNOWN
        e is IOException -> NO_INTERNET
        else -> UNKNOWN
    }
}
