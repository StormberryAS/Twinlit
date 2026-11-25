package no.stormberry.twinlit.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import no.stormberry.twinlit.R

/**
 * The colours of the footer, from the tab it ends: [Sun] on the Sun tab, [Moon] on the Moon tab
 * (SPEC 10.1: one shared component, each in its tab palette). Plain values, so a test can check the
 * contrast of every pair without a composition.
 */
data class FooterColours(
    val background: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /**
     * The small print: the legal line, the three closing notes and the icon row. The Sun tab sets all its
     * lines in [textSecondary]; MoonApp set its body lines in the lighter TextSoft and these in
     * TextMutedReadable, and the Moon tab keeps that split (fix round 2).
     */
    val smallPrint: Color = textSecondary,
    /** Links in the legal and credit lines: underlined as well, so colour is never the only mark. */
    val link: Color,
)

/**
 * The foot of the Sun tab and of the Moon tab, laid out like the footer of CheckLink (and of the
 * stormberry.as Labs apps), SunApp 2.0.0's `AppFooter` made one shared component: the Stormberry
 * wordmark and tagline, what runs on the device and who is credited for it (SunCalc, GeoNames and
 * Kartverket, L-4), a rule, the icon row, the privacy line, the permissions line, the legal line,
 * and a closing line. Not on the Alarms tab (D22). The last-update line of SunApp and MoonApp is
 * dropped: it announced Twinlit inside them.
 *
 * Every address it opens is in [FooterTargets]. They are the same addresses as in CheckLink's
 * footer, except Privacy and Disclaimer, and the five addresses of the credit sentence.
 * Disclaimer is Twinlit's own page (D21), which must be live before release (D44).
 *
 * ### No INTERNET permission, still
 *
 * Opening a link fires an ACTION_VIEW intent and the browser (or the Nostr client) does the
 * fetching, in its own process. Do not "fix" this by adding the permission: the claim in
 * this very footer rests on its absence, and CI diffs the built APK against
 * expected-permissions.txt.
 */
@Composable
fun AppFooter(colours: FooterColours, modifier: Modifier = Modifier) {
    var showLicences by remember { mutableStateOf(false) }
    if (showLicences) LicencesDialog(colours) { showLicences = false }
    val context = LocalContext.current
    val open: (String) -> Unit = { url -> openExternal(context, url) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Opaque, as the web footer's --bg-base is: the footer ends the page rather than
            // floating on the sky glow behind the cards.
            .background(colours.background),
    ) {
        HorizontalDivider(color = colours.border)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 32.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                // Brand and the on-device statement. Left-aligned, stacked, 2rem apart: the
                // phone layout of the web footer's wrapped flex row.
                Column(modifier = Modifier.widthIn(max = 320.dp)) {
                    Wordmark(colours, onClick = { open(FooterTargets.SITE) })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Smarter sales. Global culture. AI-driven strategy.",
                        color = colours.textSecondary,
                        fontSize = 15.2.sp,
                        lineHeight = 22.8.sp,
                    )
                }
                Spacer(Modifier.height(32.dp))
                // Credit where it is due, in the footer itself: every third-party part the
                // app ships, with a link to its source and one to its licence.
                Text(
                    text = creditLine(colours, open),
                    color = colours.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.4.sp,
                    modifier = Modifier.widthIn(max = 320.dp),
                )

                Spacer(Modifier.height(48.dp))
                HorizontalDivider(color = colours.border)
                Spacer(Modifier.height(32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SocialRow(colours, open)

                    Text(
                        text = FooterLines.PRIVACY_LINE,
                        color = colours.textSecondary,
                        fontSize = 14.4.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = FooterLines.PERMISSIONS_LINE,
                        color = colours.textSecondary,
                        // 13.6 sp, as the legal lines: at 14.4 sp the last word wrapped alone.
                        fontSize = 13.6.sp,
                        lineHeight = 21.8.sp,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = legalLine(colours, open) { showLicences = true },
                        color = colours.smallPrint,
                        fontSize = 13.6.sp,
                        lineHeight = 21.8.sp,
                        textAlign = TextAlign.Center,
                    )

                    // Kept at 0.85 opacity as on the web; FooterTest checks it stays above 4.5:1 in every palette.
                    FooterNote(colours, "Privacy First: We do not collect cookies or personal data.")
                    FooterNote(colours, "City list dated $CITY_LIST_DATE")
                    FooterNote(colours, "Proudly powered by sovereign AI agents.")
                }
            }
        }
    }
}

/**
 * The privacy and permission lines of the app (TEXTS.md L-7, L-8). The one footer serves both
 * editions: both have the alarms and declare the same nine permissions for them, so both show
 * these two lines.
 */
