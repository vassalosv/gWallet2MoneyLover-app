# gWallet2MoneyLover — Task History

## Goal
Build an Android app that monitors Google Wallet payment notifications and automatically enters the transaction amount into Money Lover Premium.

---

## Session 1 — Initial Build

### Spec (user)
- Monitor `com.google.android.apps.walletnfcrel` via `NotificationListenerService`
- Parse amount, currency, merchant, timestamp from notification text
- Show floating overlay (primary) or heads-up notification (fallback) with Yes/No prompt
- On Yes: open Money Lover, auto-fill amount via `AccessibilityService`, stop before category
- Settings to toggle overlay/notification independently; both on by default

### Decisions
- Build method: Kotlin Gradle project
- Money Lover: Premium version (`com.bookmark.money`)
- Confirmation UI: overlay first, fallback to notification if permission not granted

### Files created
| File | Purpose |
|------|---------|
| `settings.gradle.kts` | Project root, includes `:app` |
| `build.gradle.kts` | AGP 8.x, Kotlin 1.9.25 |
| `app/build.gradle.kts` | compileSdk 35, minSdk 26, ViewBinding |
| `gradle.properties` | AndroidX, Jetifier, JVM args |
| `AndroidManifest.xml` | Permissions, service declarations, queries |
| `data/PaymentInfo.kt` | Data class with amount, currency, merchant, timestamp |
| `data/PendingTransactionStore.kt` | SharedPreferences store for pending payment |
| `data/AppSettings.kt` | App-wide settings keys and accessors |
| `parser/PaymentParser.kt` | Regex parser for multiple notification formats |
| `notification/WalletNotificationListener.kt` | NotificationListenerService |
| `notification/NotificationHelper.kt` | Notification channel + heads-up notification |
| `overlay/OverlayService.kt` | WindowManager overlay with Yes/No buttons |
| `ui/ConfirmationActivity.kt` | Dialog-themed confirmation screen |
| `ui/MainActivity.kt` | Permission status, debug log, simulate button |
| `ui/SettingsActivity.kt` | PreferenceFragmentCompat settings screen |
| `accessibility/MoneyLoverAccessibilityService.kt` | Accessibility automation |
| `res/xml/accessibility_service_config.xml` | Accessibility service config |
| `res/layout/activity_main.xml` | Main screen layout |
| `res/values/themes.xml` | App themes |
| `res/values/strings.xml` | String resources |

---

## Bugs fixed during Session 1

### Crash on launch (before any window shown)
- **Cause:** Theme parent `DayNight.DarkActionBar` injects system ActionBar; calling `setSupportActionBar()` on a custom Toolbar causes `IllegalStateException`
- **Fix:** Changed theme parent to `Theme.MaterialComponents.DayNight.NoActionBar`

### `android.useAndroidX` build error
- **Cause:** Missing `gradle.properties`
- **Fix:** Created file with `android.useAndroidX=true` and `android.enableJetifier=true`

### Crash on "Simulate payment" button
- **Cause 1:** `OverlayService` declared with `android:foregroundServiceType="specialUse"` — causes `SecurityException` on Android 16
- **Cause 2:** `POST_NOTIFICATIONS` not requested as runtime permission (Android 13+ requirement)
- **Fix:** Removed `foregroundServiceType` entirely; added `registerForActivityResult(RequestPermission)` in `MainActivity`; wrapped button code in try-catch

### Status bar overlap (toolbar hidden behind system status bar)
- **Cause:** Android 15+ enforces edge-to-edge by default
- **Fix:** Wrapped Toolbar in `AppBarLayout` with `fitsSystemWindows="true"`; added `statusBarColor` and `windowLightStatusBar` to theme

### Samsung Galaxy battery optimization killing services
- **Fix:** Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission + UI status row in `MainActivity`

### Amount not filling (first attempt)
- **Cause:** Using `ACTION_SET_TEXT` on a non-EditText custom view
- **Fix:** Switched to numpad button tapping via `findAccessibilityNodeInfosByText` + `performAction(ACTION_CLICK)`

