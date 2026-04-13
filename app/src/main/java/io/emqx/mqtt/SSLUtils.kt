package io.emqx.mqtt

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.security.KeyPair
import java.security.KeyStore
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SSLUtils {
    @Throws(Exception::class)
    fun getSingleSocketFactory(caCrtFileInputStream: InputStream?): SSLSocketFactory {
        Security.addProvider(BouncyCastleProvider())

        val certificateFactory = CertificateFactory.getInstance("X.509")
        val caCerts = mutableListOf<X509Certificate>()

        val bis = BufferedInputStream(caCrtFileInputStream)
        try {
            while (bis.available() > 0) {
                val cert = certificateFactory.generateCertificate(bis) as X509Certificate
                caCerts.add(cert)
            }
        } finally {
            bis.close()
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        for ((index, cert) in caCerts.withIndex()) {
            keyStore.setCertificateEntry("ca-$index", cert)
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, trustManagerFactory.trustManagers, null)

        return sslContext.socketFactory
    }

    @Throws(Exception::class)
    fun getInsecureSocketFactory(): SSLSocketFactory {
        Security.addProvider(BouncyCastleProvider())

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), java.security.SecureRandom())

        return sslContext.socketFactory
    }

    @Throws(Exception::class)
    fun getSocketFactory(
        caCrtFile: InputStream?, crtFile: InputStream?, keyFile: InputStream?,
        password: String
    ): SSLSocketFactory {
        Security.addProvider(BouncyCastleProvider())

        val certificateFactory = CertificateFactory.getInstance("X.509")
        var caCert: X509Certificate? = null

        var bis = BufferedInputStream(caCrtFile)
        try {
            while (bis.available() > 0) {
                caCert = certificateFactory.generateCertificate(bis) as X509Certificate
            }
        } finally {
            bis.close()
        }

        bis = BufferedInputStream(crtFile)
        var cert: X509Certificate? = null
        try {
            while (bis.available() > 0) {
                cert = certificateFactory.generateCertificate(bis) as X509Certificate
            }
        } finally {
            bis.close()
        }

        val pemParser = PEMParser(InputStreamReader(keyFile))
        val `object`: Any = pemParser.readObject()
        val converter: JcaPEMKeyConverter = JcaPEMKeyConverter().setProvider("BC")
        val key: KeyPair = converter.getKeyPair(`object` as PEMKeyPair)
        pemParser.close()

        val caKs = KeyStore.getInstance(KeyStore.getDefaultType())
        caKs.load(null, null)
        caKs.setCertificateEntry("ca-certificate", caCert)
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(caKs)
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setCertificateEntry("certificate", cert)
        ks.setKeyEntry(
            "private-cert",
            key.private,
            password.toCharArray(),
            arrayOf<Certificate>(cert!!)
        )
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, password.toCharArray())
        val context = SSLContext.getInstance("TLSv1.2")
        context.init(kmf.keyManagers, tmf.trustManagers, null)
        return context.socketFactory
    }
}
