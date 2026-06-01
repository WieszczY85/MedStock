# Google Play Data safety declaration draft for MedStock

Effective date: 2026-06-01

This document is the repository source of truth for the Play Console Data safety form. It must remain consistent with `docs/privacy_policy.md` and the in-app disclosures in Settings and Account.

## App positioning

MedStock is a home inventory manager for medicinal products. It records user-entered product, stock, expiry, dosage, alert, and reminder data. It does not provide medical advice, recommend medicines, or set dosage.

## Data collection / sharing summary

| Play data type | Collected? | Shared? | Required? | Purpose | Notes |
|---|---:|---:|---:|---|---|
| Health and fitness / Health info | Yes | Yes, only when the user enables Google Drive backup | Product inventory is required for core inventory features; Drive backup is optional | App functionality: inventory, stock, expiry, shortage alerts, reminders, backup/restore | Includes medicinal product names, strength, active substance, stock level, expiry date, user-entered dosage, alert thresholds, reminders, and dose/reminder events. |
| Personal info / Email address or user IDs | Yes | Yes, only through user-selected Google account/Drive APIs | Optional | Account connection, Drive backup/restore, avatar display | Selected Google account email is stored locally and included in backup metadata for same-account restore checks. |
| Photos and videos / Profile picture | Yes, if Google profile returns avatar | No | Optional | Account display | The app may fetch and display the Google account avatar. It is not used for advertising or analytics. |
| App activity / App interactions or preferences | Yes | Yes, only when the user enables Google Drive backup | Optional/required depending on feature | App functionality and preferences | Includes theme mode, backup state, and app feature state needed by the app. |
| Files and docs | Yes, user-created export/backup | Yes, through user-controlled export or optional Drive backup | Optional | Backup/restore and user export | Manual database export is initiated by the user. Drive backup is optional. |
| Device or other IDs | No intentional collection by app code | No | No | Not applicable | The app does not include advertising or analytics SDKs in the current codebase. Google Play services may process data under Google's SDK behavior; review SDK disclosures before release. |
| Location | No | No | No | Not applicable | The manifest does not request location permissions. |
| Contacts | No | No | No | Not applicable | The app uses Google account selection, not contact/address-book collection. |
| Financial info | No | No | No | Not applicable | No payments in the current codebase. |
| Messages | No | No | No | Not applicable | No messaging feature. |
| Audio | No | No | No | Not applicable | No audio recording. |
| Calendar | No | No | No | Not applicable | No calendar access. |

## Security practices to declare

- Data is encrypted in transit: Yes. Google APIs and public catalog downloads use HTTPS endpoints.
- Users can request/delete data: Yes, through in-app deletion, Drive backup disable/disconnect, Android app data clearing, uninstall, and Google account/Drive data controls.
- Data is not sold: Yes.
- Optional data: Google account connection and Google Drive backup are optional.
- Independent security review: Do not declare unless a valid review has been completed.

## Play Console answers that should not be used

- Do not claim that the app collects no user data.
- Do not omit health/medication-related data merely because the app is positioned as inventory management.
- Do not claim end-to-end or user-managed encryption unless a separate encryption layer is implemented.
- Do not claim analytics/ads usage unless those SDKs are added later.

## Release checklist before submission

1. Publish `docs/privacy_policy.md` as a public HTML page and set the URL in Play Console.
2. Verify the Play Console Data safety form matches this document exactly.
3. Re-check dependencies and SDK disclosures before every release.
4. Keep Settings and Account disclosures aligned with the privacy policy.
5. If new analytics, ads, crash reporting, payments, location, sensors, or medical decision features are added, update this declaration before release.
