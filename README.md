# Bill Ledger for Android

Photograph or upload bills and receipts. Bill Ledger reads each one, sorts it into a category, and keeps an Excel workbook up to date, so you can see what you spend, by month and by item.

## What it does

- **Add bills three ways:** take a photo (on phones with a camera), choose photos or PDFs, or share a bill to Bill Ledger from WhatsApp, Gmail or your gallery. You can also type one in by hand.
- **Reads bills on the phone:** Google ML Kit text recognition finds the text, then simple rules pick out the payee, dates, total, currency, category and each item line with its quantity. It is free, works offline, and needs no internet permission, so photos and data never leave the device. Any result can be corrected by tapping the bill.
- **Overview:** the latest month's total and how it changed, spending by category, a month-by-month chart, recurring bills and your biggest payees.
- **Item tracker:** pick an item such as *Milk* and see, for each month or year, how many times you bought it, how much you bought (e.g. 8 L), what it cost and the price per unit.
- **Excel workbook that updates itself:** link a workbook once (in Downloads, Google Drive, etc.), and the app rewrites it after every change. The sheets are:
  - `Bills`: one row per bill
  - `Items`: one row per purchased line
  - `Item by month`: times bought, quantity, cost and price per unit (formulas)
  - `Item by year`: the same per year, plus the average per month
  - `Category by month`: spending per category each month

  If you add or edit rows in Excel, use *Load from Excel* to bring the changes back into the app.

## Install on your phone

1. Open **Releases → Bill Ledger — latest build** on your phone and download `BillLedger.apk`.
2. Open the file and allow installing from your browser or Files app when Android asks.
3. New builds install over the old one and keep your data.

Every push to `main` builds a new APK with GitHub Actions (`.github/workflows/build.yml`).

## Build it yourself

Open the folder in Android Studio (Ladybug or newer) and press Run, or from a terminal:

```
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## How it's built

Kotlin · Jetpack Compose (Material 3) · Room · ML Kit text recognition · a small built-in `.xlsx` writer and reader (no Apache POI).

| Folder | What's there |
| --- | --- |
| `data/` | Room database (bills and their items) and settings |
| `reading/` | Photo/PDF loading and the on-device reader |
| `analytics/` | Monthly totals, categories, recurring bills and item reports |
| `excel/` | Workbook layout, `.xlsx` writer and reader |
| `ui/` | Overview, Items, Bills, Settings and the edit form |

### Notes

- The app has no internet permission: everything stays on the phone.
- The signing key in `keystore/` is a shared debug key. It lets every build install over the last one, so don't use it for a Play Store release.
