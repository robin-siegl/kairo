---
name: billing-reminders
description: Implement or review Kairo billing recurrence, trial deadlines, and notification scheduling.
---

Keep recurrence calculations in Billing.kt. Recompute from the original anchor to avoid drifting after a short month. Treat the last active day as inclusive. Keep trial deadlines separate from first charges. Update the reminder schedule after persistent data changes and check for stale or duplicate notifications.