internal object FooterLines {
    const val PRIVACY_LINE = "Your places and alarms never leave this device. Disconnect and try it."
    const val PERMISSIONS_LINE = "Alarm permissions only. No internet. No tracking."
}

/** The date of the bundled city catalogue (data/cities.tsv), as the footer states it. */
const val CITY_LIST_DATE = "2026-08-21"

@Composable
private fun FooterNote(colours: FooterColours, text: String) {
    Text(
        text = text,
        color = colours.smallPrint.copy(alpha = 0.85f),
        fontSize = 13.6.sp,
        lineHeight = 21.8.sp,
        textAlign = TextAlign.Center,
    )
}

/** "Stormberry" with the brand's red full stop; a link to stormberry.as. */
@Composable
private fun Wordmark(colours: FooterColours, onClick: () -> Unit) {
    Text(
        text = buildAnnotatedString {
            append("Stormberry")
            withStyle(SpanStyle(color = STORMBERRY_RED)) { append(".") }
        },
        color = colours.textPrimary,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = "Stormberry AS website" }
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * The middle of the web gradient on the footer dot (#E21E26 to #FF4D54). A one-glyph gradient
 * would only ever show one end of itself.
 */
private val STORMBERRY_RED = Color(0xFFF03840)

@Composable
private fun SocialRow(colours: FooterColours, open: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialIcon(colours, FooterIcons.linkedIn, "LinkedIn") { open(FooterTargets.LINKEDIN) }
        SocialIcon(colours, FooterIcons.gitHub, "GitHub") { open(FooterTargets.GITHUB) }
        SocialIcon(colours, FooterIcons.nostr, "Nostr") { open(FooterTargets.NOSTR) }
        SocialIcon(colours, FooterIcons.tor, "Tor onion site") { open(FooterTargets.ONION) }
    }
}

/** A 24 dp mark in a 48 dp target, the smallest size a thumb is promised to hit. */
@Composable
private fun SocialIcon(
    colours: FooterColours,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            // "Open LinkedIn": the spoken click label of MoonApp's footer icons (TEXTS.md L-6).
            .clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colours.smallPrint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * "Runs entirely on your device: our own code, SunCalc's formulae (BSD 2-Clause) and city
 * data from GeoNames and Kartverket (CC BY 4.0). Nothing is sent."
 *
 * Only the third-party projects and data the app's function is built on are credited here,
 * each linked to its source and each licence to its text (Marcos, 2026-10-24). The icons,
 * the programming languages and platform libraries (Kotlin, Jetpack, Material, the Java
 * time backport) and the Inter typeface are not in the footer: NOTICE and the README
 * document them. NOTICE keeps the full notices (BSD's copyright and disclaimer must travel
 * with the code); this is the plain-sentence credit, and it is not a substitute for NOTICE.
 *
 * Each licence label is one unbreakable run: no-break spaces and a non-breaking hyphen, so
 * "BSD 2-Clause" and "CC BY 4.0" never split across two lines.
 */
private fun creditLine(colours: FooterColours, open: (String) -> Unit): AnnotatedString {
    val link = TextLinkStyles(
        SpanStyle(color = colours.link, textDecoration = TextDecoration.Underline),
    )
    fun androidx.compose.ui.text.AnnotatedString.Builder.url(label: String, url: String) {
        withLink(LinkAnnotation.Clickable(tag = url, styles = link) { open(url) }) { append(label) }
    }
    return buildAnnotatedString {
        append("Runs entirely on your device: our own code, ")
        url("SunCalc", FooterTargets.SUNCALC)
        append("'s formulae (")
        url(LICENCE_BSD_2_CLAUSE, FooterTargets.SUNCALC_LICENCE)
        append(") and city data from ")
        url("GeoNames", FooterTargets.GEONAMES)
        append(" and ")
        url("Kartverket", FooterTargets.KARTVERKET)
        append(" (")
        url(LICENCE_CC_BY, FooterTargets.CC_BY)
        append("). Nothing is sent.")
    }
}

/** Licence labels, each on one line: no-break spaces, and a non-breaking hyphen in "2-Clause". */
internal const val LICENCE_BSD_2_CLAUSE = "BSD\u00A02\u2011Clause"
internal const val LICENCE_CC_BY = "CC\u00A0BY\u00A04.0"

/**
 * "© 2026 Stormberry AS · Org.nr. NO 937 751 249 · {registered municipality}, Norway · Privacy · Disclaimer · Licences"
 * (TEXTS.md L-9): SunApp 2.0.0's legal line exactly (2a26f98), with the company's registered
 * municipality (Marcos, 2026-11-21, R5 D5; DECISIONS D19), plus the Licences link (L-19),
 * the links as real links (so a screen reader lists them), in the app's blue and underlined,
 * because colour alone must not be the only thing marking a link. The organisation number
 * uses no-break spaces, as the web footer's nowrap does.
 */
private fun legalLine(colours: FooterColours, open: (String) -> Unit, onLicences: () -> Unit): AnnotatedString {
    val link = TextLinkStyles(
        SpanStyle(color = colours.link, textDecoration = TextDecoration.Underline),
    )
    fun androidx.compose.ui.text.AnnotatedString.Builder.url(label: String, url: String) {
        withLink(LinkAnnotation.Clickable(tag = url, styles = link) { open(url) }) { append(label) }
    }
    return buildAnnotatedString {
        append("© 2026 Stormberry AS · ")
        url("Org.nr. NO 937 751 249", FooterTargets.ORG_REGISTER)
        append(" · Askøy, Norway · ")
        url("Privacy", FooterTargets.PRIVACY)
        append(" · ")
        url("Disclaimer", FooterTargets.DISCLAIMER)
        append(" · ")
        // Not a web address: it opens the NOTICE that travels inside the APK (assets/legal/NOTICE).
        withLink(LinkAnnotation.Clickable(tag = LICENCES_LABEL, styles = link) { onLicences() }) { append(LICENCES_LABEL) }
    }
}

/** TEXTS.md L-19 (approved by Marcos 2026-11-22): the footer link that opens the third-party notices. */
internal const val LICENCES_LABEL = "Licences"

/** Where the packaged NOTICE lives in the APK's assets. */
internal const val NOTICE_ASSET = "legal/NOTICE"

/**
 * The third-party notices, from the packaged NOTICE, in a dialog that scrolls. The SunCalc copyright
 * line and disclaimer (BSD 2-Clause, clause 2) reach the user of a binary here.
 */
@Composable
private fun LicencesDialog(colours: FooterColours, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notice = remember {
        try {
            context.assets.open(NOTICE_ASSET).bufferedReader().use { it.readText() }
        } catch (_: java.io.IOException) {
            "The notices could not be read. They are in the NOTICE file of the source repository."
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colours.background,
        title = { Text(LICENCES_LABEL, color = colours.textPrimary) },
        text = {
            Text(
                text = notice,
                color = colours.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = colours.link) }
        },
    )
}

/**
 * Every address the footer can open. Pure data, so a JVM test can pin the lot: the same
 * targets as CheckLink's footer, and nothing but web addresses and the nostr: scheme.
 */
object FooterTargets {
    const val SITE = "https://stormberry.as"
    const val LINKEDIN = "https://linkedin.com/company/StormberryAS"
    const val GITHUB = "https://github.com/StormberryAS"
    const val NOSTR = "nostr:npub1zz9w77p2jkn95t0st9tcuea8zdn2vknf0ctrwyd4g46w3kswdccqesr0pa"
    const val ONION = "http://stormberryr7fz5xdopi7f265d7id27bgizc6uxeliaepzstompcfpyd.onion"
    const val ORG_REGISTER = "https://virksomhet.brreg.no/nb/oppslag/enheter/937751249"

    // Privacy is the company page; Disclaimer is Twinlit's own, made from DISCLAIMER.md (D21, D44).
    const val PRIVACY = "https://stormberry.as/privacy.html"
    const val DISCLAIMER = "https://twinlit.stormberry.as/disclaimer.html"

    // The credit sentence: each part's source, and each licence's text.
    const val SUNCALC = "https://github.com/mourner/suncalc"
    const val SUNCALC_LICENCE = "https://github.com/mourner/suncalc/blob/master/LICENSE"
    const val GEONAMES = "https://www.geonames.org/"
    const val KARTVERKET = "https://www.kartverket.no/en/api-and-data"
    const val CC_BY = "https://creativecommons.org/licenses/by/4.0/"

    val credits: List<String> = listOf(SUNCALC, SUNCALC_LICENCE, GEONAMES, KARTVERKET, CC_BY)

    val all: List<String> = listOf(
        SITE, LINKEDIN, GITHUB, NOSTR, ONION, ORG_REGISTER, PRIVACY, DISCLAIMER,
    ) + credits
}

/**
 * Hands [url] to whichever app on the device handles it, as a browser would: a web address
 * to a browser, a nostr: address to a Nostr client that accepts links from the web
 * (BROWSABLE). With no such app, or one the system refuses to start, a short message
 * replaces what would otherwise be a crash.
 */
internal fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_app_for_link), Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, context.getString(R.string.no_app_for_link), Toast.LENGTH_SHORT).show()
    }
}
