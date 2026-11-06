package dev.robinsiegl.kairo

import java.time.LocalDate

internal data class PaymentDraft(
    val name: String,
    val amount: String = "",
    val interval: Interval = Interval.Monthly,
    val firstCharge: String,
    val category: String = "",
    val icon: String = "Payment",
    val notes: String = "",
    val manageLink: String = "",
    val tags: List<String> = emptyList(),
)

// A duplicate is a new draft. History, deadlines, cancellations and reminder consent stay with the original.
internal fun Payment.duplicateDraft(today: LocalDate): PaymentDraft {
    val active = termsOn(today)
    return PaymentDraft(
        name = "$name copy", amount = active.amount, interval = active.interval,
        firstCharge = (nextCharge(today)?.date ?: today).toString(),
        category = category, icon = icon, notes = notes, manageLink = manageLink, tags = tags,
    )
}

internal data class ServicePreset(
    val id: String,
    val name: String,
    val category: String,
    val icon: String,
    val website: String,
    val keywords: String = "",
    val interval: Interval = Interval.Monthly,
) {
    fun draft(today: LocalDate) = PaymentDraft(
        name = name, firstCharge = today.toString(), interval = interval,
        category = category, icon = icon, manageLink = website,
    )
}

// No prices or trial durations: plans vary by region and change over time.
internal val servicePresets = listOf(
    ServicePreset("spotify", "Spotify", "Entertainment", "MUSIC", "https://www.spotify.com/account/", "music audio"),
    ServicePreset("netflix", "Netflix", "Entertainment", "MOVIE", "https://www.netflix.com/YourAccount", "tv video films"),
    ServicePreset("youtube", "YouTube Premium", "Entertainment", "VIDEO", "https://www.youtube.com/paid_memberships", "music video"),
    ServicePreset("disney", "Disney+", "Entertainment", "TV", "https://www.disneyplus.com/account", "films tv"),
    ServicePreset("prime", "Amazon Prime", "Shopping", "SHOPPING", "https://www.amazon.com/amazonprime", "delivery video"),
    ServicePreset("apple", "Apple Music", "Entertainment", "HEADPHONES", "https://music.apple.com/", "songs audio"),
    ServicePreset("dropbox", "Dropbox", "Storage", "CLOUD", "https://www.dropbox.com/account", "files backup cloud"),
    ServicePreset("github", "GitHub", "Work", "CODE", "https://github.com/settings/billing", "coding developer"),
    ServicePreset("bitwarden", "Bitwarden", "Security", "LOCK", "https://vault.bitwarden.com/", "password manager", Interval.Yearly),
    ServicePreset("proton", "Proton Unlimited", "Security", "SERVICE_PROTON", "https://account.proton.me/", "mail vpn drive"),
    ServicePreset("proton_mail", "Proton Mail", "Communication", "SERVICE_PROTON_MAIL", "https://mail.proton.me/", "email privacy"),
    ServicePreset("proton_drive", "Proton Drive", "Storage", "SERVICE_PROTON_DRIVE", "https://drive.proton.me/", "cloud files backup"),
    ServicePreset("proton_vpn", "Proton VPN", "Security", "SERVICE_PROTON_VPN", "https://protonvpn.com/", "privacy network"),
    ServicePreset("rent", "Rent", "Home", "HOME", "", "housing apartment"),
    ServicePreset("internet", "Internet", "Home", "WIFI", "", "broadband wifi"),
    ServicePreset("mobile", "Mobile plan", "Communication", "PHONE_DEVICE", "", "phone sim data"),
    ServicePreset("gym", "Gym membership", "Health", "FITNESS", "", "exercise fitness"),
).sortedBy { it.name }

internal fun matchingServices(query: String): List<ServicePreset> {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return servicePresets.filter { service ->
        words.all { "${service.name} ${service.category} ${service.keywords}".contains(it, ignoreCase = true) }
    }
}
