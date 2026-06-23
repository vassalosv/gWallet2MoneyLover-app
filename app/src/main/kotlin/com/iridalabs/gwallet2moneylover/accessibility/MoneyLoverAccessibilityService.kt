package com.iridalabs.gwallet2moneylover.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.iridalabs.gwallet2moneylover.data.AppSettings
import com.iridalabs.gwallet2moneylover.data.PaymentInfo
import com.iridalabs.gwallet2moneylover.data.PendingTransactionStore
import java.util.Locale

/**
 * Automates entering an amount into Money Lover's Add Transaction screen.
 *
 * Money Lover uses a CUSTOM numpad. The standard performAction(ACTION_CLICK)
 * is blocked by many financial apps for security. We therefore use
 * dispatchGesture() which simulates real touch events — nothing can block those.
 *
 * Key sequence for "12.40":  C → 1 → 2 → . → 4 → 0 → >
 *
 * The ">" (confirm) button is blue, bottom-right. If we can't find it by text
 * we calculate its position geometrically from the "." and "0" buttons.
 */
class MoneyLoverAccessibilityService : AccessibilityService() {

    // ── State ────────────────────────────────────────────────────────────────

    private enum class State {
        IDLE, WAITING_FOR_MAIN, WAITING_FOR_ADD, ENTERING_AMOUNT, DONE
    }

    companion object {
        private const val TAG = "MLAccessibility"

        @Volatile var isRunning = false
            private set

        @Volatile private var pendingPayment: PaymentInfo? = null
        @Volatile private var currentState = State.IDLE
        @Volatile private var retryCount = 0
        @Volatile private var addTapAttempt = 0

        fun triggerAutomation(payment: PaymentInfo) {
            Log.i(TAG, "Trigger: ${payment.formattedAmount} @ ${payment.merchantName}")
            pendingPayment  = payment
            currentState    = State.WAITING_FOR_MAIN
            retryCount      = 0
            addTapAttempt   = 0
        }

        private val ADD_BTN_IDS = listOf(
            "fab", "btn_add", "btn_add_transaction", "fabAdd",
            "floatingActionButton", "add_transaction_btn", "main_fab", "iv_add"
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private data class NavAnchor(
        val label: String,
        val bounds: Rect
    )

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        isRunning = true
        Log.i(TAG, "Service connected")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning       = false
        currentState    = State.IDLE
        super.onDestroy()
    }

    // ── Events ───────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (currentState == State.IDLE ||
            currentState == State.DONE ||
            currentState == State.ENTERING_AMOUNT
        ) return

        val mlPkg = AppSettings.getMoneyLoverPackage(this)
        if (event.packageName?.toString() != mlPkg) return

        val relevant = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return

