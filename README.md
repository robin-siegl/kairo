# Kairo

Kairo is a local-first Android tracker for recurring payments and free trials. See upcoming payments, review renewals, and remember cancellation deadlines.

- Add payments manually with a billing schedule, currency, category and icon.
- See the next 14 days of payments on Overview or browse the month calendar.
- Set trial reminders and opt in to reminders for individual renewals.
- Use Android system backup or export and merge a backup file.

Kairo has no accounts, bank connections, or network permission. Provider links open in your browser.

## Build

Use JDK 17 and Android SDK 36. Run `./gradlew :app:assembleDebug` (or `gradlew.bat :app:assembleDebug` on Windows). The **Checks** workflow also produces a `kairo-dev` APK artifact.

Production APKs are distributed through GitHub Releases. See [Releasing](docs/RELEASING.md) for signing and versioning.

## License

Kairo source code is MIT licensed. See [third-party icons](docs/THIRD_PARTY.md) for asset terms.
