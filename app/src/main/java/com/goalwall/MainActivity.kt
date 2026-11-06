package com.goalwall

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class Goal(val id: Long, val title: String, val dates: MutableSet<String>)

data class Palette(val name: String, val background: Int, val card: Int, val accent: Int, val muted: Int) {
    companion object {
        val all = listOf(
            Palette("Midnight", Color.rgb(10, 15, 27), Color.rgb(22, 31, 49), Color.rgb(150, 171, 255), Color.rgb(151, 165, 190)),
            Palette("Sage", Color.rgb(12, 24, 22), Color.rgb(25, 43, 38), Color.rgb(135, 211, 169), Color.rgb(153, 181, 168)),
            Palette("Sunset", Color.rgb(28, 16, 20), Color.rgb(48, 29, 34), Color.rgb(255, 164, 126), Color.rgb(195, 157, 151)),
            Palette("Violet", Color.rgb(20, 14, 31), Color.rgb(36, 26, 53), Color.rgb(202, 157, 255), Color.rgb(174, 157, 194))
        )
        fun selected(context: Context): Palette {
            val name = context.getSharedPreferences("goalwall", Context.MODE_PRIVATE).getString("palette", "Midnight")
            return all.firstOrNull { it.name == name } ?: all[0]
        }
    }
}

