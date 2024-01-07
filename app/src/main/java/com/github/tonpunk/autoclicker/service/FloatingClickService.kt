package com.github.tonpunk.autoclicker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import com.github.tonpunk.Settings.clicksTimeLeft
import com.github.tonpunk.Settings.startAgainIn
import com.github.tonpunk.autoclicker.TouchAndDragListener
import com.github.tonpunk.autoclicker.dp2px
import com.github.tonpunk.autoclicker.logd
import com.github.tonpunk.autoclicker.millsToSec
import com.github.tonpunk.autoclicker.toPx
import com.github.tonpunk.autoclicker.toTime
import com.github.tonpunk.autoclicker.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random


/**
 * Created on 2018/9/28.
 * By nesto
 */
class FloatingClickService: Service() {
    private lateinit var manager: WindowManager
    private lateinit var startButton: View
    private lateinit var timeLeftTextView: View
    private lateinit var clicksAgainInTextView: View
    private lateinit var startButtonParams: WindowManager.LayoutParams
    private var xForRecord = 0
    private var yForRecord = 0
    private val location = IntArray(2)
    private var startDragDistance: Int = 0

    private val clickSpeed = 80L

    private var clickTimer: Timer? = null
    private var outerTimer: Timer? = null

    private var periodicTimerJob: Job? = null
    private var timeLeftJob: Job? = null

    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val screenHeight = Resources.getSystem().displayMetrics.heightPixels

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startDragDistance = dp2px(10f)
        startButton = LayoutInflater.from(this).inflate(R.layout.start_button, null)
        timeLeftTextView = LayoutInflater.from(this).inflate(R.layout.time_textview, null)
        clicksAgainInTextView = LayoutInflater.from(this).inflate(R.layout.time_textview, null)

        //setting the layout parameters
        val overlayParam =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
        startButtonParams = WindowManager.LayoutParams(
            80.toPx(),
            80.toPx(),
            overlayParam,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

        val tvX = 0
        val tvY = (-screenHeight / 2) + 74.toPx()
        val tvTimeLeftParam = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tvX,
            tvY,
            overlayParam,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)


        //getting windows services and adding the floating view to it
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        manager.addView(startButton, startButtonParams)
        (timeLeftTextView as? TextView)?.text =
            "Time left until collection:\n ${startAgainIn.millsToSec().toTime()}"
        (clicksAgainInTextView as? TextView)?.text =
            "Collecting time left:\n ${clicksTimeLeft.millsToSec().toTime()}"
        manager.addView(timeLeftTextView, tvTimeLeftParam)
        timeLeftTextView.doOnNextLayout {
            val tvClicksAgainInParam = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                tvX,
                tvY + (it.height + 8.toPx()),
                overlayParam,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT)
            manager.addView(clicksAgainInTextView, tvClicksAgainInParam)
        }

        //adding an touchlistener to make drag movement of the floating widget
        startButton.setOnTouchListener(TouchAndDragListener(startButtonParams, startDragDistance,
                                                            { viewOnClick() },
                                                            {
                                                                "updateViewLayout".logd()
                                                                manager.updateViewLayout(
                                                                    startButton, startButtonParams)
                                                            }))
    }

    private fun uiTimer(totalMilSeconds: Long): Flow<String> {
        val totalSeconds = totalMilSeconds / 1000
        return (totalSeconds - 1 downTo 0).asFlow()
            .onEach { delay(1000) }
            .onStart { emit(totalSeconds) }
            .conflate()
            .map { it.toTime() }
    }

    private var isOn = false
    private fun viewOnClick() {
        "viewOnClick".logd()
        if (isOn) {
            outerTimer?.cancel()
            clickTimer?.cancel()
            periodicTimerJob?.cancel()
            periodicTimerJob = null
            timeLeftJob?.cancel()
            timeLeftJob = null
        } else {
            outerTimer = fixedRateTimer(initialDelay = 0,
                                        period = startAgainIn) {
                "outerTimer called".logd()
                clickTimer?.cancel()
                timeLeftJob?.cancel()
                periodicTimerJob?.cancel()

                periodicTimerJob = MainScope().launch {
                    uiTimer(startAgainIn)
                        .onCompletion { }
                        .collect {
                            val title = "Time left until collection:\n $it"
                            (timeLeftTextView as? TextView)?.text = title
                        }
                }

                timeLeftJob = MainScope().launch {
                    uiTimer(clicksTimeLeft)
                        .onStart {
                            val title = "Time left until collection:\n $startAgainIn"
                            (timeLeftTextView as? TextView)?.text = title
                        }
                        .onCompletion {
                            "onCompletion timeLeftJob".logd()
                            clickTimer?.cancel()
                        }
                        .collect {
                            val title = "Collecting time left:\n $it"
                            (clicksAgainInTextView as? TextView)?.text = title
                        }
                }

                clickTimer = fixedRateTimer(initialDelay = 0,
                                            period = clickSpeed) {
                    startButton.getLocationOnScreen(location)
                    autoClickService?.click(getRandomXDirection(startButton),
                                            getRandomYDirection(startButton))
                }
            }
        }
        isOn = !isOn
        val title = if (isOn) "ON" else "OFF"
        (startButton as? TextView)?.text = title

    }

    private fun getRandomXDirection(view: View): Int {
        val x = when (Random.nextBoolean()) {
            true -> location[0] + view.right + randomInt()
            false -> location[0] - view.width - randomInt()
        }
        return when {
            x >= screenWidth -> screenWidth - 1
            x <= 0 -> 1
            else -> x
        }
    }

    private fun getRandomYDirection(view: View): Int {
        val y = when (Random.nextBoolean()) {
            true -> location[1] - view.height - randomInt()
            false -> location[1] + view.bottom + randomInt()
        }
        return when {
            y >= screenHeight -> screenHeight - 1
            y <= 0 -> 1
            else -> y
        }
    }

    private fun randomInt() = Random.nextInt(0, 50)

    override fun onDestroy() {
        super.onDestroy()
        "FloatingClickService onDestroy".logd()
        clickTimer?.cancel()
        outerTimer?.cancel()
        periodicTimerJob?.cancel()
        timeLeftJob?.cancel()
        manager.removeView(startButton)
        manager.removeView(timeLeftTextView)
        manager.removeView(clicksAgainInTextView)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        "FloatingClickService onConfigurationChanged".logd()
        val x = startButtonParams.x
        val y = startButtonParams.y
        startButtonParams.x = xForRecord
        startButtonParams.y = yForRecord
        xForRecord = x
        yForRecord = y
        manager.updateViewLayout(startButton, startButtonParams)
    }
}