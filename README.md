<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/wordmark-dark.svg">
    <img src="branding/wordmark-light.svg" alt="Kairo. Recurring payments, in view." width="600">
  </picture>
</p>

# Kairo

Kairo is a local-first Android tracker for recurring payments and free trials. See upcoming payments, review renewals, and remember cancellation deadlines.

- Add payments manually with a billing schedule, category and icon. Choose one currency for all payments in Settings.
- See the next 14 days of payments on Overview or browse day, month and year calendar totals.
- Set trial reminders and opt in to reminders for individual renewals.
- Use Android system backup or export and merge a backup file.

Kairo has no accounts or bank connections. Production builds contact GitHub when you tap Check for updates. Provider links open in your browser.

## Faster entry and appearance

Start a payment from a searchable service preset or choose Add a custom payment. More than 50 presets fill the name, category and icon; enter your own price and confirm the charge date. They do not fetch prices, sign you in, or connect to a provider.

Duplicate payment on the detail page opens an editable draft. It keeps the current amount, interval, category, icon, notes, tags and link, and starts at the next charge or today. The original history, trial deadlines, end dates, cancellation state and reminder opt-in are not copied. Nothing is added until you save.

Settings offers system, light and dark themes, plus optional wallpaper colors on Android 12 and later. Kairo blue stays the default. Appearance settings are included in backups. Widgets retain their own system-based appearance.

All provider presets use the standard Material Symbols icon catalog.

## Reminders and price changes

Enable a second trial reminder in Settings for an additional alert one day before the cancellation deadline or first charge. A one-day primary lead already covers this alert. Test trial and renewal notification channels from Settings.

Notifications offer “Remind me tomorrow” while another reminder still fits before the renewal or within an explicit cancellation deadline. Snoozes use your configured local reminder time and survive app restarts and phone reboots. Android schedules delivery inexactly, so keep the deadline in view in Kairo.

Payment details supports future price changes and a price history. Changes apply to charges on or after the effective date and preserve the original recurrence anchor. Scheduled changes can be removed from Price history. Editing a payment preserves future price dates and applies the updated billing schedule to them.

## Insights and categories

Insights shows scheduled charges for a selected month, a monthly chart for that calendar year, and a forecast from today through the next 12 months. The monthly average is that rolling forecast divided by 12, including annual and one-time charges.

Manage categories from Payments or Insights. Create categories before assigning payments, choose a color, rename them across all matching payments, or merge them into another category. Removing a category makes its payments uncategorized. Category pages include ended payments and show the current month’s scheduled total.

Existing categories are discovered automatically from older payment data. Exports include category definitions and colors. Import merges new categories and preserves existing local colors for matching names.

## Home screen widgets

Add Upcoming payments, Trial deadlines, Next payment, or Month summary from your launcher’s widget picker. Resize Upcoming payments for a compact next-payment card or a list. Tap a payment to open its details, or the month summary to open Calendar.

Each widget has its own settings for hiding amounts and using wallpaper colors on Android 12 and newer. Upcoming payments supports 7-day and 14-day periods. Colors follow the phone’s light/dark mode. Widgets refresh after payment changes, imports, resizing, and system date/time events, with a periodic refresh as a fallback.

## Build

Use JDK 17 and Android SDK 36. Run `./gradlew :app:assembleDebug` (or `gradlew.bat :app:assembleDebug` on Windows). The **Checks** workflow also produces a `kairo-dev` APK artifact.

Production APKs are distributed through GitHub Releases. See [Releasing](docs/RELEASING.md) for signing and versioning.

## Branding

The [branding kit](branding/README.md) includes the logo, wordmarks, launcher previews, and SVG and PNG exports.

## License

Kairo source code is MIT licensed. See [third-party icons](docs/THIRD_PARTY.md) for asset terms.
