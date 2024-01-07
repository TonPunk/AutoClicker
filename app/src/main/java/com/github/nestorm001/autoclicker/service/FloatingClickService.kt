package com.github.nestorm001.autoclicker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.github.nestorm001.autoclicker.R
import com.github.nestorm001.autoclicker.TouchAndDragListener
import com.github.nestorm001.autoclicker.dp2px
import com.github.nestorm001.autoclicker.logd
import com.github.nestorm001.autoclicker.toPx
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.schedule
import kotlin.random.Random


/**
 * Created on 2018/9/28.
 * By nesto
 */
class FloatingClickService: Service() {
    private lateinit var manager: WindowManager
    private lateinit var view: View
    private lateinit var params: WindowManager.LayoutParams
    private var xForRecord = 0
    private var yForRecord = 0
    private val location = IntArray(2)
    private var startDragDistance: Int = 0
    private var clickTimer: Timer? = null
    private var outerTimer: Timer? = null
    private var stopTimer: Timer? = null
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val screenHeight = Resources.getSystem().displayMetrics.heightPixels

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startDragDistance = dp2px(10f)
        view = LayoutInflater.from(this).inflate(R.layout.widget, null)

        //setting the layout parameters
        val overlayParam =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
        params = WindowManager.LayoutParams(
            80.toPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayParam,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

        //getting windows services and adding the floating view to it
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        manager.addView(view, params)

        //adding an touchlistener to make drag movement of the floating widget
        view.setOnTouchListener(TouchAndDragListener(params, startDragDistance,
                                                     { viewOnClick() },
                                                     {
                                                         "updateViewLayout".logd()
                                                         manager.updateViewLayout(view, params)
                                                     }))
    }

    private var isOn = false
    private fun viewOnClick() {
        "viewOnClick".logd()
        if (isOn) {
            outerTimer?.cancel()
            clickTimer?.cancel()
        } else {
            outerTimer = fixedRateTimer(initialDelay = 0,
                                        period = 1_680_000) {
                "outerTimer called".logd()
                clickTimer?.cancel()
                stopTimer?.cancel()
                clickTimer = fixedRateTimer(initialDelay = 0,
                                            period = 121) {
                    view.getLocationOnScreen(location)
                    autoClickService?.click(getRandomXDirection(view),
                                            getRandomYDirection(view))
                }
                stopTimer = Timer(false)
                stopTimer?.schedule(100_000) {
                    "stopTimer called".logd()
                    clickTimer?.cancel()
                }
            }
        }
        isOn = !isOn
        val title = if (isOn) "ON" else "OFF"

        (view as? TextView)?.setText(title)

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
        val y =  when (Random.nextBoolean()) {
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
        stopTimer?.cancel()
        manager.removeView(view)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        "FloatingClickService onConfigurationChanged".logd()
        val x = params.x
        val y = params.y
        params.x = xForRecord
        params.y = yForRecord
        xForRecord = x
        yForRecord = y
        manager.updateViewLayout(view, params)
    }
}