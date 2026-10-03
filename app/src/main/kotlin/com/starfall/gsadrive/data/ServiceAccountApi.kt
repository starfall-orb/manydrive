package com.starfall.gsadrive.data

import com.starfall.gsadrive.tr

import org.json.JSONObject
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64

class ServiceAccountCredentials private constructor(
    val email: String,
    private val privateKeyPem: String,
    private val privateKey: RSAPrivateKey,
    private val keyId: String
) {
    val id: String get() = email

    fun toJson(): JSONObject = JSONObject().put("type", "service_account")
        .put("client_email", email).put("private_key", privateKeyPem)
        .put("private_key_id", keyId).put("token_uri", TOKEN_URI)

    internal fun sdkCredentials(): com.google.auth.oauth2.ServiceAccountCredentials =
        com.google.auth.oauth2.ServiceAccountCredentials.newBuilder()
            .setClientEmail(email)
            .setPrivateKey(privateKey)
            .apply { if (keyId.isNotBlank()) setPrivateKeyId(keyId) }
            .setTokenServerUri(java.net.URI(TOKEN_URI))
            .setScopes(listOf("https://www.googleapis.com/auth/drive"))
            .build()

    override fun toString(): String = "ServiceAccountCredentials($email)"

    companion object {
        const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        const val MAX_JSON_BYTES = 128 * 1024

        fun parse(text: String): ServiceAccountCredentials {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { tr("JSON file is too large.") }
            val json = try { JSONObject(text) } catch (_: Exception) {
                throw IllegalArgumentException(tr("File is not valid JSON."))
            }
            require(json.optString("type") == "service_account") { tr("Need a JSON key file of type service_account.") }
            val email = json.optString("client_email").trim()
            require(email.matches(Regex("[^\\s@]+@[^\\s@]+\\.gserviceaccount\\.com"))) { tr("JSON is missing a valid client_email.") }
            require(json.optString("token_uri", TOKEN_URI) == TOKEN_URI) { tr("token_uri is not Google's OAuth server.") }
            val pem = json.optString("private_key").trim()
            require(pem.startsWith("-----BEGIN PRIVATE KEY-----") && pem.endsWith("-----END PRIVATE KEY-----")) {
                tr("JSON is missing PKCS#8 format private_key.")
            }
            val key = try {
                val encoded = pem.removePrefix("-----BEGIN PRIVATE KEY-----").removeSuffix("-----END PRIVATE KEY-----")
                    .replace(Regex("\\s"), "")
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.Default.decode(encoded))) as RSAPrivateKey
            } catch (_: Exception) { throw IllegalArgumentException(tr("The RSA key in JSON is invalid.")) }
            require(key.modulus.bitLength() >= 2048) { tr("RSA keys must be at least 2048 bits.") }
            return ServiceAccountCredentials(email, pem, key, json.optString("private_key_id"))
        }
    }
}

class ServiceAccessToken(val value: String, val expiresAtSeconds: Long) {
    fun validAt(nowSeconds: Long) = nowSeconds < expiresAtSeconds - 60
}

object ServiceAccountApi {
    fun accessToken(credentials: ServiceAccountCredentials): ServiceAccessToken {
        val token = credentials.sdkCredentials().refreshAccessToken()
        val expiration = requireNotNull(token.expirationTime) { tr("Google does not return token expiration.") }
        check(token.tokenValue.isNotBlank()) {
            tr("Google did not return a valid access token.")
        }
        return ServiceAccessToken(token.tokenValue, expiration.time / 1000)
    }
}
