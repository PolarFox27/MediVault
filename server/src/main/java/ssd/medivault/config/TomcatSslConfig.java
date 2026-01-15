package ssd.medivault.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Programmatic SSL/TLS configuration for Tomcat with mTLS (mutual TLS) support.
 * This configuration enables client certificate authentication for doctors
 * while maintaining HTTPS for all connections.
 * Security: The truststore contains only the Intermediate CA certificate,
 * ensuring only certificates signed by our PKI are accepted.
 */
@Configuration
public class TomcatSslConfig {

    @Value("${ssl.keystore.location}")
    private String keystoreLocation;

    @Value("${ssl.keystore.password}")
    private String keystorePassword;

    @Value("${ssl.keystore.alias}")
    private String keystoreAlias;

    @Value("${ssl.truststore.location}")
    private String truststoreLocation;

    @Value("${ssl.truststore.password}")
    private String truststorePassword;

    @Value("${ssl.client-auth:want}")
    private String clientAuth;

    @Value("${server.port}")
    private int portNumber;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> sslTomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(this::configureSSL);
    }

    private void configureSSL(Connector connector) {
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setPort(portNumber);

        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);

        // Create SSL Host Config
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setHostName("_default_");
        
        // Resolve file paths relative to working directory
        String workDir = System.getProperty("user.dir");
        File keystoreFile = new File(workDir, keystoreLocation);
        File truststoreFile = new File(workDir, truststoreLocation);

        // Keystore configuration (server certificate)
        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
            sslHostConfig, 
            SSLHostConfigCertificate.Type.UNDEFINED
        );
        cert.setCertificateKeystoreFile(keystoreFile.getAbsolutePath());
        cert.setCertificateKeystorePassword(keystorePassword);
        cert.setCertificateKeystoreType("PKCS12");
        cert.setCertificateKeyAlias(keystoreAlias);
        sslHostConfig.addCertificate(cert);

        // Truststore configuration (for client certificate verification)
        sslHostConfig.setTruststoreFile(truststoreFile.getAbsolutePath());
        sslHostConfig.setTruststorePassword(truststorePassword);
        sslHostConfig.setTruststoreType("PKCS12");

        // Client authentication mode
        // "optional" = server requests client cert but doesn't require it (for patients)
        // "required" = server requires client cert (would break patient access)
        if ("want".equalsIgnoreCase(clientAuth)) {
            sslHostConfig.setCertificateVerification("optional");
        } else if ("need".equalsIgnoreCase(clientAuth)) {
            sslHostConfig.setCertificateVerification("required");
        } else {
            sslHostConfig.setCertificateVerification("none");
        }

        // TLS protocol versions (TLS 1.3 preferred, TLS 1.2 as fallback)
        sslHostConfig.setProtocols("TLSv1.3,TLSv1.2");
        
        // Apply the SSL configuration
        protocol.addSslHostConfig(sslHostConfig);
        protocol.setDefaultSSLHostConfigName("_default_");

        System.out.println("[TomcatSslConfig] Configured SSL with:");
        System.out.println("  - Keystore: " + keystoreFile.getAbsolutePath());
        System.out.println("  - Truststore: " + truststoreFile.getAbsolutePath());
        System.out.println("  - Client Auth: " + clientAuth);
    }
}
