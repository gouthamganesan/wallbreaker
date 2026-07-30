package dev.goutham.wallbreaker

import android.content.Context

/** How a given share will be delivered to Instapaper. */
enum class Route {
    /** Simple API add of the URL as-is. */
    SIMPLE_LINK,

    /** Simple API add of the Freedium-wrapped URL (Full API not configured). */
    FREEDIUM_WRAP,

    /** Full API: fetch the Freedium HTML and upload it under the original URL. */
    FREEDIUM_CONTENT,

    /** Full API: upload a shared raw-HTML file's content under its source URL. */
    HTML_CONTENT,
}

/** Lifecycle of a saved receipt. */
enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED }

/** What the user shared. */
sealed interface SharePayload {
    data class Link(val url: String) : SharePayload
    data class Html(val html: String) : SharePayload
}

/** A fully-resolved intent to save, ready to persist + enqueue. */
data class PlannedSave(
    val url: String,
    val title: String?,
    val host: String?,
    val route: Route,
    /** Present only for [Route.HTML_CONTENT] — persisted to a file by the repo. */
    val htmlContent: String?,
)

/** Outcome of routing a payload. */
sealed interface RouteResult {
    data class Ready(val save: PlannedSave) : RouteResult

    /** A raw-HTML file was shared but the Full API isn't configured. */
    data object NeedsFullApi : RouteResult

    /** No usable URL / empty HTML. */
    data object Unusable : RouteResult
}

/**
 * The one place that decides, per shared item, which Instapaper path to use.
 * Pure w.r.t. the network — it only reads settings + whether the Full API app is
 * configured; the actual fetch/upload happens later in the sync worker.
 */
object SendRouter {

    fun plan(context: Context, payload: SharePayload): RouteResult = when (payload) {
        is SharePayload.Link -> planLink(context, payload.url)
        is SharePayload.Html -> planHtml(context, payload.html)
    }

    private fun planLink(context: Context, url: String): RouteResult {
        val settings = AppSettingsStore.load(context)
        val routed = Freedium.shouldRoute(url, settings)
        val fullApi = CredentialStore.hasConsumerApp(context)
        val route = when {
            routed && fullApi -> Route.FREEDIUM_CONTENT
            routed -> Route.FREEDIUM_WRAP
            else -> Route.SIMPLE_LINK
        }
        WbLog.i("route ${UrlExtractor.host(url)} -> $route (freedium=$routed fullApi=$fullApi)")
        return RouteResult.Ready(
            PlannedSave(
                url = url,
                title = null,               // Instapaper fills the title from the crawl
                host = UrlExtractor.host(url),
                route = route,
                htmlContent = null,
            ),
        )
    }

    private fun planHtml(context: Context, html: String): RouteResult {
        if (html.isBlank()) return RouteResult.Unusable
        if (!CredentialStore.hasConsumerApp(context)) return RouteResult.NeedsFullApi

        val url = HtmlMeta.canonicalUrl(html) ?: syntheticUrl(html)
        return RouteResult.Ready(
            PlannedSave(
                url = url,
                title = HtmlMeta.title(html),
                host = UrlExtractor.host(url),
                route = Route.HTML_CONTENT,
                htmlContent = html,
            ),
        )
    }

    /** A stable placeholder URL when a shared HTML file has no canonical link. */
    private fun syntheticUrl(html: String): String {
        val hex = Integer.toHexString(html.hashCode()).removePrefix("-")
        return "https://wallbreaker.local/doc-$hex"
    }
}
