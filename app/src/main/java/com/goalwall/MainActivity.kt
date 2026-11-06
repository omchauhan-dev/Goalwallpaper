package com.goalwall

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.work.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class Goal(val id: Long, val title: String, val doneDates: MutableSet<String>)

class MainActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val styles = listOf("Heatmap", "Rings", "Chain", "Tree", "Constellation", "Mountain")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("goalwall", MODE_PRIVATE)
        scheduleDailyWallpaper()
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(12, 14, 20))
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(label("GOALWALL", 28f, Color.WHITE, true))
        page.addView(label("Your work, visualised every day.", 14f, 0xFFB7BCC8.toInt()))
        page.addView(space(20))

        status = label("", 16f, 0xFFE6E9F0.toInt(), true)
        page.addView(status)
        page.addView(space(18))

        page.addView(label("TODAY'S GOALS", 13f, 0xFF9DA6B8.toInt(), true))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)

        val input = EditText(this).apply {
            hint = "Add a goal (e.g. Operating Systems)"
            setHintTextColor(0xFF777F90.toInt())
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        page.addView(input)
        val add = Button(this).apply { text = "ADD GOAL" }
        page.addView(add)
        add.setOnClickListener {
            val title = input.text.toString().trim()
            if (title.isNotEmpty()) {
                val goals = getGoals().toMutableList()
                goals.add(Goal(System.currentTimeMillis(), title, mutableSetOf()))
                saveGoals(goals)
                input.text.clear()
                refresh()
            }
        }

        page.addView(space(18))
        page.addView(label("WALLPAPER STYLE", 13f, 0xFF9DA6B8.toInt(), true))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles)
        spinner.setSelection(styles.indexOf(prefs.getString("style", "Constellation")).coerceAtLeast(0))
        page.addView(spinner)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("style", styles[position]).apply()
            }
        }

        val apply = Button(this).apply { text = "APPLY TO LOCK SCREEN" }
        page.addView(apply)
        apply.setOnClickListener {
            try {
                val bitmap = WallpaperArt.create(this, getGoals(), getOverallStreak(), prefs.getString("style", "Constellation")!!)
                WallpaperManager.getInstance(this).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                Toast.makeText(this, "Lock-screen wallpaper updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not set wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        page.addView(space(12))
        page.addView(label("Streaks count consecutive days with at least one completed goal. Each goal also has its own streak.", 12f, 0xFF929AAA.toInt()))
        refresh()
    }

    private fun refresh() {
        val goals = getGoals()
        val today = LocalDate.now().toString()
        val streak = getOverallStreak()
        status.text = "🔥  $streak-day overall streak    •    ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
        list.removeAllViews()
        if (goals.isEmpty()) list.addView(label("No goals yet. Add your first goal below.", 14f, 0xFFB7BCC8.toInt()))
        goals.forEach { goal ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val check = CheckBox(this).apply {
                text = goal.title
                setTextColor(Color.WHITE)
                isChecked = today in goal.doneDates
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFF9DABFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val streakText = TextView(this).apply {
                text = "🔥${goalStreak(goal)}"
                setTextColor(0xFFFFC36A.toInt())
                textSize = 14f
            }
            check.setOnCheckedChangeListener { _, checked ->
                val updated = getGoals().toMutableList()
                val index = updated.indexOfFirst { it.id == goal.id }
                if (index >= 0) {
                    if (checked) updated[index].doneDates.add(today) else updated[index].doneDates.remove(today)
                    saveGoals(updated)
                    refresh()
                }
            }
            row.addView(check)
            row.addView(streakText)
            list.addView(row)
        }
    }

    private fun getGoals(): List<Goal> {
        val raw = prefs.getString("goals", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size != 3) null else Goal(parts[0].toLongOrNull() ?: 0L, parts[1].replace("%7C", "|"), parts[2].split(",").filter { it.isNotBlank() }.toMutableSet())
        }
    }

    private fun saveGoals(goals: List<Goal>) {
        val raw = goals.joinToString("\n") { "${it.id}|${it.title.replace("|", "%7C")}|${it.doneDates.joinToString(",")}" }
        prefs.edit().putString("goals", raw).apply()
    }

    private fun getOverallStreak(): Int {
        val dates = getGoals().flatMap { it.doneDates }.toSet()
        var d = LocalDate.now()
        if (d.toString() !in dates) d = d.minusDays(1)
        var count = 0
        while (d.toString() in dates) { count++; d = d.minusDays(1) }
        return count
    }

    private fun goalStreak(goal: Goal): Int {
        var d = LocalDate.now()
        if (d.toString() !in goal.doneDates) d = d.minusDays(1)
        var count = 0
        while (d.toString() in goal.doneDates) { count++; d = d.minusDays(1) }
        return count
    }

    private fun scheduleDailyWallpaper() {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(5)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(1000)
        val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("goalwall_daily", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 5, 0, 5) }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
}

class DailyWallpaperWorker(ctx: android.content.Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result = try {
        val prefs = applicationContext.getSharedPreferences("goalwall", Activity.MODE_PRIVATE)
        val raw = prefs.getString("goals", "") ?: ""
        val goals = if (raw.isBlank()) emptyList() else raw.split("\n").mapNotNull {
            val p = it.split("|", limit = 3)
            if (p.size != 3) null else Goal(p[0].toLongOrNull() ?: 0L, p[1].replace("%7C", "|"), p[2].split(",").filter { s -> s.isNotBlank() }.toMutableSet())
        }
        val dates = goals.flatMap { it.doneDates }.toSet()
        var d = LocalDate.now()
        if (d.toString() !in dates) d = d.minusDays(1)
        var streak = 0
        while (d.toString() in dates) { streak++; d = d.minusDays(1) }
        val bitmap = WallpaperArt.create(applicationContext, goals, streak, prefs.getString("style", "Constellation")!!)
        WallpaperManager.getInstance(applicationContext).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

object WallpaperArt {
    fun create(ctx: android.content.Context, goals: List<Goal>, streak: Int, style: String): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1080)
        val h = (dm.heightPixels * 1.15f).toInt().coerceAtLeast(1920)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(9, 13, 23))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val accent = Color.rgb(145, 165, 255)
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE  •  dd MMMM yyyy"))
        p.color = 0xFFB7C1D8.toInt(); p.textSize = w * .035f
        c.drawText(date.uppercase(), w*.09f, h*.24f, p)
        p.color = Color.WHITE; p.typeface = Typeface.create("sans-serif", Typeface.BOLD); p.textSize = w*.10f
        c.drawText("$streak DAY STREAK", w*.09f, h*.31f, p)
        p.typeface = Typeface.create("sans-serif", Typeface.NORMAL); p.textSize = w*.045f; p.color = accent
        c.drawText(style.uppercase(), w*.09f, h*.37f, p)
        drawStyle(c, p, w, h, style, streak, accent)
        p.color = Color.WHITE; p.textSize = w*.045f; p.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        c.drawText("TODAY'S FOCUS", w*.09f, h*.73f, p)
        p.typeface = Typeface.create("sans-serif", Typeface.NORMAL); p.textSize = w*.038f; p.color = 0xFFE0E5F0.toInt()
        goals.filter { LocalDate.now().toString() !in it.doneDates }.take(3).forEachIndexed { i, g ->
            c.drawText("•  ${g.title.take(34)}", w*.09f, h*(.77f + i*.035f), p)
        }
        p.color = 0xFF9DA8BC.toInt(); p.textSize = w*.03f
        c.drawText("SMALL STEPS. COMPOUND PROGRESS.", w*.09f, h*.91f, p)
        return bmp
    }

    private fun drawStyle(c: Canvas, p: Paint, w: Int, h: Int, style: String, streak: Int, accent: Int) {
        val cx = w/2f; val cy = h*.53f
        p.style = Paint.Style.STROKE; p.strokeWidth = w*.008f; p.color = 0xFF29344B.toInt()
        when (style) {
            "Heatmap" -> {
                val cell = w*.065f; val gap = w*.018f; val startX = w*.16f; val startY = h*.44f
                for (i in 0 until 35) {
                    val level = ((i + streak) % 5) / 4f
                    p.style = Paint.Style.FILL; p.color = if (i >= 35-streak.coerceAtMost(35)) accent else Color.argb((55+level*100).toInt(), 90, 110, 150)
                    val x = startX + (i%7)*(cell+gap); val y = startY + (i/7)*(cell+gap)
                    c.drawRoundRect(x,y,x+cell,y+cell,cell*.18f,cell*.18f,p)
                }
            }
            "Rings" -> for (i in 0..2) { p.color = if (i==0) accent else 0xFF46516B.toInt(); p.strokeWidth = w*.018f; c.drawArc(cx-w*(.11f+i*.07f),cy-w*(.11f+i*.07f),cx+w*(.11f+i*.07f),cy+w*(.11f+i*.07f),-90f, (streak*31f).coerceAtMost(360f),false,p) }
            "Chain" -> {
                p.color = accent; p.strokeWidth = w*.012f
                for (i in 0 until min(streak,8)) { val x=w*.18f+i*w*.09f; val y=cy+sin(i*.8)*w*.04; c.drawCircle(x.toFloat(),y.toFloat(),w*.025f,p) }
            }
            "Tree" -> {
                p.color=accent; p.strokeWidth=w*.012f; c.drawLine(cx,cy+w*.17f,cx,cy-w*.13f,p)
                for (i in 0 until min(streak,12)) { val y=cy+w*.10f-i*w*.019f; val side=if(i%2==0) 1 else -1; c.drawLine(cx,y,cx+side*w*.13f,y-w*.10f,p); c.drawCircle(cx+side*w*.13f,y-w*.10f,w*.018f,p) }
            }
            "Mountain" -> {
                p.style=Paint.Style.STROKE; p.color=accent; p.strokeWidth=w*.009f
                val path=Path().apply { moveTo(w*.12f,cy+w*.17f); lineTo(w*.42f,cy-w*.13f); lineTo(w*.54f,cy+w*.01f); lineTo(w*.72f,cy-w*.20f); lineTo(w*.90f,cy+w*.17f) }
                c.drawPath(path,p); c.drawCircle(w*(.12f+.78f*min(streak,30)/30f),cy+w*.17f-w*.30f*min(streak,30)/30f,w*.018f,p)
            }
            else -> {
                p.style=Paint.Style.FILL
                val rnd = java.util.Random(42)
                repeat(65) {
                    val x=rnd.nextFloat()*w; val y=h*.43f+rnd.nextFloat()*h*.22f
                    p.color=if(it<streak) accent else 0xFF53617D.toInt()
                    c.drawCircle(x,y,if(it<streak) w*.006f else w*.003f,p)
                }
                p.style=Paint.Style.STROKE; p.color=0xFF53617D.toInt(); p.strokeWidth=w*.002f
                for(i in 0 until min(streak,12)-1) { val x1=w*(.15f+(i%6)*.13f); val y1=cy-w*.07f+(i/6)*w*.12f; c.drawLine(x1,y1,x1+w*.13f,y1+w*.025f,p) }
            }
        }
        p.style=Paint.Style.FILL
    }
}
