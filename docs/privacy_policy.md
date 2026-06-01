# MedStock Privacy Policy

Effective date: 2026-06-01

MedStock is a home inventory manager for medicinal products. The app helps users record medicinal products kept at home, track stock levels, expiry dates, and shortage alerts, and restore data through an optional Google Drive backup. MedStock does not provide medical advice, does not recommend medicines, and does not set or verify dosage.

Developer / publisher: WieszczY (SyntaxDevTeam)
Contact for privacy requests: https://github.com/SyntaxDevTeam/MedStock_One/issues

> Before publishing to Google Play, publish this policy as a public, non-geofenced HTML page and enter that URL in Play Console.

## Data processed by the app

MedStock may process the following data when the user enters it or enables the relevant feature:

- Medicinal product inventory: product name, strength, active substance, package information, unit, current stock, expiry dates, alert thresholds, and notes/labels shown by the app.
- User-entered dosage and reminder data: dosage values entered by the user, reminder time, enabled days, reminder label, selected sound name, linked product IDs, and dose/reminder events used by the app.
- Google account data: selected Google account email address and optional account avatar URL/image display when the user connects a Google account.
- App preferences: theme mode and Drive backup state.
- Local catalog/cache data: public medicine registry and pharmacy catalog data downloaded from public sources used to match products and display reference information.
- Local database export: if the user manually exports the database, the exported file contains the local data stored by the app.

## How data is used

MedStock uses this data only to provide app features:

- display and manage the user's home inventory of medicinal products;
- calculate stock status and shortage alerts from user-entered stock and dosage values;
- show expiry and reminder information;
- restore user data from an optional backup;
- display the connected Google account state and avatar;
- maintain app preferences and catalog matching.

MedStock does not use advertising SDKs, analytics SDKs, behavioral tracking, or data broker services, and does not sell personal data.

## Local storage

By default, user inventory, reminder, dosage, dose event, app preference, and catalog data is stored locally on the Android device in the app's private storage. Android app data can be removed by uninstalling the app or clearing MedStock data in Android system settings.

## Google Drive backup

Google Drive backup is optional. If the user connects a Google account and enables backup, MedStock creates a JSON backup containing medicinal products, user-entered dosage, stock data, reminders, alert settings, and app preferences. The backup is uploaded to the `appDataFolder` area of the user's Google Drive using the Google Drive app data scope.

The backup is used only to restore MedStock data for the same connected Google account. Users can disable Drive backup in the Account screen, disconnect the Google account in MedStock, and remove MedStock backup data from Google Drive or by revoking the app's Google account access.

## Data sharing

MedStock transmits data off the device only for optional user-controlled features:

- Google account and profile APIs are used after the user selects a Google account, to store the selected email locally and optionally display the account avatar.
- Google Drive API is used only when Drive backup is enabled, to upload/download the backup in the user's Google Drive app data folder.
- Public medicine/pharmacy registry endpoints may be contacted to download public catalog data. These catalog downloads are not intended to upload user inventory data to those public registry endpoints.

MedStock does not sell user data and does not share user data for advertising.

## Medical disclaimer

MedStock is not a medical device. It does not diagnose, treat, prevent disease, recommend medicines, verify whether a dosage is correct, or replace advice from a doctor or pharmacist. The user is responsible for entering correct data and following professional medical instructions.

## User choices and deletion

Users can:

- edit or delete medicines and reminders in the app;
- disable Google Drive backup;
- disconnect the Google account from MedStock;
- export the local database manually;
- clear app data in Android system settings;
- uninstall the app;
- delete MedStock backup data from Google Drive or revoke MedStock's Google access from the Google account settings.

## Security

MedStock stores local data in Android app-private storage. Google Drive backup is transmitted over HTTPS to Google APIs and stored in the user's Google Drive app data area. The current implementation does not add a separate user-managed encryption layer on top of Google Drive storage.

## Children's privacy

MedStock is not directed to children. The app should not be used by children to make medication decisions.

## Changes

This policy may be updated when app features, data handling, or Google Play declarations change.
