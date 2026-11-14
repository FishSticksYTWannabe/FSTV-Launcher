package com.fstv.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.app.ActivityManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var repo: AppRepository
    private lateinit var shadow: Bitmap
    private lateinit var root: FrameLayout
    private lateinit var backdrop: Backdrop
    private lateinit var hero: HeroView
    private lateinit var grid: FrameLayout
    private lateinit var clock: TextView
    private lateinit var greeting: TextView
    private lateinit var cornerDate: TextView
    private lateinit var clockPanel: FrameLayout
    private lateinit var digitalGroup: FrameLayout
    private lateinit var bigA: TextView
    private lateinit var bigB: TextView
    private lateinit var bigDate: TextView
    private lateinit var analog: AnalogClockView
    private lateinit var hint: TextView
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    private var bigFront = 0
    private var lastTime = ""
    private lateinit var launchVeil: View
    private lateinit var launchTile: ImageView
    private lateinit var heroLaunch: ImageView
    private lateinit var shelf: ShelfBar
    private lateinit var deepVeil: View
    private lateinit var splash: TextView
    private lateinit var splashSpinner: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val heroCache = LruCache<String, Bitmap>(HERO_CACHE_SIZE)
    private val scroll = Spring(170f, 24f, epsilon = 0.5f)
    private val idleAmount = Spring(24f, 10f)
    private val deepAmount = Spring(16f, 9f)
    private val ticker = Ticker(::onFrame)

    private val cells = ArrayList<TileCell>()
    private var focusIndex = 0
    private var focusKey: String? = null
    private var editing = false
    private var showAll = false
    private var resumed = false
    private var loadGeneration = 0
    private var heroGeneration = 0
    private var lastInput = 0L
    private var lastMove = 0L
    private var selectHeld = false
    private var longPressFired = false
    private var idleMode = false
    private var deepIdle = false
    private var swallowUntilKeyUp = false
    private var wallpaper: Bitmap? = null
    private var slides: List<File> = emptyList()
    private var slideIndex = 0

    private val idleCheck = Runnable { enterIdle() }
    private val deepIdleCheck = Runnable { enterDeepIdle() }
    private val advanceSlide = Runnable { showNextSlide() }

    private val longPress = Runnable {
        longPressFired = true
        cells.getOrNull(focusIndex)?.setHeld(false)
        setEditing(!editing)
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClock()
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(reloadQuietly)
            handler.postDelayed(reloadQuietly, RELOAD_DEBOUNCE_MS)
        }
    }

    private val reloadQuietly = Runnable { reload(cascade = false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The idle photos replace sleep and Amazon's screensaver while this screen is showing.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val size = Point().also { windowManager.defaultDisplay.getRealSize(it) }
        ui = Ui(size.x, size.y)
        repo = AppRepository(this, ui)
        shadow = Art.shadow(ui)

        root = FrameLayout(this).apply {
            clipChildren = false
            setBackgroundColor(Color.BLACK)
        }
        backdrop = Backdrop(this, ui)
        hero = HeroView(this, ui)
        shelf = ShelfBar(this, ui)
        grid = FrameLayout(this).apply { clipChildren = false }
        clock = makeText(ui.px(54f), Typeface.create("sans-serif-light", Typeface.NORMAL))
        greeting = makeText(ui.px(52f), Typeface.create("sans-serif-light", Typeface.NORMAL))
        cornerDate = makeText(ui.px(28f), Typeface.create("sans-serif", Typeface.NORMAL)).apply {
            setTextColor(0xCCFFFFFF.toInt())
        }
        hint = makeText(ui.px(30f), Typeface.DEFAULT).apply {
            text = "Use the arrows to move this app. Press Select when you are done."
            alpha = 0f
        }

        root.addView(backdrop, fullScreen())
        root.addView(hero, fullScreen())
        root.addView(shelf, fullScreen())
        root.addView(grid, fullScreen())
        root.addView(greeting, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            topMargin = ui.px(44f).toInt()
            leftMargin = ui.left.toInt()
        })
        root.addView(cornerDate, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            topMargin = ui.px(118f).toInt()
            leftMargin = ui.left.toInt()
        })
        root.addView(clock, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
            topMargin = ui.px(48f).toInt()
            rightMargin = ui.left.toInt()
        })
        root.addView(hint, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = ui.px(36f).toInt()
        })
        // A full-screen dark pane that deepens as the TV sits idle for a long time (near-off look).
        deepVeil = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }
        root.addView(deepVeil, fullScreen())

        // The idle / always-on clock panel: big digital time (two layers for animated changes),
        // the date, and an analog face shown in the near-off state. Sits above deepVeil so it stays
        // bright while the rest of the screen darkens.
        clockPanel = FrameLayout(this).apply { clipChildren = false; alpha = 0f }
        // Near-off screen shows a large analog clock; the inactive screen shows the digital one.
        analog = AnalogClockView(this, ui).apply { alpha = 0f }
        clockPanel.addView(analog, FrameLayout.LayoutParams(ui.px(560f).toInt(), ui.px(560f).toInt(), Gravity.CENTER))
        digitalGroup = FrameLayout(this).apply { clipChildren = false }
        bigA = makeBigClock()
        bigB = makeBigClock().apply { alpha = 0f }
        digitalGroup.addView(bigA, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        digitalGroup.addView(bigB, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        clockPanel.addView(digitalGroup, fullScreen())
        bigDate = TextView(this).apply {
            setTextColor(0xF2FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(48f))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            setShadowLayer(ui.px(14f), 0f, ui.px(2f), 0x80000000.toInt())
        }
        clockPanel.addView(bigDate, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        root.addView(clockPanel, fullScreen())

        launchVeil = View(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        launchTile = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        // Overlay that holds the focused app's hero image and expands it to open the app. It sits
        // above the grid (so the expansion is visible) but below the black launch veil.
        heroLaunch = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        root.addView(heroLaunch, FrameLayout.LayoutParams(ui.heroArtW.toInt(), ui.heroArtH.toInt()).apply {
            topMargin = ui.heroArtTop.toInt()
            leftMargin = ((ui.screenW - ui.heroArtW) / 2f).toInt()
        })
        root.addView(launchVeil, fullScreen())
        root.addView(launchTile, FrameLayout.LayoutParams(ui.tileW.toInt(), ui.tileH.toInt()))

        // Startup wordmark over black, shown once on a cold start with the chime.
        splash = TextView(this).apply {
            text = "FSTV"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(150f))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(splash, fullScreen())
        // Loading spinner the typed wordmark hands off to, briefly, before the home screen fades in.
        splashSpinner = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        val spinnerSize = ui.px(96f).toInt()
        root.addView(splashSpinner, FrameLayout.LayoutParams(spinnerSize, spinnerSize).apply {
            gravity = Gravity.CENTER
        })
        setContentView(root)
        if (savedInstanceState == null) playStartup()
        GuardService.start(this)
        // Queued first, so a wallpaper is ready before the grid decides what to show behind it.
        loadWallpaper()
        reload(cascade = true)
    }

    /**
     * Startup animation: the FSTV wordmark scales in, a terminal-style cursor appears next to it,
     * " Launcher" types out one character at a time, the whole word fades into a loading spinner
     * for a beat under two seconds, then the home screen takes over.
     */
    private fun playStartup() {
        try {
            MediaPlayer.create(this, R.raw.startup)?.apply {
                setVolume(0.55f, 0.55f)
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (_: Exception) {
            // Audio is optional; the visuals still play.
        }
        splash.text = "FSTV"
        splash.visibility = View.VISIBLE
        splash.alpha = 1f
        splash.scaleX = 0.92f
        splash.scaleY = 0.92f
        splash.animate().scaleX(1f).scaleY(1f).setDuration(900L)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        handler.postDelayed({ typeTail("FSTV", " | Launcher", 0) }, SPLASH_HOLD_MS)
    }

    private fun typeTail(head: String, tail: String, pos: Int) {
        if (pos > tail.length) {
            handler.postDelayed({ splashToSpinner() }, SPLASH_PAUSE_MS)
            return
        }
        splash.text = head + tail.substring(0, pos)
        handler.postDelayed({ typeTail(head, tail, pos + 1) }, SPLASH_TYPE_MS)
    }

    private fun splashToSpinner() {
        splash.animate().alpha(0f).setDuration(350L)
            .withEndAction { splash.visibility = View.GONE }.start()
        splashSpinner.visibility = View.VISIBLE
        splashSpinner.alpha = 0f
        splashSpinner.animate().alpha(1f).setDuration(350L).start()
        handler.postDelayed({
            splashSpinner.animate().alpha(0f).setDuration(400L)
                .withEndAction { splashSpinner.visibility = View.GONE }.start()
        }, SPLASH_SPINNER_MS)
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        updateClock()
        resetLaunchZoom()
        // Back on the home screen: nothing else needs to stay warm, so reclaim their RAM.
        trimBackground(null)
        if (cells.isNotEmpty()) loadWallpaper()
        cells.getOrNull(focusIndex)?.let { requestHero(it.entry, 1) }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        ticker.start()
        poke()
    }

    override fun onPause() {
        // Give the CPU and GPU straight to whatever app is opening.
        exitIdle()
        resumed = false
        ticker.stop()
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(idleCheck)
        handler.removeCallbacks(deepIdleCheck)
        super.onPause()
    }

    override fun onStop() {
        unregisterReceiver(clockReceiver)
        unregisterReceiver(packageReceiver)
        handler.removeCallbacks(reloadQuietly)
        releaseHeroMemory()
        wallpaper = null
        backdrop.clear()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) releaseHeroMemory()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        exitIdle()
        // Pressing Home while already home returns to the first app, like tvOS.
        if (resumed || intent.getBooleanExtra(EXTRA_GO_TO_START, false)) goToStart()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The press that ends idle mode only wakes the screen. It doesn't also move or open anything.
        if (idleMode && event.action == KeyEvent.ACTION_DOWN && wakesFromIdle(event.keyCode)) {
            exitIdle()
            swallowUntilKeyUp = true
            return true
        }
        if (swallowUntilKeyUp) {
            if (event.action == KeyEvent.ACTION_UP) swallowUntilKeyUp = false
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> return handleMove(event, -1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return handleMove(event, 1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> return handleMove(event, 0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> return handleMove(event, 0, 1)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_A -> {
                handleSelect(event)
                return true
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_DEL,              // Backspace
            KeyEvent.KEYCODE_FORWARD_DEL -> {  // Delete
                if (event.action == KeyEvent.ACTION_UP) handleBack()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP) setEditing(!editing)
                return true
            }
            // Tab / Shift+Tab: focus next / previous tile, like a web form.
            KeyEvent.KEYCODE_TAB -> return handleMove(event, if (event.isShiftPressed) -1 else 1, 0)
            // Page Up / Page Down: jump up or down one row.
            KeyEvent.KEYCODE_PAGE_UP -> return handleMove(event, 0, -1)
            KeyEvent.KEYCODE_PAGE_DOWN -> return handleMove(event, 0, 1)
            // Home / End: jump to first or last tile (keyboard power-user shortcut).
            KeyEvent.KEYCODE_MOVE_HOME -> return handleJump(event, 0)
            KeyEvent.KEYCODE_MOVE_END -> return handleJump(event, cells.lastIndex)
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleJump(event: KeyEvent, target: Int): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || cells.isEmpty() || target < 0) return true
        if (target == focusIndex) return true
        poke()
        val direction = if (target > focusIndex) 1 else -1
        changeFocus(target, direction, 0)
        ticker.wake()
        return true
    }

    private fun handleMove(event: KeyEvent, dx: Int, dy: Int): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || cells.isEmpty()) return true
        val now = SystemClock.uptimeMillis()
        if (event.repeatCount > 0 && now - lastMove < REPEAT_MS) return true
        lastMove = now
        poke()

        val target = neighbor(focusIndex, dx, dy)
        when {
            target == null -> cells[focusIndex].bump(dx, dy)
            editing -> moveTile(target)
            else -> changeFocus(target, dx, dy)
        }
        ticker.wake()
        return true
    }

    private fun neighbor(index: Int, dx: Int, dy: Int): Int? {
        val count = cells.size
        val columns = ui.columns
        val row = index / columns
        val column = index % columns
        if (dx != 0) {
            val next = index + dx
            return if (column + dx in 0 until columns && next in 0 until count) next else null
        }
        val nextRow = row + dy
        if (nextRow < 0) return null
        val next = nextRow * columns + column
        if (next < count) return next
        return if (nextRow == (count - 1) / columns) count - 1 else null
    }

    private fun changeFocus(target: Int, dx: Int, dy: Int) {
        cells[focusIndex].setFocusedTile(false, dx, dy)
        focusIndex = target
        cells[target].setFocusedTile(true, dx, dy)
        presentFocused(dx, dy)
    }

    private fun presentFocused(dx: Int, dy: Int) {
        val cell = cells.getOrNull(focusIndex) ?: return
        focusKey = cell.entry.key
        scroll.target = ui.scrollForRow(focusIndex / ui.columns)
        if (wallpaper == null && !idleMode) backdrop.show(cell.entry.backdrop)
        val direction = if (dx != 0) dx else if (dy != 0) dy else 1
        hero.showTitle(cell.entry.label, direction)
        requestHero(cell.entry, direction)
    }

    private fun moveTile(target: Int) {
        Collections.swap(cells, focusIndex, target)
        focusIndex = target
        cells.forEachIndexed { i, cell -> cell.place(ui.slotX(i), ui.slotY(i), snap = false) }
        scroll.target = ui.scrollForRow(focusIndex / ui.columns)
        repo.saveOrder(cells.map { it.entry.key })
    }

    private fun handleSelect(event: KeyEvent) {
        val cell = cells.getOrNull(focusIndex) ?: return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                poke()
                selectHeld = true
                longPressFired = false
                cell.setHeld(true)
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                cell.setHeld(false)
                ticker.wake()
                if (!selectHeld) return
                selectHeld = false
                if (longPressFired) return
                if (editing) setEditing(false) else open(cell)
            }
        }
    }

    private fun handleBack() {
        poke()
        when {
            editing -> setEditing(false)
            focusIndex >= ui.columns -> goToStart()
            else -> cells.getOrNull(focusIndex)?.bump(0, -1)
        }
        ticker.wake()
    }

    private fun open(cell: TileCell) {
        val entry = cell.entry
        if (entry.kind == Kind.MORE) {
            showAll = !showAll
            reload(cascade = true)
            return
        }
        val intent = entry.intent ?: return
        // Opening Amazon's launcher on purpose (the Fire TV Home tile): leave the guard off while
        // the user is actively using it. The guard watches for inactivity and bounces back after
        // 5 min of no foreground events on the launcher - so a long browse is fine, but an idle
        // screen comes home on its own. The 1-hour cap below is just a safety net in case the
        // idle check ever misses; a HOME press returns to FSTV at any time.
        if (intent.component?.packageName == AppRepository.STOCK_LAUNCHER) {
            GuardService.suppressUntil = SystemClock.uptimeMillis() + 60 * 60 * 1000L
        }
        // Free RAM from other apps so the one we are opening has room (helps on this low-RAM TV).
        trimBackground(intent.component?.packageName)
        // Pick the open animation. Our system/designed tiles (Settings, Appstore, Fire TV Home,
        // Inputs) carry a flat square iconRes instead of a real banner, so scaling them across
        // the screen looks terrible and runs into a slow Amazon cold-start splash on the other
        // side. For those, fade cleanly to black instead of stretching our icon; then hold the
        // launch until the black is fully opaque so the system splash takes over from solid
        // black, not from a half-transparent veil over the home screen.
        val flatTile = entry.iconRes != 0
        val useHero = hero.hasArt() && hero.alpha > 0.5f && !flatTile
        val lead = when {
            flatTile -> FADE_LAUNCH_MS
            useHero -> LAUNCH_LEAD_MS
            else -> 0L
        }
        when {
            flatTile -> playFadeLaunch()
            useHero -> playHeroLaunch(cell)
            else -> playLaunchZoom(cell)
        }
        // No system animation: it would reveal Amazon's launcher, which sits behind this screen.
        val launch = Runnable {
            try {
                startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
            } catch (_: Exception) {
                resetLaunchZoom()
                cell.bump(1, 0)
                ticker.wake()
            }
        }
        if (lead > 0L) root.postDelayed(launch, lead) else launch.run()
    }

    /**
     * Clean fade-to-black for tiles whose art is just a flat square icon (Settings, Appstore,
     * Fire TV Home, Inputs). Scaling those across the screen looks like a stretched icon; this
     * hides the rug-pull between our home screen and the slow Amazon splash that follows.
     */
    private fun playFadeLaunch() {
        launchVeil.visibility = View.VISIBLE
        launchVeil.alpha = 0f
        launchVeil.animate().alpha(1f).setDuration(FADE_LAUNCH_MS).start()
    }

    /** Grows the opened tile to fill the screen, then fades to black while the app loads. */
    private fun playLaunchZoom(cell: TileCell) {
        val startX = cell.translationX + ui.pad
        val startY = cell.translationY + ui.pad - scroll.value
        val cover = maxOf(ui.screenW / ui.tileW, ui.screenH / ui.tileH)
        launchTile.setImageBitmap(cell.entry.tile)
        launchTile.translationX = startX
        launchTile.translationY = startY
        launchTile.scaleX = 1f + ui.focusGrow
        launchTile.scaleY = 1f + ui.focusGrow
        launchTile.visibility = View.VISIBLE
        launchTile.animate()
            .translationX((ui.screenW - ui.tileW) / 2f)
            .translationY((ui.screenH - ui.tileH) / 2f)
            .scaleX(cover)
            .scaleY(cover)
            .setDuration(LAUNCH_ZOOM_MS)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
        launchVeil.visibility = View.VISIBLE
        launchVeil.alpha = 0f
        launchVeil.animate().alpha(1f).setStartDelay(LAUNCH_ZOOM_MS / 2).setDuration(LAUNCH_ZOOM_MS).start()
    }

    /**
     * Kills the background (cached) processes of the other apps on the home screen, freeing RAM for
     * the app being launched. Only cached processes are killed, never the foreground app, so it is
     * safe; it just means a backgrounded app will cold-start next time instead of staying warm.
     */
    private fun trimBackground(exceptPkg: String?) {
        worker.execute {
            val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return@execute
            for (cell in cells) {
                val pkg = cell.entry.intent?.component?.packageName ?: continue
                if (pkg == exceptPkg || pkg == packageName) continue
                try {
                    am.killBackgroundProcesses(pkg)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** The tvOS-style open: the big hero image expands to fill the screen, then fades to black. */
    private fun playHeroLaunch(cell: TileCell) {
        val art = heroCache.get(cell.entry.key)
        if (art == null) {
            playLaunchZoom(cell)
            return
        }
        heroLaunch.setImageBitmap(art)
        heroLaunch.pivotX = ui.heroArtW / 2f
        heroLaunch.pivotY = ui.heroArtH / 2f
        heroLaunch.scaleX = 1f
        heroLaunch.scaleY = 1f
        heroLaunch.translationY = 0f
        heroLaunch.alpha = 1f
        heroLaunch.visibility = View.VISIBLE
        val cover = maxOf(ui.screenW / ui.heroArtW, ui.screenH / ui.heroArtH)
        val centerY = ui.heroArtTop + ui.heroArtH / 2f
        heroLaunch.animate()
            .scaleX(cover).scaleY(cover)
            .translationY(ui.screenH / 2f - centerY)
            .setDuration(HERO_LAUNCH_MS)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
        launchVeil.visibility = View.VISIBLE
        launchVeil.alpha = 0f
        launchVeil.animate().alpha(1f).setStartDelay(HERO_LAUNCH_MS / 3).setDuration(HERO_LAUNCH_MS).start()
    }

    private fun resetLaunchZoom() {
        launchTile.animate().cancel()
        launchVeil.animate().cancel()
        launchTile.visibility = View.GONE
        launchTile.setImageDrawable(null)
        launchVeil.visibility = View.GONE
        heroLaunch.animate().cancel()
        heroLaunch.visibility = View.GONE
        heroLaunch.setImageDrawable(null)
    }

    private fun setEditing(on: Boolean) {
        if (cells.isEmpty()) return
        editing = on
        hint.animate().alpha(if (on) 1f else 0f).setDuration(HINT_MS).start()
        poke()
    }

    private fun goToStart() {
        if (cells.isEmpty()) return
        setEditing(false)
        if (focusIndex != 0) changeFocus(0, 0, -1)
        ticker.wake()
    }

    private fun reload(cascade: Boolean) {
        val generation = ++loadGeneration
        val all = showAll
        worker.execute {
            val entries = repo.load(all)
            handler.post { if (generation == loadGeneration) buildGrid(entries, cascade) }
        }
    }

    private fun buildGrid(entries: List<AppEntry>, cascade: Boolean) {
        grid.removeAllViews()
        cells.clear()
        heroCache.evictAll()
        entries.forEachIndexed { i, entry ->
            val cell = TileCell(this, entry, ui, shadow, i * 1.7f)
            cell.place(ui.slotX(i), ui.slotY(i), snap = true)
            if (cascade) cell.revealAfter(i * CASCADE_SECONDS)
            grid.addView(cell, FrameLayout.LayoutParams(ui.cellW, ui.cellH))
            cells.add(cell)
        }
        if (cells.isEmpty()) return

        focusIndex = cells.indexOfFirst { it.entry.key == focusKey }.coerceAtLeast(0)
        cells[focusIndex].setFocusedTile(true, 0, 0)
        presentFocused(0, 0)
        if (idleMode) cells.forEach { it.setDimmed(true) }
        poke()
    }

    private fun loadWallpaper() {
        val file = File(getExternalFilesDir(null) ?: return, WALLPAPER_FILE)
        worker.execute {
            val bitmap = decode(file)
            handler.post {
                wallpaper = bitmap
                applyHomeBackdrop()
            }
        }
    }

    private fun decode(file: File): Bitmap? = try {
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun applyHomeBackdrop() {
        if (idleMode) return
        val picture = wallpaper
        if (picture != null) {
            backdrop.motion = BackdropMotion.STILL
            backdrop.show(picture)
        } else {
            backdrop.motion = BackdropMotion.BLURRED
            cells.getOrNull(focusIndex)?.let { backdrop.show(it.entry.backdrop) }
        }
        ticker.wake()
    }

    private fun enterIdle() {
        if (idleMode || !resumed || cells.isEmpty()) return
        goToStart()
        idleMode = true
        handler.removeCallbacks(idleCheck)
        idleAmount.target = 1f
        cells.forEach { it.setDimmed(true) }
        slides = File(getExternalFilesDir(null), SLIDES_DIR)
            .listFiles { file -> file.name.endsWith(".jpg", ignoreCase = true) }
            ?.toList()?.shuffled().orEmpty()
        slideIndex = 0
        if (slides.isNotEmpty()) {
            backdrop.motion = BackdropMotion.SLIDESHOW
            showNextSlide()
        }
        ticker.wake()
    }

    private fun enterDeepIdle() {
        if (!resumed || cells.isEmpty()) return
        if (!idleMode) enterIdle()
        deepIdle = true
        deepAmount.target = 1f
        ticker.wake()
    }

    private fun exitIdle() {
        if (!idleMode && !deepIdle) return
        idleMode = false
        deepIdle = false
        handler.removeCallbacks(advanceSlide)
        handler.removeCallbacks(deepIdleCheck)
        idleAmount.target = 0f
        deepAmount.target = 0f
        cells.forEach { it.setDimmed(false) }
        applyHomeBackdrop()
        poke()
    }

    private fun showNextSlide() {
        if (!idleMode || slides.isEmpty()) return
        val file = slides[slideIndex++ % slides.size]
        worker.execute {
            val bitmap = decode(file)
            handler.post {
                if (idleMode) {
                    if (bitmap != null) backdrop.show(bitmap, slow = true)
                    handler.postDelayed(advanceSlide, SLIDE_MS)
                }
            }
        }
    }

    private fun wakesFromIdle(keyCode: Int): Boolean =
        keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            keyCode != KeyEvent.KEYCODE_VOLUME_MUTE

    private fun requestHero(entry: AppEntry, direction: Int) {
        val generation = ++heroGeneration
        heroCache.get(entry.key)?.let {
            hero.showArt(it, direction)
            return
        }
        worker.execute {
            if (generation != heroGeneration) return@execute
            val art = repo.heroArt(entry)
            handler.post {
                heroCache.put(entry.key, art)
                if (generation == heroGeneration) hero.showArt(art, direction)
            }
        }
    }

    private fun releaseHeroMemory() {
        heroGeneration++
        heroCache.evictAll()
        hero.clearArt()
    }

    private fun updateClock() {
        val now = Date()
        val time = DateFormat.getTimeFormat(this).format(now)
        val date = dateFormat.format(now)
        clock.text = time
        greeting.text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..4 -> "Good night"
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        cornerDate.text = date
        bigDate.text = date
        if (lastTime.isEmpty() || !resumed) {
            bigA.text = time
            bigB.text = time
        } else if (time != lastTime) {
            animateBigClock(time)
        }
        lastTime = time
    }

    /** Records user activity, which keeps the ambient motion running for a while. */
    private fun poke() {
        lastInput = SystemClock.uptimeMillis()
        handler.removeCallbacks(idleCheck)
        handler.removeCallbacks(deepIdleCheck)
        if (resumed && !idleMode) handler.postDelayed(idleCheck, IDLE_MS)
        if (resumed) handler.postDelayed(deepIdleCheck, DEEP_IDLE_MS)
        ticker.wake()
    }

    private fun onFrame(dt: Float, time: Float): Boolean {
        val idle = SystemClock.uptimeMillis() - lastInput
        val sway = idle < SWAY_IDLE_MS
        val ambient = idle < AMBIENT_IDLE_MS

        var moving = scroll.step(dt)
        moving = idleAmount.step(dt) || moving
        moving = deepAmount.step(dt) || moving
        val deep = deepAmount.value.coerceIn(0f, 1f)
        val inactive = idleAmount.value.coerceIn(0f, 1f)
        grid.translationY = -scroll.value
        val away = (scroll.value / ui.scrollForRow(1)).coerceIn(0f, 1f)
        hero.alpha = (1f - away) * (1f - inactive) * (1f - deep)
        hero.translationY = -away * ui.px(160f)
        grid.alpha = 1f - deep
        // Top widgets fade out as the grid scrolls up under them, and hand off to the big clock when idle.
        val topFade = (1f - inactive) * (1f - away)
        clock.alpha = topFade
        greeting.alpha = topFade
        cornerDate.alpha = topFade
        // Genuinely dark idle: a full-screen veil dims everything (inactive) then nearly blacks it out (near-off).
        deepVeil.alpha = INACTIVE_DIM * inactive + (DEEP_DIM - INACTIVE_DIM) * deep
        // Clock panel fades in and sits up top when inactive; near-off centers and enlarges it.
        clockPanel.alpha = inactive
        clockPanel.translationY = -ui.screenH * CLOCK_TOP_FRAC * (1f - deep)
        // Digital fades out and the analog grows in as the TV slides from inactive to near-off.
        digitalGroup.alpha = 1f - deep
        analog.alpha = deep
        val aScale = 1f + ANALOG_GROW * deep
        analog.scaleX = aScale
        analog.scaleY = aScale
        bigDate.scaleX = 1f + 0.3f * deep
        bigDate.scaleY = 1f + 0.3f * deep
        bigDate.translationY = ui.px(150f) + ui.px(360f) * deep
        if (deep > 0.01f) {
            val c = Calendar.getInstance()
            analog.setTime(c.get(Calendar.HOUR), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
        }

        for (cell in cells) moving = cell.tick(dt, time, sway, editing) || moving

        // Silver shelf under the focused app (hidden while editing or deeply idle).
        val fc = if (!editing && deep < 0.5f) cells.getOrNull(focusIndex) else null
        if (fc != null) {
            val cx = fc.translationX + ui.cellW / 2f
            val cyc = fc.translationY + grid.translationY + ui.cardH / 2f
            val baseline = cyc + ui.tileH * 0.5f * (1f + ui.focusGrow * fc.focusLevel) + ui.px(12f)
            shelf.update(cx, baseline, ui.tileW, fc.focusLevel * (1f - idleAmount.value.coerceIn(0f, 1f)))
        } else {
            shelf.update(0f, 0f, ui.tileW, 0f)
        }

        moving = backdrop.tick(dt, time, ambient) || moving
        if (ambient) hero.drift(time)
        return moving || ambient || idleMode
    }

    private fun makeText(size: Float, face: Typeface) = TextView(this).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        typeface = face
    }

    private fun makeBigClock() = TextView(this).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(188f))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        gravity = Gravity.CENTER
        letterSpacing = 0.01f
        setShadowLayer(ui.px(28f), 0f, ui.px(4f), 0x70000000.toInt())
    }

    /** Swaps the two time layers with a slide and fade, so each minute change animates. */
    private fun animateBigClock(text: CharSequence) {
        val outgoing = if (bigFront == 0) bigA else bigB
        bigFront = 1 - bigFront
        val incoming = if (bigFront == 0) bigA else bigB
        incoming.text = text
        outgoing.animate().cancel()
        outgoing.animate().alpha(0f).translationY(-ui.px(44f))
            .setDuration(360L).setInterpolator(DecelerateInterpolator(2f)).start()
        incoming.animate().cancel()
        incoming.alpha = 0f
        incoming.translationY = ui.px(50f)
        incoming.animate().alpha(1f).translationY(0f)
            .setDuration(460L).setInterpolator(DecelerateInterpolator(2.4f)).start()
    }

    private fun fullScreen() = FrameLayout.LayoutParams(MATCH, MATCH)

    companion object {
        const val EXTRA_GO_TO_START = "go_to_start"

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val HERO_CACHE_SIZE = 3
        private const val RELOAD_DEBOUNCE_MS = 500L
        private const val REPEAT_MS = 110L
        private const val LONG_PRESS_MS = 650L
        private const val HINT_MS = 220L
        private const val CASCADE_SECONDS = 0.045f
        private const val LAUNCH_ZOOM_MS = 260L
        private const val HERO_LAUNCH_MS = 380L
        private const val LAUNCH_LEAD_MS = 180L
        private const val FADE_LAUNCH_MS = 220L
        private const val SPLASH_HOLD_MS = 850L
        private const val SPLASH_TYPE_MS = 75L
        private const val SPLASH_PAUSE_MS = 400L
        private const val SPLASH_SPINNER_MS = 1500L
        private const val WALLPAPER_FILE = "wallpaper.jpg"
        private const val SLIDES_DIR = "screensaver"
        // TEST TIMERS: 10s to go inactive, 30s to near-off. Real values are 120_000 and 3_600_000.
        private const val IDLE_MS = 120_000L
        private const val DEEP_IDLE_MS = 3_600_000L
        private const val INACTIVE_DIM = 0.5f
        private const val DEEP_DIM = 0.9f
        private const val CLOCK_TOP_FRAC = 0.24f
        private const val ANALOG_GROW = 0.2f
        private const val SLIDE_MS = 14_000L

        // Motion winds down when nobody is using the TV, so the GPU can rest.
        private const val SWAY_IDLE_MS = 30_000L
        private const val AMBIENT_IDLE_MS = 120_000L
    }
}