        // Debounce — let the screen settle before we inspect it
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ processScreen() }, 800)
    }

    // ── Screen processing ────────────────────────────────────────────────────

    private fun processScreen() {
        val root = rootInActiveWindow ?: return
        if (AppSettings.isDebugMode(this)) dumpTree(root, 0)

        when (currentState) {
            State.WAITING_FOR_MAIN -> {
                if (isAddScreen(root)) {
                    currentState = State.ENTERING_AMOUNT
                    handler.postDelayed({ startAmountEntry() }, 1200)
                } else {
                    clickAddButton(root)
                }
            }
            State.WAITING_FOR_ADD -> {
                if (isAddScreen(root)) {
                    currentState = State.ENTERING_AMOUNT
                    handler.postDelayed({ startAmountEntry() }, 1200)
                } else {
                    retryCount++
                    if (retryCount <= 4) {
                        Log.d(TAG, "Add screen not open after tap; retrying '+' attempt $retryCount")
                        clickAddButton(root)
                    } else if (retryCount > 8) {
                        showToast("Couldn't find Add Transaction screen.\nPlease tap '+' manually.")
                        currentState = State.IDLE
                    }
                }
            }
            State.ENTERING_AMOUNT -> { /* handled by gesture chain */ }
            else -> Unit
        }
    }

    // ── Screen detection ─────────────────────────────────────────────────────

    private fun isAddScreen(root: AccessibilityNodeInfo): Boolean =
        hasMoneyLoverNumpad(root)

    private fun hasMoneyLoverNumpad(root: AccessibilityNodeInfo): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectAll(root, nodes)

        val visibleKeys = nodes
            .filter { it.isVisibleToUser }
            .mapNotNull { node ->
                val text = normalizedNodeText(node) ?: return@mapNotNull null
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                text to bounds
            }
            .filter { (_, bounds) -> bounds.width() > 0 && bounds.height() > 0 }

        val digitCount = ('0'..'9').count { digit ->
            visibleKeys.any { (text, _) -> text == digit.toString() }
        }
        val hasClear = visibleKeys.any { (text, _) -> text.equals("C", ignoreCase = true) }
        val hasDecimal = visibleKeys.any { (text, _) -> text == "." || text == "," }
        val bottomRowKeyCount = visibleKeys
            .map { (text, _) -> text }
            .filter { it in setOf("0", ".", ",", ">") }
            .distinct()
            .size

        val isNumpad = digitCount >= 8 && hasClear && hasDecimal && bottomRowKeyCount >= 2
        Log.d(TAG, "Numpad check: digits=$digitCount clear=$hasClear decimal=$hasDecimal bottomRowKeys=$bottomRowKeyCount -> $isNumpad")
        return isNumpad
    }

    // ── Step 1: tap the "+" button ───────────────────────────────────────────
    //
    // Money Lover bottom nav:  Home | Transactions | [+] | Budgets | Account
    //
    // The "+" is item 3 of 5. "Transactions" is directly to its left,
    // "Budgets" is directly to its right. Text bounds are narrower than full
    // tab slots, so the "+" position is inferred from tab center spacing.
    //
    // Strategy (in order):
    //   1. Resource-id lookup
    //   2. Text / content-description of "+" itself
    //   3. Bottom-nav label center spacing
    //   4. Blind coordinate fallback

    private fun clickAddButton(root: AccessibilityNodeInfo) {
        // Set state immediately — prevents re-entrant loops on rapid events
        currentState = State.WAITING_FOR_ADD
        addTapAttempt++

        val pkg = AppSettings.getMoneyLoverPackage(this)

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val screenW: Int
        val screenH: Int
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenW = bounds.width()
            screenH = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val dm = android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            screenW = dm.widthPixels
            screenH = dm.heightPixels
        }
        // Foldables and gesture/taskbar layouts can place app bottom navs well above
        // the physical display bottom, so use the lower quarter instead of only 12%.
        val navThreshold = screenH * 0.75f

        // 1. By resource-id
        for (id in ADD_BTN_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            if (nodes.isNotEmpty()) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Tapped add by id: $id")
                scheduleAddScreenCheck()
                return
            }
        }

        // 2. By text / content-description of "+" itself
        for (label in listOf("+", "Add", "Add transaction", "New transaction")) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
                .filter { it.isVisibleToUser && it.isClickable }
            if (nodes.isNotEmpty()) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Tapped add by label: $label")
                scheduleAddScreenCheck()
                return
            }
        }

        // 3. Infer the center "+" item from bottom-nav label centers.
        bottomNavAddCenter(root, navThreshold, screenW)?.let { (tapX, tapY) ->
            Log.d(TAG, "Bottom-nav inferred '+' tap ($tapX, $tapY)")
            tapAt(tapX, tapY) { scheduleAddScreenCheck() }
            return
        }

        // 4. Blind fallback
        val density = resources.displayMetrics.density
        val tapX = screenW / 2f
        val tapY = screenH - (56 * density / 2f) - (20 * density)
        Log.d(TAG, "Blind tap at ($tapX, $tapY)  screenH=$screenH")
        tapAt(tapX, tapY) { scheduleAddScreenCheck() }
    }

    private fun scheduleAddScreenCheck() {
        handler.postDelayed({
            if (currentState == State.WAITING_FOR_ADD) processScreen()
        }, 1200)
    }

    private fun bottomNavAddCenter(
        root: AccessibilityNodeInfo,
        threshold: Float,
        screenW: Int
    ): Pair<Float, Float>? {
        val anchors = listOf("Home", "Transactions", "Budgets", "Account")
            .mapNotNull { label ->
                bottomNavBounds(root, label, threshold)?.let { NavAnchor(label, it) }
            }
            .associateBy { it.label }

        val transactions = anchors["Transactions"]
        val budgets = anchors["Budgets"]
        if (transactions != null && budgets != null) {
            val slotWidth = (budgets.bounds.exactCenterX() - transactions.bounds.exactCenterX()) / 2f
            val tapX = nudgedAddButtonX(
                (transactions.bounds.exactCenterX() + budgets.bounds.exactCenterX()) / 2f,
                slotWidth
            )
            val tapY = raisedAddButtonY(transactions.bounds, budgets.bounds)
            Log.d(TAG, "Nav midpoint: Transactions/Budgets -> ($tapX, $tapY)")
            return Pair(tapX, tapY)
        }

        val home = anchors["Home"]
        if (home != null && transactions != null) {
            val spacing = transactions.bounds.exactCenterX() - home.bounds.exactCenterX()
            if (spacing > 0f) {
                val tapX = nudgedAddButtonX(transactions.bounds.exactCenterX() + spacing, spacing)
                val tapY = raisedAddButtonY(home.bounds, transactions.bounds)
                Log.d(TAG, "Nav spacing: Home/Transactions -> ($tapX, $tapY)")
                return Pair(tapX, tapY)
            }
        }

        val account = anchors["Account"]
        if (budgets != null && account != null) {
            val spacing = account.bounds.exactCenterX() - budgets.bounds.exactCenterX()
            if (spacing > 0f) {
                val tapX = nudgedAddButtonX(budgets.bounds.exactCenterX() - spacing, spacing)
                val tapY = raisedAddButtonY(budgets.bounds, account.bounds)
                Log.d(TAG, "Nav spacing: Budgets/Account -> ($tapX, $tapY)")
                return Pair(tapX, tapY)
            }
        }

        val slotWidth = screenW / 5f
        if (transactions != null) {
            val tapX = nudgedAddButtonX(transactions.bounds.exactCenterX() + slotWidth, slotWidth)
            val tapY = raisedAddButtonY(transactions.bounds)
            Log.d(TAG, "Nav single anchor: Transactions -> ($tapX, $tapY)")
            return Pair(tapX, tapY)
        }
        if (budgets != null) {
            val tapX = nudgedAddButtonX(budgets.bounds.exactCenterX() - slotWidth, slotWidth)
            val tapY = raisedAddButtonY(budgets.bounds)
            Log.d(TAG, "Nav single anchor: Budgets -> ($tapX, $tapY)")
            return Pair(tapX, tapY)
        }

        val anyAnchor = anchors.values.firstOrNull() ?: return null
        val tapX = nudgedAddButtonX(screenW / 2f, slotWidth)
        val tapY = raisedAddButtonY(anyAnchor.bounds)
        Log.d(TAG, "Nav single non-adjacent anchor: ${anyAnchor.label} -> ($tapX, $tapY)")
        return Pair(tapX, tapY)
    }

    private fun nudgedAddButtonX(baseX: Float, slotWidth: Float): Float {
        val nudgeFactors = floatArrayOf(0f, 0.12f, -0.12f, 0.22f, -0.22f)
        val offsetIndex = (addTapAttempt - 1).coerceIn(0, nudgeFactors.lastIndex)
        return baseX + slotWidth * nudgeFactors[offsetIndex]
    }

    private fun raisedAddButtonY(vararg anchors: Rect): Float {
        val density = resources.displayMetrics.density
        val labelTop = anchors.minOf { it.top }.toFloat()
        val offsetsDp = floatArrayOf(72f, 88f, 56f, 104f, 40f)
        val offsetIndex = (addTapAttempt - 1).coerceIn(0, offsetsDp.lastIndex)
        return (labelTop - offsetsDp[offsetIndex] * density).coerceAtLeast(0f)
    }

    /**
     * Return the bounds of [label] only if it sits inside the bottom nav bar
     * (centerY >= [threshold]). Picks the lowest occurrence to avoid content matches.
     */
    private fun bottomNavBounds(
        root: AccessibilityNodeInfo,
        label: String,
        threshold: Float
    ): Rect? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectAll(root, nodes)
        return nodes
            .filter { it.isVisibleToUser && normalizedNodeText(it) == label }
            .mapNotNull { n -> Rect().also { b -> n.getBoundsInScreen(b) } }
            .filter { b -> b.centerY() >= threshold }
            .maxByOrNull { b -> b.centerY() }
    }

    // ── Step 2: enter amount via gesture taps ────────────────────────────────

    private fun startAmountEntry(attempt: Int = 0) {
        val payment = pendingPayment ?: return

        // Prefer the ML window explicitly; fall back to active window
        val mlPkg = AppSettings.getMoneyLoverPackage(this)
        val root = windows
            ?.mapNotNull { it.root }
            ?.firstOrNull { it.packageName?.toString() == mlPkg }
            ?: rootInActiveWindow

        if (root == null) {
            Log.w(TAG, "No root window on attempt $attempt")
            retry(attempt) { startAmountEntry(it) }
            return
        }

        // Dump tree on first attempt so we can debug if needed
        if (attempt == 0) dumpTree(root, 0)

        // Amount string e.g. "12.40"
        val amountStr = String.format(Locale.US, "%.2f", payment.amount)

        val taps = mutableListOf<Triple<String, Float, Float>>()

        // C/clear – optional; Money Lover may use backspace instead
        nodeCenter(root, "C")?.let { taps += Triple("C", it.first, it.second) }

        // Digits and decimal point
        var foundAny = false
        for (ch in amountStr) {
            val key = ch.toString()
            val center = nodeCenter(root, key)
            if (center != null) {
                taps += Triple(key, center.first, center.second)
                foundAny = true
            } else {
                Log.w(TAG, "Key '$key' not found (attempt $attempt)")
            }
        }

        // Confirm button – ">" or geometric fallback
        confirmCenter(root)?.let { taps += Triple(">", it.first, it.second) }

        Log.i(TAG, "Attempt $attempt — tap plan: ${taps.map { it.first }}")

        if (!foundAny) {
            // Numpad not rendered yet — retry
            if (attempt < 4) {
                Log.w(TAG, "No digit nodes found, retrying in 1.5s")
                retry(attempt) { startAmountEntry(it) }
            } else {
                val sample = sampleNodeTexts(root)
                showToast("Numpad not accessible after ${attempt + 1} attempts.\n" +
                        "Visible nodes: $sample\nCheck logcat tag MLAccessibility.")
                currentState = State.IDLE
            }
            return
        }

        executeTaps(taps, 0, payment)
    }

    private fun retry(attempt: Int, block: (Int) -> Unit) {
        handler.postDelayed({ block(attempt + 1) }, 1500)
    }

    /** Collect a short sample of visible node texts for diagnostics */
    private fun sampleNodeTexts(root: AccessibilityNodeInfo): String {
        val all = mutableListOf<AccessibilityNodeInfo>()
        collectAll(root, all)
        return all.filter { it.isVisibleToUser && (it.text != null || it.contentDescription != null) }
            .take(8)
            .joinToString(" | ") { "t='${it.text}' d='${it.contentDescription}'" }
    }

    /** Execute gesture taps sequentially, 300 ms apart */
    private fun executeTaps(
        taps: List<Triple<String, Float, Float>>,
        index: Int,
        payment: PaymentInfo
    ) {
        if (index >= taps.size) {
            currentState = State.DONE
            finishAutomation(payment)
            return
        }

        val (key, x, y) = taps[index]
        Log.d(TAG, "Gesture tap '$key' at (${x.toInt()}, ${y.toInt()})")

        tapAt(x, y) {
            handler.postDelayed({ executeTaps(taps, index + 1, payment) }, 300)
        }
    }

    // ── Node / coordinate helpers ────────────────────────────────────────────

    /**
     * Find a visible node for [key], preferring the one lowest on screen
     * (largest bounds.top = deepest in layout = numpad area, not amount display).
     *
     * Uses findAccessibilityNodeInfosByText (partial/substring match) to stay
     * consistent with how Money Lover's custom numpad exposes its button labels —
     * exact equality breaks if the node has trailing spaces or Unicode digits.
     */
    private fun nodeCenter(root: AccessibilityNodeInfo, key: String): Pair<Float, Float>? {
        val all = mutableListOf<AccessibilityNodeInfo>()
        collectAll(root, all)
        val candidates = all.filter { node ->
            node.isVisibleToUser && normalizedNodeText(node) == key
        }

        // Pick the candidate lowest on screen to avoid matching the amount display
        val match = candidates.maxByOrNull { n ->
            val b = Rect(); n.getBoundsInScreen(b); b.top
        } ?: return null

        val b = Rect()
        match.getBoundsInScreen(b)
        return Pair(b.exactCenterX(), b.exactCenterY())
    }

    /**
     * Find the ">" confirm button.
     * Tries text/desc first; falls back to geometric calculation:
     *   Layout of bottom row:  [0]  [000]  [.]  [>]
     *   Button width ≈ (centerX(".") − centerX("0")) / 2
     *   confirmX ≈ centerX(".") + buttonWidth
     */
    private fun confirmCenter(root: AccessibilityNodeInfo): Pair<Float, Float>? {
        // 1. By text ">"
        nodeCenter(root, ">")?.let { return it }

        // 2. By common content descriptions
        val all = mutableListOf<AccessibilityNodeInfo>()
        collectAll(root, all)
        val confirmDescs = setOf("done", "confirm", "next", "ok", "accept", "go", "validate")
        all.firstOrNull { n ->
            n.isVisibleToUser &&
            n.contentDescription?.toString()?.lowercase()?.let { it in confirmDescs } == true
        }?.let { n ->
            val b = Rect(); n.getBoundsInScreen(b)
            return Pair(b.exactCenterX(), b.exactCenterY())
        }

        // 3. Geometric: ">" is one button-width to the right of "."
        val dotPt  = nodeCenter(root, ".") ?: return null
        val zeroPt = nodeCenter(root, "0") ?: return null
        // Bottom row spans [0] [000] [.] [>] → 4 equal columns
        // distance "0"→"." is 2 button-widths  ⟹  buttonWidth = (dotX − zeroX) / 2
        val bw = (dotPt.first - zeroPt.first) / 2f
        Log.d(TAG, "Geometric '>': dotCenter=${dotPt}, buttonWidth=$bw → x=${dotPt.first + bw}")
        return Pair(dotPt.first + bw, dotPt.second)
    }

    // ── Gesture dispatch ─────────────────────────────────────────────────────

    private fun tapAt(x: Float, y: Float, onDone: () -> Unit) {
        val path    = Path().apply { moveTo(x, y) }
        val stroke  = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) = onDone()
            override fun onCancelled(g: GestureDescription) {
                Log.w(TAG, "Gesture cancelled at ($x, $y)")
                onDone()
            }
        }, handler)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun finishAutomation(payment: PaymentInfo) {
        pendingPayment = null
        PendingTransactionStore.clear(this)
        showToast("✓ ${payment.formattedAmount} entered.\nNow select a category and tap Save.")
        Log.i(TAG, "Automation done for ${payment.formattedAmount}")
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun collectAll(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectAll(it, out) }
    }

    private fun normalizedNodeText(node: AccessibilityNodeInfo): String? {
        val raw = node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: return null
        return raw.trim().takeIf { it.isNotEmpty() }
    }

    private fun findByClass(node: AccessibilityNodeInfo, cls: String): AccessibilityNodeInfo? {
        if (node.className?.toString() == cls && node.isClickable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findByClass(it, cls) }?.let { return it }
        }
        return null
    }

    private fun showToast(msg: String) =
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    private fun dumpTree(node: AccessibilityNodeInfo?, depth: Int) {
        node ?: return
        val indent = "  ".repeat(depth)
        Log.d(TAG,
            "$indent[${node.className?.toString()?.substringAfterLast('.')}] " +
            "id=${node.viewIdResourceName?.substringAfter('/')} " +
            "text='${node.text?.toString()}' desc='${node.contentDescription?.toString()}' " +
            "click=${node.isClickable} vis=${node.isVisibleToUser}"
        )
        for (i in 0 until node.childCount) dumpTree(node.getChild(i), depth + 1)
    }
}
