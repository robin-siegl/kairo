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
    val keywords: String = "",
    val interval: Interval = Interval.Monthly,
) {
    fun draft(today: LocalDate) = PaymentDraft(
        name = name, firstCharge = today.toString(), interval = interval,
        category = category, icon = icon,
    )
}

// No prices or trial durations: plans vary by region and change over time.
internal val servicePresets = listOf(
    ServicePreset("spotify", "Spotify", "Entertainment", "MUSIC", "music audio"),
    ServicePreset("netflix", "Netflix", "Entertainment", "MOVIE", "tv video films"),
    ServicePreset("youtube", "YouTube Premium", "Entertainment", "VIDEO", "music video"),
    ServicePreset("disney", "Disney+", "Entertainment", "TV", "films tv"),
    ServicePreset("prime", "Amazon Prime", "Shopping", "SHOPPING", "delivery video"),
    ServicePreset("apple", "Apple Music", "Entertainment", "HEADPHONES", "songs audio"),
    ServicePreset("dropbox", "Dropbox", "Storage", "CLOUD", "files backup cloud"),
    ServicePreset("github", "GitHub", "Work", "CODE", "coding developer"),
    ServicePreset("bitwarden", "Bitwarden", "Security", "LOCK", "password manager", Interval.Yearly),
    ServicePreset("proton", "Proton Unlimited", "Security", "SHIELD", "mail vpn drive"),
    ServicePreset("proton_mail", "Proton Mail", "Communication", "MAIL", "email privacy"),
    ServicePreset("proton_drive", "Proton Drive", "Storage", "CLOUD", "cloud files backup"),
    ServicePreset("proton_vpn", "Proton VPN", "Security", "VPN", "privacy network"),
    ServicePreset("amazon_music", "Amazon Music", "Entertainment", "MUSIC", "songs audio"),
    ServicePreset("audible", "Audible", "Entertainment", "BOOK", "audiobooks reading"),
    ServicePreset("apple_tv", "Apple TV+", "Entertainment", "TV", "films video"),
    ServicePreset("icloud", "iCloud+", "Storage", "CLOUD", "apple cloud photos backup"),
    ServicePreset("google_one", "Google One", "Storage", "CLOUD", "google drive photos backup"),
    ServicePreset("microsoft_365", "Microsoft 365", "Work", "COMPUTER", "office word excel outlook"),
    ServicePreset("onedrive", "OneDrive", "Storage", "CLOUD", "microsoft files backup"),
    ServicePreset("deezer", "Deezer", "Entertainment", "MUSIC", "songs audio"),
    ServicePreset("tidal", "TIDAL", "Entertainment", "HEADPHONES", "music audio"),
    ServicePreset("crunchyroll", "Crunchyroll", "Entertainment", "TV", "anime streaming"),
    ServicePreset("sky_x", "Sky X", "Entertainment", "TV", "austria sports films"),
    ServicePreset("wow", "WOW", "Entertainment", "TV", "sky streaming sports"),
    ServicePreset("dazn", "DAZN", "Entertainment", "TV", "sports football streaming"),
    ServicePreset("playstation", "PlayStation Plus", "Gaming", "GAMING", "sony games ps5"),
    ServicePreset("xbox", "Xbox Game Pass", "Gaming", "GAMING", "microsoft games pc"),
    ServicePreset("nintendo", "Nintendo Switch Online", "Gaming", "GAMING", "games switch"),
    ServicePreset("geforce", "GeForce NOW", "Gaming", "GAMING", "nvidia cloud games"),
    ServicePreset("discord", "Discord Nitro", "Communication", "MESSAGES", "chat community"),
    ServicePreset("twitch", "Twitch", "Entertainment", "VIDEO", "live gaming streams"),
    ServicePreset("patreon", "Patreon", "Entertainment", "HEART", "creators membership"),
    ServicePreset("adobe", "Adobe Creative Cloud", "Work", "PALETTE", "photoshop illustrator design"),
    ServicePreset("canva", "Canva", "Work", "PALETTE", "design graphics"),
    ServicePreset("figma", "Figma", "Work", "PALETTE", "design ui prototype"),
    ServicePreset("notion", "Notion", "Work", "NOTES", "notes workspace"),
    ServicePreset("obsidian", "Obsidian Sync", "Work", "NOTES", "notes sync backup"),
    ServicePreset("todoist", "Todoist", "Work", "CALENDAR", "tasks planning"),
    ServicePreset("1password", "1Password", "Security", "LOCK", "password manager"),
    ServicePreset("nordvpn", "NordVPN", "Security", "VPN", "privacy network"),
    ServicePreset("surfshark", "Surfshark", "Security", "VPN", "privacy network"),
    ServicePreset("mullvad", "Mullvad VPN", "Security", "VPN", "privacy network"),
    ServicePreset("a1", "A1", "Communication", "PHONE_DEVICE", "austria mobile internet broadband"),
    ServicePreset("magenta", "Magenta", "Communication", "PHONE_DEVICE", "austria mobile internet broadband"),
    ServicePreset("drei", "Drei", "Communication", "PHONE_DEVICE", "austria mobile internet broadband"),
    ServicePreset("spusu", "spusu", "Communication", "PHONE_DEVICE", "austria mobile phone"),
    ServicePreset("hot", "HoT", "Communication", "PHONE_DEVICE", "austria mobile phone hofer"),
    ServicePreset("rent", "Rent", "Home", "HOME", "housing apartment"),
    ServicePreset("internet", "Internet", "Home", "WIFI", "broadband wifi"),
    ServicePreset("mobile", "Mobile plan", "Communication", "PHONE_DEVICE", "phone sim data"),
    ServicePreset("gym", "Gym membership", "Health", "FITNESS", "exercise fitness"),
).sortedBy { it.name }

internal fun matchingServices(query: String): List<ServicePreset> {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return servicePresets.filter { service ->
        words.all { "${service.name} ${service.category} ${service.keywords}".contains(it, ignoreCase = true) }
    }
}