### Amount still not filling (second attempt)
- **Cause:** `performAction(ACTION_CLICK)` returns `true` but produces no input — Money Lover's custom financial numpad blocks accessibility action events
- **Fix:** Switched to `dispatchGesture()` with `GestureDescription.StrokeDescription`, simulating real touch events. Updated `accessibility_service_config.xml` with `canPerformGestures="true"`

### Accessibility service revoked after crash
- **Cause:** Android revokes accessibility services after process death (security measure). Root cause was the foreground service crash
- **Fix:** Fixing the crash prevents the revocation

---

## Session 2 — Accessibility Rewrite & UI Fixes

### `MoneyLoverAccessibilityService.kt` — full rewrite
- Replaced `performAction(ACTION_CLICK)` with `dispatchGesture()` + `GestureDescription.StrokeDescription`
- Tap sequence: `C → digits → .  → confirm (>)`
- Node lookup: `findAccessibilityNodeInfosByText` (partial match) — exact `==` match was breaking on nodes with trailing spaces or Unicode variants
- Picks the candidate with the highest `bounds.top` (lowest on screen = numpad area, not amount display)
- Geometric fallback for ">" confirm button: calculated from "." and "0" button positions
- Retry logic: up to 4 attempts with 1.5s delay if no digit nodes found
- Uses `windows` API to find Money Lover's window directly rather than `rootInActiveWindow`
- On final failure: shows diagnostic toast with sample of visible node texts

### Compile error fix
- `node.className` returns `CharSequence?` not `String?` — `substringAfterLast` only exists on `String`
- **Fix:** Added `.toString()` call before `substringAfterLast`

### Version label added to main screen
- Added `TextView` in `activity_main.xml` above the test button
- Set from `packageManager.getPackageInfo(packageName, 0).versionName` in `MainActivity.onCreate`
- Color `#888888`, right-aligned

---

## Session 3 — Money Lover "+" Button Navigation

### Problem
The accessibility service could not find or tap the "+" button in Money Lover's bottom navigation bar to open the Add Transaction screen.

### What was tried (in order)

| Attempt | Approach | Result |
|---------|----------|--------|
| 1 | Resource-id lookup (`fab`, `btn_add`, etc.) | Not found — ML uses different IDs |
| 2 | FAB class lookup (`FloatingActionButton`, `ImageButton`) | Not found — it's a nav bar item |
| 3 | Text/label search (`"+"`, `"Add"`, etc.) | Not found — button may have no text |
| 4 | "Bottom 20% closest to centre" heuristic | Tapped a transaction list item instead |
| 5 | Nav-anchor: find any nav label, tap `screenW/2` | Tapped "Transactions" tab — `screenW/2` was landing on the wrong item |
| 6 | **Adjacent-tab relative positioning** | In testing |

### Current approach (v1.0.3)
Money Lover bottom nav layout: `Home | Transactions | [+] | Budgets | Account`

- Find "Transactions" node filtered to bottom 12% of screen (avoids content matches)
- `plusX = Transactions.centerX + Transactions.width` (one item-width to the right)
- `plusY = Transactions.centerY`
- Fallback: same from "Budgets" side (`plusX = Budgets.centerX - Budgets.width`)
- State set to `WAITING_FOR_ADD` immediately before any async tap to prevent re-entrant event loops

---

## Open Issues / Pending

- [ ] Confirm "+" button tap works reliably with adjacent-tab approach (v1.0.3)
- [ ] Confirm numpad digit nodes are found (diagnostic toast will show visible nodes if they aren't)
- [ ] End-to-end test with real Google Wallet payment notification

---

## Version History

| Version | Date | Notes |
|---------|------|-------|
| 1.0.0 | 2026-06-18 | Initial release |
| 1.0.1 | 2026-06-18 | Version label fix; "+" button search improvements |
| 1.0.2 | 2026-06-18 | Nav-anchor approach for "+" button |
| 1.0.3 | 2026-06-18 | Adjacent-tab relative positioning for "+" button |
