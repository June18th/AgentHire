package com.git.hui.jobclaw.core.security;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates that an HTTP URL resolves only to public network addresses.
 * AI-GENERATED
 */
public final class PublicUrlSafety {

    private PublicUrlSafety() {
    }

    public static CheckResult check(String url) {
        try {
            return check(URI.create(url));
        } catch (Exception ex) {
            return new CheckResult(url, false, "Invalid URL format");
        }
    }

    // AIDEV-NOTE: 所有解析地址都必须是公网地址
    public static CheckResult check(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return new CheckResult(String.valueOf(uri), false, "Invalid URL format");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return new CheckResult(uri.getHost(), false, "Only HTTP and HTTPS URLs are allowed");
        }
        if (uri.getUserInfo() != null) {
            return new CheckResult(uri.getHost(), false, "URLs containing user credentials are not allowed");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            return new CheckResult(host, false, "Local host names are not allowed");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    return new CheckResult(host, false, "Private, local, or reserved addresses are not allowed");
                }
            }
            return new CheckResult(host, true, "URL resolves only to public network addresses");
        } catch (UnknownHostException ex) {
            return new CheckResult(host, false, "Host could not be resolved");
        }
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first == 0 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && (third == 0 || third == 2))
                    || (first == 198 && (second == 18 || second == 19 || second == 51 && third == 100))
                    || (first == 203 && second == 0 && third == 113);
        }
        return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
    }

    public record CheckResult(String host, boolean canAccess, String reason) {
    }
}
