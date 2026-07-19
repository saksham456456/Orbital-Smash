package com.example.orbitalsmash // Make sure this matches your package name

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

    private val corePaint = Paint().apply { isAntiAlias = true }
    private val shieldPaint = Paint().apply { color = Color.parseColor("#FFFF00"); isAntiAlias = true }
    private val enemyPaint = Paint().apply { color = Color.parseColor("#FF1744"); isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 120f; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
    }
    private val subTextPaint = Paint().apply {
        color = Color.LTGRAY; textSize = 50f; textAlign = Paint.Align.CENTER; isAntiAlias = true
    }

    private var screenW = 0f
    private var screenH = 0f
    private var centerX = 0f
    private var centerY = 0f

    private var isGameOver = false
    private var score = 0
    private var frames = 0
    private var shakeFrames = 0

    private val coreRadius = 80f
    private val orbitRadius = 200f
    private val shieldRadius = 40f
    private var shieldAngle = 0.0
    private var shieldDirection = 1
    private var baseOrbitSpeed = 0.05

    // Trail effect for visual juice
    private val shieldHistory = CopyOnWriteArrayList<PointF>()

    private val enemies = CopyOnWriteArrayList<Enemy>()
    private var baseEnemySpeed = 6f
    private var enemySpawnRate = 50 // Spawns every 50 frames initially

    data class Enemy(var x: Float, var y: Float, val speedX: Float, val speedY: Float)

    init {
        setBackgroundColor(Color.parseColor("#121212"))
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
        shieldHistory.clear()
        isGameOver = false
        invalidate()
    }

    private fun updateGame() {
        if (isGameOver) return

        frames++

        // Difficulty scaling
        val difficultyMultiplier = 1f + (score * 0.05f)
        val currentOrbitSpeed = baseOrbitSpeed * difficultyMultiplier
        val currentEnemySpeed = baseEnemySpeed * difficultyMultiplier
        val currentSpawnRate = Math.max(10, (enemySpawnRate - score * 2))

        shieldAngle += currentOrbitSpeed * shieldDirection
        val shieldX = centerX + orbitRadius * cos(shieldAngle).toFloat()
        val shieldY = centerY + orbitRadius * sin(shieldAngle).toFloat()

        // Update trail history
        shieldHistory.add(0, PointF(shieldX, shieldY))
        if (shieldHistory.size > 8) shieldHistory.removeAt(shieldHistory.size - 1)

        if (frames % currentSpawnRate == 0) {
            spawnEnemy(currentEnemySpeed)
        }

        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()
            enemy.x += enemy.speedX
            enemy.y += enemy.speedY

            // Shield collision
            if (getDistance(enemy.x, enemy.y, shieldX, shieldY) < shieldRadius + 30f) {
                enemies.remove(enemy)
                score++
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                continue
            }

            // Core collision
            if (getDistance(enemy.x, enemy.y, centerX, centerY) < coreRadius + 30f) {
                isGameOver = true
                shakeFrames = 15
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private fun spawnEnemy(speed: Float) {
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val spawnDistance = Math.max(screenW, screenH)
        val startX = centerX + spawnDistance * cos(angle).toFloat()
        val startY = centerY + spawnDistance * sin(angle).toFloat()

        val dx = centerX - startX
        val dy = centerY - startY
        val distance = getDistance(startX, startY, centerX, centerY)

        enemies.add(Enemy(startX, startY, (dx / distance) * speed, (dy / distance) * speed))
    }

    private fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt(Math.pow((x2 - x1).toDouble(), 2.0) + Math.pow((y2 - y1).toDouble(), 2.0)).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateGame()

        canvas.save()

        // Screen shake logic
        if (shakeFrames > 0) {
            val shakeX = Random.nextInt(-15, 15).toFloat()
            val shakeY = Random.nextInt(-15, 15).toFloat()
            canvas.translate(shakeX, shakeY)
            shakeFrames--
        }

        // Color shift based on score
        val hue = (score * 10f) % 360f
        corePaint.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        canvas.drawCircle(centerX, centerY, coreRadius, corePaint)

        if (!isGameOver) {
            // Draw trail
            for (i in shieldHistory.indices) {
                shieldPaint.alpha = 255 - (i * 30)
                canvas.drawCircle(shieldHistory[i].x, shieldHistory[i].y, shieldRadius * (1f - i * 0.05f), shieldPaint)
            }
            shieldPaint.alpha = 255
        }

        // Draw enemies
        for (enemy in enemies) {
            canvas.drawRect(enemy.x - 30f, enemy.y - 30f, enemy.x + 30f, enemy.y + 30f, enemyPaint)
        }

        // Draw UI
        if (isGameOver) {
            canvas.drawText("GAME OVER", centerX, centerY - 100, textPaint)
            canvas.drawText("SCORE: $score", centerX, centerY + 50, textPaint)
            canvas.drawText("TAP TO RESTART", centerX, centerY + 150, subTextPaint)
        } else {
            textPaint.alpha = 50
            canvas.drawText(score.toString(), centerX, centerY + 40, textPaint)
            textPaint.alpha = 255
        }

        canvas.restore()
        invalidate()
    }
}