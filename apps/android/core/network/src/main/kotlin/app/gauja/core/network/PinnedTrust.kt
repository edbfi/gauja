// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import app.gauja.core.model.servers.TlsMode
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

internal fun configureTrust(builder: OkHttpClient.Builder, mode: TlsMode) {
    if (mode !is TlsMode.Pinned) return
    val trust =
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client certificates are unsupported")
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("Missing certificate")
                leaf.checkValidity()
                val fingerprint =
                    MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") {
                        "%02x".format(it)
                    }
                if (fingerprint != mode.fingerprint.hex)
                    throw CertificateException("Certificate pin mismatch")
            }
        }
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf(trust), null)
    // OkHttp's default hostname verifier remains active, even for explicitly pinned certificates.
    builder.sslSocketFactory(context.socketFactory, trust)
}
