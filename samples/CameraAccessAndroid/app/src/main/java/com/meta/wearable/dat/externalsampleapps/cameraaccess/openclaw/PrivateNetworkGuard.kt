package com.meta.wearable.dat.externalsampleapps.cameraaccess.openclaw

import java.net.InetAddress
import java.net.URI

// OpenClaw's gateway is a self-hosted, local-network-only service with no TLS of its own -- the
// manifest allows cleartext traffic app-wide specifically so this can work at all, since Android's
// network security config can only allowlist fixed domains known at build time, not "whatever
// host the user types into Settings" (typically a Tailscale IP or a .local mDNS name). That
// manifest flag can't express "only for private networks", so this is the actual boundary
// instead: refuse to send the gateway's bearer token in the clear to anything that doesn't
// resolve to a private/local address, so a mistyped or malicious public host can't silently
// exfiltrate it.
object PrivateNetworkGuard {
    fun isCleartextHostAllowed(hostWithScheme: String): Boolean {
        val uri = try {
            URI(if ("://" in hostWithScheme) hostWithScheme else "http://$hostWithScheme")
        } catch (e: Exception) {
            return false
        }
        // Already encrypted in transit -- nothing to guard against here.
        if (uri.scheme == "https" || uri.scheme == "wss") return true
        val host = uri.host ?: return false
        return isPrivateOrLocalHost(host)
    }

    private fun isPrivateOrLocalHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".local", ignoreCase = true)) {
            // mDNS/Bonjour names (e.g. "Johns-MacBook-Pro.local") don't resolve through standard
            // DNS -- InetAddress.getByName() would just throw, so trust the naming convention
            // itself rather than attempting resolution.
            return true
        }
        val addr = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return false
        }
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
            // isSiteLocalAddress covers RFC1918 (10/8, 172.16/12, 192.168/16).
            return true
        }
        // Tailscale's CGNAT range (100.64.0.0/10) -- not RFC1918, so isSiteLocalAddress misses it,
        // but it's exactly as private in practice (only reachable over an active Tailscale tunnel).
        val bytes = addr.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 100 && second in 64..127) return true
        }
        return false
    }
}
