package com.starfall.gsadrive.data

import com.starfall.gsadrive.tr

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Keep API diagnostics without exposing response bodies, URLs or credentials. */
internal fun driveLoadError(failure: Throwable): String {
    val causes = generateSequence(failure) { it.cause }.take(12).toList()
    val http = causes.filterIsInstance<HttpResponseException>().firstOrNull()
    if (http != null) {
        val reasons = (http as? GoogleJsonResponseException)?.details?.errors.orEmpty()
            .mapNotNull { it.reason?.takeIf { reason -> reason.matches(Regex("[A-Za-z0-9_.-]{1,80}")) } }
            .distinct()
        val detail = when {
            reasons.any { it in setOf("accessNotConfigured", "serviceDisabled") } ->
                tr("The Google Drive API is not enabled for your app's project.")
            reasons.any { it in setOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded") } || http.statusCode == 429 ->
                tr("Google Drive is limiting the number of visits. Please try again later.")
            http.statusCode == 401 -> tr("Google refuses access token. Please re-authorize your account.")
            http.statusCode == 403 -> tr("Google denies access to Drive. Need to check app permissions and account policies.")
            http.statusCode == 404 -> tr("The folder no longer exists or the account doesn't have access rights.")
            http.statusCode >= 500 -> tr("The Google Drive server is experiencing an error. Please try again later.")
            else -> tr("Request to download Drive file list failed.")
        }
        return "$detail (HTTP ${http.statusCode}${if (reasons.isEmpty()) "" else ": ${reasons.joinToString() }"})"
    }
    return when {
        causes.any { it is UnknownHostException } -> tr("Google Drive address cannot be resolved. Please check your network connection or DNS.")
        causes.any { it is SocketTimeoutException } -> tr("Google Drive connection timed out. Let's try something new.")
        causes.any { it is SSLException } -> tr("Unable to establish a secure connection to Google Drive.")
        causes.any { it is LinkageError } -> tr("Google library compatibility error (${causes.first { it is LinkageError }.javaClass.simpleName}).")
        else -> tr("Unable to load Drive file list (${failure.javaClass.simpleName}).")
    }
}
