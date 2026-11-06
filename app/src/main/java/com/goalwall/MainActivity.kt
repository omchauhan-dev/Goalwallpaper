package com.goalwall

import android.app.Activity
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
import kotlin.math.sin

data class Goal(val id: Long, val title: String, val dates: MutableSet<String>)

object GoalStore {
    private const val PREFS = "goalwall"
    fun read(context: Context): MutableList<Goal> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("goals", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { i ->
                val item = array.getJSONObject(i)
                val dates = item.getJSONArray("dates")
                Goal(item.getLong("id"), item.getString("title"),
                    MutableSet(dates.length()) { j -> dates.getString(j) })
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
    fun streak(dates: Set<String>): Int {
        var day = LocalDate.now()
        if (day.toString() !in dates) day = day.minusDays(1)
        var count = 0
        while (day.toString() in dates) { count++; day = day.minusDays(1) }
        return count
    }
    fun overallStreak(goals: List<Goal>) = streak(goals.flatMap { it.dates }.toSet())
}

class MainActivity : Activity() {
    private lateinit var goalList: LinearLayout
    private lateinit var summary: TextView
    private val styles = listOf("Heatmap", "Rings", "Chain", "Tree", "Constellation", "Mountain")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleRefresh()
        renderScreen()
    }

    private fun renderScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.rgb(12, 15, 24))
        }
        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)
        page.addView(text("GOALWALL", 28f, Color.WHITE, true))
        page.addView(text("Turn consistency into something you can see.", 14f, 0xFFB8C1D4.toInt()))
        summary = text("", 16f, 0xFFE8ECF5.toInt(), true)
        page.addView(summary)
        page.addView(gap(18))
        page.addView(text("YOUR DAILY GOALS", 13f, 0xFF9DAAC1.toInt(), true))
        goalList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(goalList)

        val input = EditText(this).apply {
            hint = "Add a goal, e.g. Study Operating Systems"
            setHintTextColor(0xFF7F899D.toInt())
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        page.addView(input)
        page.addView(Button(this).apply {
            text = "ADD GOAL"
            setOnClickListener {
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val goals = GoalStore.read(this@MainActivity)
                    goals.add(Goal(System.currentTimeMillis(), title, mutableSetOf()))
                    GoalStore.save(this@MainActivity, goals)
                    input.text.clear()
                    refreshGoals()
                }
            }
        })
        page.addView(gap(18))
        page.addView(text("WALLPAPER DESIGN", 13f, 0xFF9DAAC1.toInt(), true))
        val picker = Spinner(this)
        picker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles)
        val prefs = getSharedPreferences("goalwall", MODE_PRIVATE)
        picker.setSelection(styles.indexOf(prefs.getString("style", "Constellation")).coerceAtLeast(0))
        picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("style", styles[position]).apply()
            }
        }
        page.addView(picker)
        page.addView(Button(this).apply {
            text = "APPLY TO LOCK SCREEN"
            setOnClickListener { applyWallpaper() }
        })
        page.addView(gap(12))
        page.addView(text("Your goals stay on this device. Daily automatic updates are scheduled by Android and may be delayed by battery optimisation.", 12f, 0xFF8F9BB0.toInt()))
        refreshGoals()
    }

    private fun refreshGoals() {
        val goals = GoalStore.read(this)
        val today = LocalDate.now().toString()
        summary.text = "🔥  " + GoalStore.overallStreak(goals) + "-day streak   •   " +
            LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        goalList.removeAllViews()
        if (goals.isEmpty()) goalList.addView(text("No goals yet. Add your first one above.", 14f, 0xFFB8C1D4.toInt()))
        goals.forEach { goal ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val check = CheckBox(this).apply {
                text = goal.title
                setTextColor(Color.WHITE)
                isChecked = today in goal.dates
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFF9DAEFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            row.addView(check)
            row.addView(text("🔥 " + GoalStore.streak(goal.dates), 14f, 0xFFFFC36A.toInt(), true))
            goalList.addView(row)
            check.setOnCheckedChangeListener { _, checked ->
                val updated = GoalStore.read(this)
                val target = updated.firstOrNull { it.id == goal.id } ?: return@setOnCheckedChangeListener
                if (checked) target.dates.add(today) else target.dates.remove(today)
                GoalStore.save(this, updated)
                refreshGoals()
            }
        }
    }

    private fun applyWallpaper() {
        try {
            val style = getSharedPreferences("goalwall", MODE_PRIVATE).getString("style", "Constellation") ?: "Constellation"
            val goals = GoalStore.read(this)
            val bitmap = WallpaperRenderer.create(this, goals, GoalStore.overallStreak(goals), style)
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
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "goalwall_daily", ExistingPeriodicWorkPolicy.UPDATE, work
        )
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, 6, 0, 6)
        }
    private fun gap(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }
}

class DailyWallpaperWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val goals = GoalStore.read(applicationContext)
        val style = applicationContext.getSharedPreferences("goalwall", Context.MODE_PRIVATE)
            .getString("style", "Constellation") ?: "Constellation"
        val bitmap = WallpaperRenderer.create(applicationContext, goals, GoalStore.overallStreak(goals), style)
        WallpaperManager.getInstance(applicationContext).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

object WallpaperRenderer {
    fun create(context: Context, goals: List<Goal>, streak: Int, style: String): Bitmap {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1080)
        val height = (metrics.heightPixels * 1.15f).toInt().coerceAtLeast(1920)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(9, 13, 23))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val accent = Color.rgb(145, 165, 255)
        paint.color = 0xFFB7C1D8.toInt()
        paint.textSize = width * .034f
        canvas.drawText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE  •  dd MMMM yyyy")).uppercase(), width * .08f, height * .23f, paint)
        paint.color = Color.WHITE
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = width * .095f
        canvas.drawText(streak.toString() + " DAY STREAK", width * .08f, height * .31f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = width * .04f
        paint.color = accent
        canvas.drawText(style.uppercase(), width * .08f, height * .37f, paint)
        drawStyle(canvas, paint, width, height, style, streak, accent)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = width * .043f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("TODAY'S FOCUS", width * .08f, height * .73f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = width * .036f
        paint.color = 0xFFE0E5F0.toInt()
        goals.filter { LocalDate.now().toString() !in it.dates }.take(3).forEachIndexed { index, goal ->
            canvas.drawText("•  " + goal.title.take(32), width * .08f, height * (.77f + index * .035f), paint)
        }
        paint.color = 0xFF9DA8BC.toInt()
        paint.textSize = width * .027f
        canvas.drawText("SMALL STEPS. COMPOUND PROGRESS.", width * .08f, height * .92f, paint)
        return bitmap
    }

    private fun drawStyle(c: Canvas, p: Paint, w: Int, h: Int, style: String, streak: Int, accent: Int) {
        val cx = w / 2f
        val cy = h * .53f
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * .012f
        when (style) {
            "Heatmap" -> {
                val cell = w * .065f
                val gap = w * .018f
                for (i in 0 until 35) {
                    p.style = Paint.Style.FILL
                    p.color = if (i >= 35 - min(streak, 35)) accent else 0xFF39465F.toInt()
                    val x = w * .16f + (i % 7) * (cell + gap)
                    val y = h * .44f + (i / 7) * (cell + gap)
                    c.drawRoundRect(x, y, x + cell, y + cell, cell * .18f, cell * .18f, p)
                }
            }
            "Rings" -> for (i in 0..2) {
                p.color = if (i == 0) accent else 0xFF46516B.toInt()
                p.strokeWidth = w * .018f
                val radius = w * (.11f + i * .07f)
                c.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, -90f,
                    (streak * 31f).coerceAtMost(360f), false, p)
            }
            "Chain" -> {
                p.color = accent
                for (i in 0 until min(streak, 8)) {
                    val x = w * .18f + i * w * .09f
                    val y = cy + sin(i * .8).toFloat() * w * .04f
                    c.drawCircle(x, y, w * .025f, p)
                }
            }
            "Tree" -> {
                p.color = accent
                c.drawLine(cx, cy + w * .17f, cx, cy - w * .13f, p)
                for (i in 0 until min(streak, 12)) {
                    val y = cy + w * .10f - i * w * .019f
                    val side = if (i % 2 == 0) 1 else -1
                    c.drawLine(cx, y, cx + side * w * .13f, y - w * .10f, p)
                    c.drawCircle(cx + side * w * .13f, y - w * .10f, w * .018f, p)
                }
            }
            "Mountain" -> {
                p.color = accent
                p.strokeWidth = w * .009f
                val path = Path().apply {
                    moveTo(w * .12f, cy + w * .17f)
                    lineTo(w * .42f, cy - w * .13f)
                    lineTo(w * .54f, cy + w * .01f)
                    lineTo(w * .72f, cy - w * .20f)
                    lineTo(w * .90f, cy + w * .17f)
                }
                c.drawPath(path, p)
                val progress = min(streak, 30) / 30f
                c.drawCircle(w * (.12f + .78f * progress), cy + w * .17f - w * .30f * progress, w * .018f, p)
            }
            else -> {
                p.style = Paint.Style.FILL
                val random = java.util.Random(42)
                repeat(65) { index ->
                    val x = random.nextFloat() * w
                    val y = h * .43f + random.nextFloat() * h * .22f
                    p.color = if (index < streak) accent else 0xFF53617D.toInt()
                    c.drawCircle(x, y, if (index < streak) w * .006f else w * .003f, p)
                }
            }
        }
        p.style = Paint.Style.FILL
    }
}
