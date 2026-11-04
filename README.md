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
