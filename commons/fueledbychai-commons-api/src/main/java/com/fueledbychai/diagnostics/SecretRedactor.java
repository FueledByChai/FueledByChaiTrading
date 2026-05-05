package com.fueledbychai.diagnostics;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Redacts well-known authentication and signature material from text
 * intended for developer-mode display. Defense in depth, applied at the
 * publishing site so secrets never leave the JVM in plaintext via WireTap.
 */
public final class SecretRedactor {

    private static final String MASK = "***REDACTED***";

    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization",
            "x-api-key",
            "apikey",
            "api-key",
            "x-mbx-apikey",
            "x-bapi-api-key",
            "x-bapi-sign",
            "okx-access-key",
            "okx-access-sign",
            "okx-access-passphrase",
            "x-signature",
            "signature",
            "cookie",
            "set-cookie"
    );

    private static final Pattern JSON_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:apiKey|api_key|secret|secretKey|secret_key|signature|sig|privateKey|private_key|nonce|passphrase|token|accessToken|access_token|password)\"\\s*:\\s*)(\"[^\"]*\"|[0-9a-fA-F]+)"
    );

    private SecretRedactor() {
    }

    public static String redactHeaderValue(String headerName, String value) {
        if (headerName == null) {
            return value;
        }
        if (SENSITIVE_HEADER_NAMES.contains(headerName.toLowerCase())) {
            return MASK;
        }
        return value;
    }

    public static String redactBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        return JSON_FIELD_PATTERN.matcher(body).replaceAll("$1\"" + MASK + "\"");
    }
}