object GoalStore {
    private const val PREFS = "goalwall"
    fun read(context: Context): MutableList<Goal> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("goals", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { i ->
                val item = array.getJSONObject(i)
                val dates = item.getJSONArray("dates")
                val completed = mutableSetOf<String>()
                repeat(dates.length()) { j -> completed.add(dates.getString(j)) }
                Goal(item.getLong("id"), item.getString("title"), completed)
            }
        } catch (_: Exception) { mutableListOf() }
    }
    fun save(context: Context, goals: List<Goal>) {
        val array = JSONArray()
        goals.forEach { goal ->
            val dates = JSONArray()
            goal.dates.sorted().forEach { dates.put(it) }
            array.put(JSONObject().put("id", goal.id).put("title", goal.title).put("dates", dates))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("goals", array.toString()).apply()
    }
    fun completedDates(goals: List<Goal>) = goals.flatMap { it.dates }.toSet()
    fun streak(dates: Set<String>): Int {
        var day = LocalDate.now()
        if (day.toString() !in dates) day = day.minusDays(1)
        var count = 0
        while (day.toString() in dates) { count++; day = day.minusDays(1) }
        return count
    }
    fun overallStreak(goals: List<Goal>) = streak(completedDates(goals))
}

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var goalList: LinearLayout
    private lateinit var heatmap: LinearLayout
    private lateinit var stats: TextView
    private lateinit var progress: TextView
    private lateinit var selectedDay: TextView
    private lateinit var paletteRow: LinearLayout
    private var chosenDate: LocalDate = LocalDate.now()
    private val styles = listOf("Heatmap", "Rings", "Chain", "Tree", "Constellation", "Mountain")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleRefresh()
        buildUi()
    }

    private fun buildUi() {
        val palette = Palette.selected(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 30)
            setBackgroundColor(palette.background)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(label("GOALWALL", 27f, Color.WHITE, true))
        root.addView(label("Make consistency visible.", 14f, palette.muted))
        root.addView(space(16))

        val hero = card(palette)
        hero.addView(label("YOUR MOMENTUM", 11f, palette.muted, true))
        stats = label("", 27f, Color.WHITE, true)
        hero.addView(stats)
        progress = label("", 14f, palette.accent, true)
        hero.addView(progress)
        root.addView(hero)
        root.addView(space(16))

        val heatCard = card(palette)
        heatCard.addView(label("YOUR ACTIVITY", 16f, Color.WHITE, true))
        heatCard.addView(label("Tap any day to inspect your progress.", 12f, palette.muted))
        heatmap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heatCard.addView(heatmap)
        selectedDay = label("", 12f, palette.accent)
        heatCard.addView(selectedDay)
        root.addView(heatCard)
        root.addView(space(16))

        root.addView(label("TODAY'S GOALS", 16f, Color.WHITE, true))
        root.addView(label("Check off what you complete. Long-press a goal to remove it.", 12f, palette.muted))
        goalList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(goalList)
        root.addView(Button(this).apply {
            text = "+  ADD A GOAL"
            setOnClickListener { addGoalDialog() }
        })
        root.addView(space(16))

        val designCard = card(palette)
        designCard.addView(label("MAKE IT YOURS", 16f, Color.WHITE, true))
        designCard.addView(label("Choose a colour mood for your wallpaper.", 12f, palette.muted))
        paletteRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        designCard.addView(paletteRow)
        val stylePicker = Spinner(this)
        stylePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles)
        val prefs = getSharedPreferences("goalwall", MODE_PRIVATE)
        stylePicker.setSelection(styles.indexOf(prefs.getString("style", "Heatmap")).coerceAtLeast(0))
        stylePicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("style", styles[position]).apply()
            }
        }
        designCard.addView(label("Wallpaper pattern", 12f, palette.muted))
        designCard.addView(stylePicker)
        designCard.addView(Button(this).apply {
            text = "PREVIEW & APPLY LOCK SCREEN"
            setOnClickListener { applyWallpaper() }
        })
        root.addView(designCard)
        root.addView(space(12))
        root.addView(label("Private by design • Your goals stay on this phone.", 11f, palette.muted))
        renderPaletteChoices()
        refresh()
    }

    private fun card(p: Palette) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(17, 15, 17, 15)
        setBackgroundColor(p.card)
    }

    private fun renderPaletteChoices() {
        paletteRow.removeAllViews()
        val current = Palette.selected(this)
        Palette.all.forEach { p ->
            val button = Button(this).apply {
                text = if (p.name == current.name) "✓ " + p.name else p.name
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(p.accent)
                setOnClickListener {
                    getSharedPreferences("goalwall", MODE_PRIVATE).edit().putString("palette", p.name).apply()
                    rebuild()
                }
            }
            paletteRow.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(2, 8, 2, 8) })
        }
    }

    private fun rebuild() {
        buildUi()
    }

    private fun addGoalDialog() {
        val input = EditText(this).apply { hint = "e.g. Study, exercise, read 10 pages" }
        AlertDialog.Builder(this)
            .setTitle("Add a goal")
            .setMessage("Keep it simple and specific.")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val goals = GoalStore.read(this)
                    goals.add(Goal(System.currentTimeMillis(), title, mutableSetOf()))
                    GoalStore.save(this, goals)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refresh() {
        val goals = GoalStore.read(this)
        val today = LocalDate.now().toString()
        val completed = goals.count { today in it.dates }
        stats.text = GoalStore.overallStreak(goals).toString() + " day streak  🔥"
        progress.text = if (goals.isEmpty()) "Add your first goal to begin." else "$completed of ${goals.size} goals completed today"
        renderHeatmap(goals)
        renderGoals(goals)
    }

    private fun renderHeatmap(goals: List<Goal>) {
        heatmap.removeAllViews()
        val p = Palette.selected(this)
        val end = LocalDate.now()
        val start = end.minusDays(34)
        val dates = (0..34).map { start.plusDays(it.toLong()) }
        val weeks = dates.chunked(7)
        weeks.forEach { week ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            week.forEach { date ->
                val count = goals.count { date.toString() in it.dates }
                val tile = TextView(this).apply {
                    text = date.dayOfMonth.toString()
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(if (count > 0) Color.WHITE else p.muted)
                    setBackgroundColor(heatColor(p, count))
                    contentDescription = "${date.format(DateTimeFormatter.ofPattern("d MMM"))}: $count completed goals"
                    setOnClickListener {
                        chosenDate = date
                        val detail = goals.count { date.toString() in it.dates }
                        selectedDay.text = date.format(DateTimeFormatter.ofPattern("EEE, d MMM")) + "  •  $detail goals completed"
                    }
                }
                val lp = LinearLayout.LayoutParams(0, dp(39), 1f)
                lp.setMargins(2, 3, 2, 3)
                row.addView(tile, lp)
            }
            repeat(7 - week.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(39), 1f))
            }
            heatmap.addView(row)
        }
        selectedDay.text = chosenDate.format(DateTimeFormatter.ofPattern("EEE, d MMM")) +
            "  •  " + goals.count { chosenDate.toString() in it.dates } + " goals completed"
    }

    private fun heatColor(p: Palette, count: Int): Int = when (count) {
        0 -> Color.rgb(47, 56, 73)
        1 -> blend(p.card, p.accent, .40f)
        2 -> blend(p.card, p.accent, .68f)
        else -> p.accent
    }

    private fun blend(a: Int, b: Int, amount: Float): Int {
        fun channel(x: Int, y: Int) = (Color.red(x) + (Color.red(y) - Color.red(x)) * amount).toInt()
        fun green(x: Int, y: Int) = (Color.green(x) + (Color.green(y) - Color.green(x)) * amount).toInt()
        fun blue(x: Int, y: Int) = (Color.blue(x) + (Color.blue(y) - Color.blue(x)) * amount).toInt()
        return Color.rgb(channel(a,b), green(a,b), blue(a,b))
    }

    private fun renderGoals(goals: List<Goal>) {
        goalList.removeAllViews()
        val today = LocalDate.now().toString()
        if (goals.isEmpty()) {
            goalList.addView(label("Nothing here yet. Add one small goal to get started.", 13f, Palette.selected(this).muted))
            return
        }
        goals.forEach { goal ->
            val p = Palette.selected(this)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10, 8, 10, 8)
                setBackgroundColor(p.card)
            }
            val check = CheckBox(this).apply {
                text = goal.title
                textSize = 14f
                setTextColor(Color.WHITE)
                isChecked = today in goal.dates
                buttonTintList = android.content.res.ColorStateList.valueOf(p.accent)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val streak = label("🔥 ${GoalStore.streak(goal.dates)}", 12f, p.accent, true)
            row.addView(check)
            row.addView(streak)
            row.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Remove goal?")
                    .setMessage("Remove '${goal.title}' and its saved history?")
                    .setNegativeButton("Keep", null)
                    .setPositiveButton("Remove") { _, _ ->
                        val updated = GoalStore.read(this).filterNot { it.id == goal.id }
                        GoalStore.save(this, updated)
                        refresh()
                    }.show()
                true
            }
            check.setOnCheckedChangeListener { _, checked ->
                val updated = GoalStore.read(this)
                val target = updated.firstOrNull { it.id == goal.id } ?: return@setOnCheckedChangeListener
                if (checked) target.dates.add(today) else target.dates.remove(today)
                GoalStore.save(this, updated)
                refresh()
            }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, dp(5), 0, dp(5))
            goalList.addView(row, lp)
        }
    }

    private fun applyWallpaper() {
        try {
            val style = getSharedPreferences("goalwall", MODE_PRIVATE).getString("style", "Heatmap") ?: "Heatmap"
            val goals = GoalStore.read(this)
            val bitmap = WallpaperRenderer.create(this, goals, GoalStore.overallStreak(goals), style, Palette.selected(this))
            WallpaperManager.getInstance(this).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            Toast.makeText(this, "Lock-screen wallpaper updated", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Wallpaper update failed: " + error.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleRefresh() {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(5)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(1000)
        val work = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("goalwall_daily", ExistingPeriodicWorkPolicy.UPDATE, work)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, 5, 0, 5)
        }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

class DailyWallpaperWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val goals = GoalStore.read(applicationContext)
        val prefs = applicationContext.getSharedPreferences("goalwall", Context.MODE_PRIVATE)
        val style = prefs.getString("style", "Heatmap") ?: "Heatmap"
        val bitmap = WallpaperRenderer.create(applicationContext, goals, GoalStore.overallStreak(goals), style, Palette.selected(applicationContext))
        WallpaperManager.getInstance(applicationContext).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

object WallpaperRenderer {
    fun create(context: Context, goals: List<Goal>, streak: Int, style: String, palette: Palette): Bitmap {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1080)
        val height = (metrics.heightPixels * 1.15f).toInt().coerceAtLeast(1920)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = width * .095f
        canvas.drawText(streak.toString() + " DAY STREAK", width * .08f, height * .44f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = width * .04f
        paint.color = palette.accent
        canvas.drawText(style.uppercase(), width * .08f, height * .50f, paint)
        drawHeatmap(canvas, paint, width, height, goals, palette)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = width * .043f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("TODAY'S FOCUS", width * .08f, height * .80f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = width * .036f
        paint.color = 0xFFE0E5F0.toInt()
        goals.filter { LocalDate.now().toString() !in it.dates }.take(3).forEachIndexed { index, goal ->
            canvas.drawText("•  " + goal.title.take(32), width * .08f, height * (.84f + index * .032f), paint)
        }
        paint.color = palette.muted
        paint.textSize = width * .027f
        canvas.drawText("SMALL STEPS. COMPOUND PROGRESS.", width * .08f, height * .96f, paint)
        return bitmap
    }

    private fun drawHeatmap(c: Canvas, p: Paint, w: Int, h: Int, goals: List<Goal>, palette: Palette) {
        val start = LocalDate.now().minusDays(34)
        val cell = w * .072f
        val gap = w * .018f
        val gridWidth = 7 * cell + 6 * gap
        val startX = (w - gridWidth) / 2f
        val startY = h * .56f
        val dates = GoalStore.completedDates(goals)
        for (i in 0 until 35) {
            val date = start.plusDays(i.toLong())
            val count = goals.count { date.toString() in it.dates }
            p.style = Paint.Style.FILL
            p.color = when (count) {
                0 -> 0xFF35415C.toInt()
                1 -> palette.accent
                else -> blendColor(palette.accent, Color.WHITE, min(count, 3) * .13f)
            }
            val x = startX + (i % 7) * (cell + gap)
            val y = startY + (i / 7) * (cell + gap)
            c.drawRoundRect(x, y, x + cell, y + cell, cell * .22f, cell * .22f, p)
        }
    }

    private fun blendColor(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )
}
