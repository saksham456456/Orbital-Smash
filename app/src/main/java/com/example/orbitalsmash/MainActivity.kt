package com.example.orbitalsmash // Ensure this matches your package

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView(this))
    }
}

class GameView(context: Context) : View(context) {

    // --- High-Fidelity Paints with Neon Bloom (Shadow Layers) ---
    private val corePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        setShadowLayer(40f, 0f, 0f, Color.parseColor("#00E5FF")) // Neon Glow
    }
    private val shieldPaint = Paint().apply {
        color = Color.parseColor("#FFFF00")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 15f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(30f, 0f, 0f, Color.parseColor("#FFFF00"))
    }
    private val enemyPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
        setShadowLayer(25f, 0f, 0f, Color.parseColor("#FF1744"))
    }
    private val particlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 140f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, Color.WHITE)
    }
    private val subTextPaint = Paint().apply {
        color = Color.LTGRAY; textSize = 50f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    // --- State Variables ---
    private var screenW = 0f
    private var screenH = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var isGameOver = false
    private var score = 0
    private var frames = 0
    private var shakeFrames = 0

    // --- Core & Shield ---
    private val coreRadius = 60f
    private val orbitRadius = 220f
    private var shieldAngle = 0.0
    private var shieldDirection = 1
    private var baseOrbitSpeed = 0.06
    private val shieldHistory = CopyOnWriteArrayList<Float>() // Store angles for trail

    // --- Entities ---
    private val enemies = CopyOnWriteArrayList<Enemy>()
    private val particles = CopyOnWriteArrayList<Particle>()
    private var baseEnemySpeed = 7f
    private var enemySpawnRate = 45

    // --- Data Classes ---
    data class Enemy(var x: Float, var y: Float, val speedX: Float, val speedY: Float, var rotation: Float)
    data class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Int, val maxLife: Int, val color: Int)

    init {
        setBackgroundColor(Color.parseColor("#0B0B0B")) // Deeper black for better contrast
        // Required to render blur/glow effects properly on some devices
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat()
        screenH = h.toFloat()
        centerX = screenW / 2f
        centerY = screenH / 2f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isGameOver) {
                resetGame()
            } else {
                shieldDirection *= -1
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        return true
    }

    private fun resetGame() {
        score = 0
        frames = 0
        enemies.clear()
        particles.clear()
        shieldHistory.clear()
        isGameOver = false
        invalidate()
    }

    private fun updateGame() {
        if (isGameOver) {
            updateParticles() // Keep particles moving after death
            return
        }

        frames++
        val difficultyMultiplier = 1f + (score * 0.06f)
        val currentOrbitSpeed = baseOrbitSpeed * difficultyMultiplier
        val currentEnemySpeed = baseEnemySpeed * difficultyMultiplier
        val currentSpawnRate = Math.max(12, (enemySpawnRate - score * 2))

        // Update Shield
        shieldAngle += currentOrbitSpeed * shieldDirection
        shieldHistory.add(0, shieldAngle.toFloat())
        if (shieldHistory.size > 10) shieldHistory.removeAt(shieldHistory.size - 1)

        val shieldX = centerX + orbitRadius * cos(shieldAngle).toFloat()
        val shieldY = centerY + orbitRadius * sin(shieldAngle).toFloat()

        // Spawner
        if (frames % currentSpawnRate == 0) spawnEnemy(currentEnemySpeed)

        // Update Enemies
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()
            enemy.x += enemy.speedX
            enemy.y += enemy.speedY
            enemy.rotation += 5f // Spin the enemy

            // Shield Collision (Success)
            if (getDistance(enemy.x, enemy.y, shieldX, shieldY) < 60f) {
                spawnParticles(enemy.x, enemy.y, Color.parseColor("#FFFF00"), 15)
                enemies.remove(enemy)
                score++
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                continue
            }

            // Core Collision (Game Over)
            if (getDistance(enemy.x, enemy.y, centerX, centerY) < coreRadius + 20f) {
                spawnParticles(centerX, centerY, Color.parseColor("#00E5FF"), 40)
                isGameOver = true
                shakeFrames = 20
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        updateParticles()
    }

    private fun updateParticles() {
        val pIterator = particles.iterator()
        while (pIterator.hasNext()) {
            val p = pIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.life--
            if (p.life <= 0) particles.remove(p)
        }
    }

    private fun spawnEnemy(speed: Float) {
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val spawnDist = Math.max(screenW, screenH)
        val startX = centerX + spawnDist * cos(angle).toFloat()
        val startY = centerY + spawnDist * sin(angle).toFloat()
        val dist = getDistance(startX, startY, centerX, centerY)
        enemies.add(Enemy(startX, startY, ((centerX - startX) / dist) * speed, ((centerY - startY) / dist) * speed, 0f))
    }

    private fun spawnParticles(x: Float, y: Float, baseColor: Int, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val speed = Random.nextFloat() * 15f + 5f
            val life = Random.nextInt(20, 40)
            particles.add(Particle(x, y, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(), life, life, baseColor))
        }
    }

    private fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt(Math.pow((x2 - x1).toDouble(), 2.0) + Math.pow((y2 - y1).toDouble(), 2.0)).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateGame()

        canvas.save()
        // Screen Shake
        if (shakeFrames > 0) {
            canvas.translate(Random.nextInt(-20, 20).toFloat(), Random.nextInt(-20, 20).toFloat())
            shakeFrames--
        }

        // Draw Score Background
        if (!isGameOver) {
            textPaint.alpha = 20 // Very faint in background
            canvas.drawText(score.toString(), centerX, centerY + 50, textPaint)
            textPaint.alpha = 255
        }

        // Draw Dynamic Core
        if (!isGameOver) {
            val hue = (score * 8f) % 360f
            val coreColor = Color.HSVToColor(floatArrayOf(hue, 0.8f, 1f))
            corePaint.color = coreColor
            corePaint.setShadowLayer(40f + (sin(frames * 0.1).toFloat() * 10f), 0f, 0f, coreColor) // Pulsing glow
            canvas.drawCircle(centerX, centerY, coreRadius, corePaint)
        }

        // Draw Shield & Motion Trail (Vector-style Arc)
        if (!isGameOver) {
            val rect = RectF(centerX - orbitRadius, centerY - orbitRadius, centerX + orbitRadius, centerY + orbitRadius)

            // Trail
            for (i in shieldHistory.indices) {
                shieldPaint.alpha = 255 - (i * 25)
                val angleDeg = Math.toDegrees(shieldHistory[i].toDouble()).toFloat()
                canvas.drawArc(rect, angleDeg - 10f, 20f, false, shieldPaint)
            }
            shieldPaint.alpha = 255

            // Main Shield Arc
            val currentAngleDeg = Math.toDegrees(shieldAngle).toFloat()
            canvas.drawArc(rect, currentAngleDeg - 25f, 50f, false, shieldPaint)
        }

        // Draw Enemies (Vector-style rotating diamonds)
        for (enemy in enemies) {
            canvas.save()
            canvas.translate(enemy.x, enemy.y)
            canvas.rotate(enemy.rotation)
            val path = Path().apply {
                moveTo(0f, -30f)
                lineTo(30f, 0f)
                lineTo(0f, 30f)
                lineTo(-30f, 0f)
                close()
            }
            canvas.drawPath(path, enemyPaint)
            canvas.restore()
        }

        // Draw Particles
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = ((p.life.toFloat() / p.maxLife.toFloat()) * 255).toInt()
            val size = (p.life.toFloat() / p.maxLife.toFloat()) * 12f
            canvas.drawCircle(p.x, p.y, size, particlePaint)
        }

        // Draw UI
        if (isGameOver) {
            canvas.drawText("GAME OVER", centerX, centerY - 150, textPaint)
            canvas.drawText(score.toString(), centerX, centerY + 30, textPaint)
            canvas.drawText("TAP TO RESTART", centerX, centerY + 180, subTextPaint)
        }

        canvas.restore()
        invalidate()
    }
}
