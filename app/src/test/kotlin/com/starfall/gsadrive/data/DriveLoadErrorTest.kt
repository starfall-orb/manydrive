package com.starfall.gsadrive.data

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class DriveLoadErrorTest {
    @Test fun distinguishesDisabledApiFromPermissionFailure() {
        val details = GoogleJsonError().apply {
            errors = listOf(GoogleJsonError.ErrorInfo().apply { reason = "accessNotConfigured" })
        }
        val error = GoogleJsonResponseException(HttpResponseException.Builder(403, "Forbidden", HttpHeaders()), details)
        val message = driveLoadError(error)
        assertTrue(message.contains("not enabled"))
        assertTrue(message.contains("HTTP 403: accessNotConfigured"))
    }

    @Test fun doesNotExposeHttpBodyOrUrl() {
        val error = HttpResponseException.Builder(401, "Unauthorized", HttpHeaders())
            .setContent("secret-token https://example.invalid/private").build()
        val message = driveLoadError(error)
        assertTrue(message.contains("HTTP 401"))
        assertFalse(message.contains("secret-token"))
        assertFalse(message.contains("example.invalid"))
    }

    @Test fun identifiesWrappedNetworkAndSdkFailures() {
        assertTrue(driveLoadError(IllegalStateException(UnknownHostException("private-url"))).contains("DNS"))
        assertTrue(driveLoadError(NoClassDefFoundError("internal-secret")).contains("NoClassDefFoundError"))
        assertFalse(driveLoadError(NoClassDefFoundError("internal-secret")).contains("internal-secret"))
    }
}
