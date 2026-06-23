# Changelog

## [1.0.9] - 2026-06-23

### Changed
- Pressing `Yes` on the payment notification now launches Money Lover directly and starts the existing accessibility automation, skipping the intermediate confirmation popup.
- Pressing `Yes` on the floating overlay follows the same direct-to-Money-Lover flow.
- Shared the Money Lover launch/automation checks in one helper so fallback paths still validate Money Lover installation and accessibility service status.

## [1.0.8] - 2026-06-22

### Added
- Main screen button: `Scan existing Wallet notifications`.
- Manual scan uses the notification listener's `activeNotifications` list to parse Google Wallet notifications that are already visible in the notification shade.
- If multiple active payments parse, the app prompts one at a time from newest to older across repeated scan button presses, avoiding overwrites of the single pending transaction slot.

## [1.0.7] - 2026-06-21

### Fixed
- Payment parser: real Google Wallet notification format was not handled
  - Actual format observed on device: `title="COSMOS SPORT"`, `text="€29.32 with Credit Visa ••6158"`
  - Added `P_AMOUNT_WITH` pattern: matches `€AMOUNT with ...` in text; merchant taken from notification title
  - Added `P_AMOUNT_WITH_SYMBOL_LAST` for `AMOUNT€ with ...` variant
  - Both patterns run at highest priority before the legacy combined-string patterns

## [1.0.6] - 2026-06-20

### Fixed
- Prevented the automation from mistaking the transaction list/category screens for the Add Transaction screen. It now requires an actual Money Lover numpad pattern before entering the amount.
- Changed numpad key lookup from substring matching to exact visible key matching, so list entries and amounts are not tapped as if they were numpad buttons.
- Added X nudges and larger raised Y offsets across `+` tap retries for the Fold layout.
- Prevented accessibility content-change events from cancelling the amount-entry gesture chain.

## [1.0.5] - 2026-06-20

### Fixed
- `clickAddButton()`: moved the inferred `+` tap above the bottom-nav text label row to target Money Lover's raised center button instead of the `Transactions` tab hit area.
- Added automatic retry attempts with alternate raised Y offsets if the Add Transaction screen does not open after the first tap.
- Relaxed bottom-nav label detection for foldable/taskbar layouts where the app bottom nav can sit above the physical bottom of the display.

## [1.0.4] - 2026-06-20

### Fixed
- `clickAddButton()`: fixed center `+` targeting when Money Lover exposes only adjacent bottom-nav labels. The tap is now inferred from label center spacing, preferring the midpoint between `Transactions` and `Budgets`, instead of adding the narrow `Transactions` text width and landing back on the `Transactions` tab.
- Blind coordinate fallback now uses the measured screen width on all supported Android versions.

## [1.0.3] - 2026-06-18

### Fixed
- Nav-anchor was tapping content-area nodes (e.g. "Transactions" section header) instead of the bottom nav bar tab. Fix: among all matches for a label, pick the one with the **largest centerY** (lowest on screen), then verify it sits below 88% of screen height — that confirms it's the actual nav bar strip, not content

## [1.0.3] - 2026-06-18

### Fixed
- `clickAddButton()`: previous nav-anchor tapped "Transactions" tab instead of "+" because using `screenW/2` as X was unreliable. Replaced with **adjacent-tab relative positioning**:
  - Find "Transactions" node (confirmed directly LEFT of "+") → `plusX = Transactions.centerX + Transactions.width`
  - Fallback: find "Budgets" node (directly RIGHT of "+") → `plusX = Budgets.centerX - Budgets.width`
  - Both approaches use the actual pixel bounds of the known tabs so the result is exact regardless of screen size or density

## [1.0.2] - 2026-06-18

### Fixed
- `clickAddButton()`: replaced unreliable "bottom 20% closest to centre" heuristic (was tapping list items) with **nav-anchor approach**: finds any other visible bottom nav label ("Home", "Transactions", "Budgets", "Account") to read the nav bar's Y coordinate, then gesture-taps at (screenWidth/2, navBarCentreY) — works even when the "+" is not individually accessible
- Version not incrementing: `versionCode`/`versionName` now updated in `build.gradle.kts` with each release

## [1.0.1] - 2026-06-18

### Fixed
- Version label not visible: changed text color from `textColorSecondary` to explicit `#888888`, right-aligned
- `clickAddButton()` rewritten: Money Lover uses a centred "+" in the bottom navigation bar, not a FloatingActionButton
  - Set `currentState = WAITING_FOR_ADD` immediately (before any async tap) to prevent re-entrant event loops
  - Added text/content-description search ("+", "Add", "Add transaction", "New transaction")
  - Added "bottom-centre heuristic": finds the clickable node with the smallest horizontal distance from screen centre in the bottom 20% of the display — reliably hits the green "+" regardless of its internal implementation
  - Blind gesture fallback taps 30 dp above true screen bottom to clear the system gesture bar

## [1.0.0] - 2026-06-18

### Added
- Initial release
- `NotificationListenerService` monitoring Google Wallet (and Samsung Pay) notifications
- Regex payment parser supporting multiple notification formats and locales (€12.40, 12,40€, "Paid €12.40 at …", etc.)
- Floating overlay (TYPE_APPLICATION_OVERLAY) with Yes/No prompt when payment detected
- Heads-up notification fallback when overlay permission not granted
- Per-type toggles in Settings (overlay on/off, notification on/off)
- `AccessibilityService` automating amount entry into Money Lover via `dispatchGesture()` (simulated touch — bypasses custom numpad security blocks)
- `ConfirmationActivity` (dialog-themed) showing amount, merchant, date with "Open Money Lover" button
- `PendingTransactionStore` (SharedPreferences) persisting payment across process boundaries
- Battery optimization exemption request (Samsung Galaxy / OEM kill protection)
- Debug log panel in MainActivity showing last 20 wallet notification events
- "Simulate test payment" button for testing without a real Google Wallet transaction
- Settings screen: overlay toggle, notification toggle, Money Lover package name, debug mode
- Version label displayed in main screen

### Fixed
- Crash on launch: theme parent changed from `DayNight.DarkActionBar` to `DayNight.NoActionBar` (ActionBar conflict with custom Toolbar)
- Crash on "Simulate payment": removed `android:foregroundServiceType="specialUse"` — not allowed for plain Services on Android 16
- `POST_NOTIFICATIONS` runtime permission request added (required Android 13+, was causing silent SecurityException)
- Status bar overlap: wrapped Toolbar in `AppBarLayout` with `fitsSystemWindows="true"` (Android 15+ edge-to-edge enforcement)
- Accessibility service revoked after crash: root cause was foreground service crash — fixing that prevents revocation
- Samsung battery optimization killing `NotificationListenerService`: added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + UI status row

### Technical notes
- `performAction(ACTION_CLICK)` on Money Lover's custom numpad returns `true` but produces no input (app-level block). Replaced with `dispatchGesture()` + `GestureDescription.StrokeDescription` which simulates real touch events
- Node lookup uses `findAccessibilityNodeInfosByText` (partial match) — exact `==` match breaks on nodes with trailing spaces or Unicode variants
- `canPerformGestures="true"` set in `accessibility_service_config.xml`
- Numpad entry retries up to 4 times with 1.5s delay; on final failure shows diagnostic toast with visible node sample
