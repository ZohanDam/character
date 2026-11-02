package com.zohandam.character

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var page: LinearLayout
    private var screen = "home"
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val midnightRefresh = Runnable { if (!isFinishing) { render(); scheduleRefresh() } }
    private var checking = false
    private var release: Release? = null
    private var updateStatus = "Check GitHub for the latest release."
    private var updateFailed = false
    private val ink get() = getColor(R.color.ink)
    private val muted get() = getColor(R.color.muted)
    private val accent get() = getColor(R.color.accent)
    private val soft get() = getColor(R.color.soft)
    private val surface get() = getColor(R.color.surface)

    override fun attachBaseContext(base: Context) {
        val selected = base.getSharedPreferences("character", MODE_PRIVATE).getInt("theme", 0)
        if (selected == 0) { super.attachBaseContext(base); return }
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (selected == 2) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        screen = savedInstanceState?.getString("screen") ?: "home"
        Reminders.channel(this)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(0) {
                if (screen == "home") finish()
                else navigate(if (screen == "info") "settings" else "home")
            }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) { Reminders.schedule(this); render(); scheduleRefresh() }
    }

    override fun onPause() { handler.removeCallbacks(midnightRefresh); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("screen", screen); super.onSaveInstanceState(outState) }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (screen == "home") super.onBackPressed()
        else navigate(if (screen == "info") "settings" else "home")
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(midnightRefresh)
        val now = java.time.ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        handler.postDelayed(midnightRefresh, java.time.Duration.between(now, next).toMillis() + 100)
    }

    private fun navigate(destination: String) { screen = destination; render() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun space(parent: LinearLayout, size: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(size))) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun rounded(color: Int, radius: Int = 22, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), getColor(R.color.line))
    }
    private fun clickable(view: View, color: Int, action: () -> Unit) {
        view.background = RippleDrawable(ColorStateList.valueOf(0x22365F48), rounded(color), null)
        view.isClickable = true; view.isFocusable = true
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
        view.setOnClickListener { action() }
    }
    private fun label(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun smallLabel(value: String) = label(value, 11f, accent, true).apply { letterSpacing = 0.13f }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit): TextView =
        label(value, 15f, if (primary) getColor(R.color.background) else accent, true).apply {
            gravity = Gravity.CENTER; minHeight = dp(52)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            clickable(this, if (primary) accent else soft, action)
        }
    private fun card(): LinearLayout = column().apply {
        background = rounded(surface, 24, true)
        setPadding(dp(22), dp(22), dp(22), dp(22))
    }

    private fun render() {
        val root = column().apply { setBackgroundColor(getColor(R.color.background)) }
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(12))
        }
        if (screen != "home") {
            val back = label("‹", 34f, accent).apply {
                gravity = Gravity.CENTER; contentDescription = "Back"
                clickable(this, getColor(R.color.background)) { onBackPressed() }
            }
            header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        header.addView(label(when (screen) {
            "traits" -> "The twelve traits"
            "settings" -> "Settings"
            "info" -> "About Character"
            else -> "Character"
        }, 21f, ink, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (screen == "home") {
            header.addView(button("Settings") { navigate("settings") })
        }
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        page = column().apply { setPadding(dp(24), dp(12), dp(24), dp(28)) }
        scroll.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        when (screen) { "traits" -> traits(); "settings" -> settings(); "info" -> info(); else -> home() }
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun home() {
        val today = LocalDate.now()
        val all = store.traits()
        val index = Schedule.traitIndex(store.anchor, today, all.size)
        val trait = all[index]
        val sunday = Schedule.sunday(today)
        val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        space(page, 12)
        page.addView(smallLabel("ONE TRAIT. ONE WEEK."))
        space(page, 22)
        val focus = card()
        val weekRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        weekRow.addView(smallLabel("THIS WEEK"), LinearLayout.LayoutParams(0, -2, 1f))
        weekRow.addView(label("${index + 1} / ${all.size}", 13f, muted))
        focus.addView(weekRow)
        space(focus, 8)
        focus.addView(label("${sunday.format(dateFormat)} – ${sunday.plusDays(6).format(dateFormat)}", 14f, muted))
        space(focus, 38)
        focus.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_notification); imageTintList = ColorStateList.valueOf(accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        space(focus, 20)
        focus.addView(label(trait.name, 38f).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            setLineSpacing(0f, 1.05f)
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        space(focus, 20)
        focus.addView(label(trait.definition, 19f).apply { setLineSpacing(dp(5).toFloat(), 1.12f) })
        space(focus, 34)
        val line = View(this).apply { setBackgroundColor(getColor(R.color.line)) }
        focus.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        space(focus, 16)
        focus.addView(label("A new trait every Sunday", 13f, muted))
        page.addView(focus)
        space(page, 18)
        page.addView(button("View all 12 traits") { navigate("traits") })
        space(page, 20)
        val reminderText = when {
            !store.enabled -> "Set a daily reminder"
            !Reminders.permitted(this) -> "Reminders need notification permission"
            else -> "${store.times.size} daily reminder${if (store.times.size == 1) "" else "s"} · ${store.times.joinToString(" · ") { time(it) }}"
        }
        page.addView(label(reminderText, 13f, muted).apply {
            gravity = Gravity.CENTER; minHeight = dp(48)
            clickable(this, getColor(R.color.background)) { navigate("settings") }
        })
    }

    private fun traits() {
        page.addView(label("A repeating twelve-week rhythm. Tap a trait to edit its name or definition.", 15f, muted))
        space(page, 20)
        val current = Schedule.traitIndex(store.anchor, LocalDate.now())
        store.traits().forEachIndexed { index, trait ->
            val row = column().apply { setPadding(dp(18), dp(17), dp(18), dp(17)) }
            clickable(row, if (index == current) soft else surface) { editTrait(index) }
            row.addView(smallLabel("${(index + 1).toString().padStart(2, '0')}${if (index == current) "  ·  THIS WEEK" else ""}"))
            space(row, 6)
            row.addView(label(trait.name, 19f, ink, true))
            space(row, 5)
            row.addView(label(trait.definition, 14f, muted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            page.addView(row)
            space(page, 10)
        }
    }

    private fun editTrait(index: Int) {
        val original = store.traits()[index]
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        form.addView(label("Name", 13f, muted))
        val name = EditText(this).apply {
            setText(original.name); isSingleLine = true; setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(100))
        }
        form.addView(name)
        space(form, 12)
        form.addView(label("Definition", 13f, muted))
        val definition = EditText(this).apply {
            setText(original.definition); minLines = 4; maxLines = 8; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(4000))
        }
        form.addView(definition)
        val scroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this).setTitle("Edit trait").setView(scroll)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                val body = definition.text.toString().trim()
                when {
                    title.isBlank() -> name.error = "Enter a trait name"
                    body.isBlank() -> definition.error = "Enter a definition"
                    else -> {
                        store.saveTrait(index, Trait(title, body))
                        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(definition.windowToken, 0)
                        dialog.dismiss(); render(); toast("Trait saved")
                    }
                }
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun settings() {
        page.addView(smallLabel("DAILY REMINDERS"))
        space(page, 12)
        val reminders = card()
        val toggle = Switch(this).apply {
            setText(R.string.daily_reminders); textSize = 17f; setTextColor(ink)
            minHeight = dp(48); isChecked = store.enabled
            setOnCheckedChangeListener { _, checked ->
                store.enabled = checked
                if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
                }
                Reminders.schedule(this@MainActivity); render()
            }
        }
        reminders.addView(toggle)
        space(reminders, 4)
        reminders.addView(label("${store.times.size} reminder${if (store.times.size == 1) "" else "s"} per day. Each time repeats daily.", 14f, muted))
        space(reminders, 18)
        store.times.forEach { minutes ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val clock = button(time(minutes)) { pickTime(minutes) }
            clock.gravity = Gravity.CENTER_VERTICAL
            row.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
            if (store.times.size > 1) {
                row.addView(button("Remove") {
                    store.times = store.times.filter { it != minutes }
                    Reminders.schedule(this); render()
                }.apply { contentDescription = "Remove reminder at ${time(minutes)}" })
            }
            reminders.addView(row); space(reminders, 8)
        }
        reminders.addView(button("+ Add reminder time") { pickTime(null) })
        space(reminders, 12)
        reminders.addView(label("Default: once a day at 8:00 AM. Times follow your phone’s local time.", 12f, muted))
        page.addView(reminders)
        if (store.enabled && !Reminders.permitted(this)) {
            space(page, 12)
            page.addView(button("Allow notifications") {
                if (Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                    !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
                } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
        }
        if (Build.VERSION.SDK_INT >= 31 && store.enabled && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            space(page, 14)
            page.addView(label("Android may delay reminders. Allow precise timing to use your selected times more closely.", 13f, muted))
            space(page, 8)
            page.addView(button("Allow precise timing") {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            })
        }
        space(page, 28)
        page.addView(smallLabel("APPEARANCE"))
        space(page, 12)
        val appearance = card()
        val themes = arrayOf("System default", "Light", "Dark")
        appearance.addView(label("Theme", 17f, ink, true))
        space(appearance, 8)
        appearance.addView(button(themes.getOrElse(store.theme) { themes[0] }) {
            AlertDialog.Builder(this).setTitle("Appearance")
                .setSingleChoiceItems(themes, store.theme) { dialog, selected ->
                    store.theme = selected; dialog.dismiss(); recreate()
                }.setNegativeButton("Cancel", null).show()
        })
        page.addView(appearance)
        space(page, 24)
        page.addView(button("About Character  ·  ${BuildConfig.VERSION_NAME}") { navigate("info") })
    }

    private fun pickTime(previous: Int?) {
        val suggested = previous ?: listOf(12 * 60, 18 * 60, 20 * 60).firstOrNull { it !in store.times } ?: 9 * 60
        TimePickerDialog(this, { _, hour, minute ->
            val chosen = hour * 60 + minute
            if (chosen != previous && chosen in store.times) { toast("That reminder time already exists"); return@TimePickerDialog }
            store.times = (store.times.filter { it != previous } + chosen).sorted()
            Reminders.schedule(this); render()
        }, suggested / 60, suggested % 60, android.text.format.DateFormat.is24HourFormat(this)).show()
    }

    private fun time(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).format(
        DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a", Locale.getDefault()))

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Reminders.schedule(this); render()
    }

    private fun info() {
        space(page, 8)
        page.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(76), dp(76)))
        space(page, 20)
        page.addView(label("Character", 36f).apply { typeface = Typeface.create("serif", Typeface.NORMAL) })
        space(page, 6)
        page.addView(label("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 14f, muted))
        space(page, 22)
        page.addView(label("A quiet weekly reminder of the character you want to cultivate. Focus on one of twelve traits each week, read its meaning, and choose daily reminders that fit your day.", 17f))
        space(page, 14)
        page.addView(label("The cycle advances every Sunday. Trait names and definitions are yours to edit. Your settings and edits stay on this device; internet access is used only for update checks and downloads you request.", 14f, muted))
        space(page, 28)
        val updates = card()
        updates.addView(smallLabel("APP UPDATES"))
        space(updates, 12)
        updates.addView(label(updateStatus, 15f, if (updateFailed) muted else ink))
        space(updates, 14)
        updates.addView(button(if (checking) "Checking…" else "Check for updates", primary = true) { checkUpdates() }.apply {
            isEnabled = !checking; alpha = if (checking) 0.6f else 1f
        })
        release?.takeIf { Versions.newer(it.version, BuildConfig.VERSION_NAME) }?.let { latest ->
            space(updates, 10)
            if (latest.notes.isNotBlank()) {
                updates.addView(label(latest.notes.take(2000), 13f, muted)); space(updates, 12)
            }
            if (latest.apk.isNotEmpty()) updates.addView(button("Download version ${latest.version}") { download(latest) })
            space(updates, 8)
            updates.addView(button("View release on GitHub") { browse(latest.page) })
        }
        space(updates, 10)
        updates.addView(label("Updates download from GitHub. Installing an update keeps your traits, schedule, and cycle.", 12f, muted))
        page.addView(updates)
        space(page, 16)
        page.addView(button("Source code on GitHub") { browse(Updates.repositoryUrl) })
    }

    private fun checkUpdates() {
        if (checking) return
        checking = true; updateFailed = false; updateStatus = "Checking GitHub…"; render()
        worker.execute {
            val result = runCatching { Updates.latest() }
            handler.post {
                if (isFinishing || isDestroyed) return@post
                checking = false
                result.onSuccess { latest ->
                    release = latest
                    updateStatus = when {
                        latest == null -> "No published release is available yet."
                        Versions.newer(latest.version, BuildConfig.VERSION_NAME) && latest.apk.isEmpty() -> "Version ${latest.version} is available. Its APK has not been published yet."
                        Versions.newer(latest.version, BuildConfig.VERSION_NAME) -> "Version ${latest.version} is available."
                        else -> "You’re up to date. Version ${BuildConfig.VERSION_NAME} is installed."
                    }
                }.onFailure {
                    updateFailed = true
                    updateStatus = if (it is java.io.IOException) "Could not connect. Check your internet connection and try again." else it.message ?: "Could not check for updates. Try again."
                }
                if (screen == "info") render()
            }
        }
    }

    private fun download(latest: Release) {
        AlertDialog.Builder(this).setTitle("Download version ${latest.version}?")
            .setMessage("The APK will download from GitHub to Downloads. Open the downloaded file to install it. Android may ask you to allow installation from that source.")
            .setNegativeButton("Cancel", null).setPositiveButton("Download") { _, _ ->
                runCatching {
                    val request = DownloadManager.Request(Uri.parse(latest.apk))
                        .setTitle("Character ${latest.version}")
                        .setDescription("App update from GitHub")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Character-${latest.version}.apk")
                    getSystemService(DownloadManager::class.java).enqueue(request)
                    toast("Download started. Open it from Downloads when complete.")
                }.onFailure { browse(latest.apk) }
            }.show()
    }

    private fun browse(url: String) { open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    private fun open(intent: Intent) { runCatching { startActivity(intent) }.onFailure { toast("No app is available to open this.") } }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
