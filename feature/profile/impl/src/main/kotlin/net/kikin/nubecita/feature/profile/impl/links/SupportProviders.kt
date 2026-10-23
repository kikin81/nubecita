package net.kikin.nubecita.feature.profile.impl.links

/**
 * A recognized support / payment platform. Links to one render as the
 * emphasized (green) profile-link pill.
 *
 * Ported from the official client's `SUPPORT_PROVIDERS`
 * (`bluesky-social/social-app` → `src/features/profileLinks/providers.ts`) so
 * a profile looks the same in Nubecita as on bsky.app.
 *
 * @property domain registrable domain, or a specific host like
 *   `buy.stripe.com`. Matches the domain itself and its subdomains only, so
 *   `ko-fi.com.example.net` does not count as Ko-fi.
 * @property pathPrefix for providers that share a domain with non-support
 *   pages; the link's path must start with it.
 */
internal data class SupportProvider(
    val name: String,
    val domain: String,
    val pathPrefix: String? = null,
)

/** Adult platforms are excluded, matching the official list. */
internal val SUPPORT_PROVIDERS: List<SupportProvider> =
    listOf(
        SupportProvider("BackerKit", "backerkit.com"),
        SupportProvider("Big Cartel", "bigcartel.com"),
        SupportProvider("Boosty", "boosty.to"),
        SupportProvider("BOOTH", "booth.pm"),
        SupportProvider("Buy Me a Coffee", "buymeacoffee.com"),
        SupportProvider("Cash App", "cash.app"),
        SupportProvider("Chuffed", "chuffed.org", pathPrefix = "/campaign/"),
        SupportProvider("Chuffed", "chuffed.org", pathPrefix = "/project/"),
        SupportProvider("Crowdfundr", "crowdfundr.com"),
        SupportProvider("Donorbox", "donorbox.org"),
        SupportProvider("Etsy", "etsy.com"),
        SupportProvider("Fourthwall", "fourthwall.com"),
        SupportProvider("Givebutter", "givebutter.com"),
        SupportProvider("GiveSendGo", "givesendgo.com"),
        SupportProvider("GoFundMe", "gofundme.com"),
        SupportProvider("Gumroad", "gumroad.com"),
        SupportProvider("Indiegogo", "indiegogo.com"),
        SupportProvider("INPRNT", "inprnt.com", pathPrefix = "/gallery/"),
        SupportProvider("itch.io", "itch.io"),
        SupportProvider("JustGiving", "justgiving.com"),
        SupportProvider("Kickstarter", "kickstarter.com"),
        SupportProvider("Ko-fi", "ko-fi.com"),
        SupportProvider("Liberapay", "liberapay.com"),
        SupportProvider("Open Collective", "opencollective.com"),
        SupportProvider("Patreon", "patreon.com"),
        SupportProvider("PayPal", "paypal.com", pathPrefix = "/paypalme/"),
        SupportProvider("PayPal", "paypal.me"),
        SupportProvider("pixivFANBOX", "fanbox.cc"),
        SupportProvider("Redbubble", "redbubble.com"),
        SupportProvider("Skeb", "skeb.jp"),
        SupportProvider("Stripe", "buy.stripe.com"),
        SupportProvider("Stripe", "donate.stripe.com"),
        SupportProvider("Substack", "substack.com"),
        SupportProvider("Throne", "throne.com"),
        SupportProvider("Venmo", "venmo.com"),
        SupportProvider("VGen", "vgen.co"),
        SupportProvider("Zeffy", "zeffy.com"),
    )

/**
 * The support platform [url] points at, or null. A bare provider homepage
 * (`https://patreon.com/`) never counts — only a specific account or page,
 * on a subdomain (`name.itch.io`) or in the path (`ko-fi.com/name`).
 */
internal fun supportProviderFor(url: String): SupportProvider? {
    val parsed = parseWebUrl(url) ?: return null
    return SUPPORT_PROVIDERS.firstOrNull { provider ->
        hostMatchesDomain(parsed.host, provider.domain) && isAccountUrl(parsed, provider)
    }
}

internal fun isSupportLink(url: String): Boolean = supportProviderFor(url) != null

/** True when [host] is [domain] itself or one of its subdomains. */
internal fun hostMatchesDomain(
    host: String,
    domain: String,
): Boolean = host == domain || host.endsWith(".$domain")

private fun isAccountUrl(
    parsed: WebUrl,
    provider: SupportProvider,
): Boolean {
    val path = parsed.path.lowercase().trimEnd('/')
    provider.pathPrefix?.let { prefix ->
        return path.startsWith(prefix.trimEnd('/') + "/")
    }
    val isAccountSubdomain = parsed.host != provider.domain && parsed.host != "www.${provider.domain}"
    return isAccountSubdomain || path.isNotEmpty()
}
