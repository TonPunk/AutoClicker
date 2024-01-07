package com.github.tonpunk.autoclicker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Typeface.DEFAULT_BOLD
import android.icu.util.Calendar
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.bigkoo.pickerview.builder.TimePickerBuilder
import com.bigkoo.pickerview.view.TimePickerView
import com.github.tonpunk.Settings.clicksTimeLeft
import com.github.tonpunk.Settings.setClickTime
import com.github.tonpunk.Settings.setPeriodTime
import com.github.tonpunk.Settings.startAgainIn
import com.github.tonpunk.autoclicker.service.FloatingClickService
import com.github.tonpunk.autoclicker.service.autoClickService


private const val PERMISSION_CODE = 110

class MainActivity: AppCompatActivity() {

    private var serviceIntent: Intent? = null
    private var timePicker: TimePickerView? = null
    private val calendar: Calendar = Calendar.getInstance()
    private val spanBuilder = SpannableStringBuilder()
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val button = findViewById<Button>(R.id.button)
        val periodTextView = findViewById<TextView>(R.id.tv_period_time)
        val clickTimeTextView = findViewById<TextView>(R.id.tv_click_time)

        val titlePeriod = makeTitle(R.string.period_left_title, startAgainIn.millsToSec().toTime())
        periodTextView.text = titlePeriod
        val titleClick = makeTitle(R.string.time_left_title, clicksTimeLeft.millsToSec().toTime())
        clickTimeTextView.text = titleClick

        button.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                serviceIntent = Intent(this@MainActivity, FloatingClickService::class.java)
                startService(serviceIntent)
            } else {
                askPermission()
                shortToast("You need System Alert Window Permission to do this")
            }
        }

        periodTextView.setOnClickListener {
            if (timePicker?.isShowing == true) {
                timePicker?.dismiss()
                timePicker = null
            }

            timePicker = TimePickerBuilder(this)
            { date, _ ->
                calendar.time = date
                val minutes = calendar.get(Calendar.MINUTE)
                val second = calendar.get(Calendar.SECOND)
                val mills = ((minutes * 60L) + second) * 1_000
                "time setted  mills=$mills".logd()
                setPeriodTime(mills)
                periodTextView.text = makeTitle(R.string.period_left_title, startAgainIn.millsToSec().toTime())
            }
                .setType(booleanArrayOf(false, false, false, false, true, true))// type of date
                .setContentTextSize(24)
                .setItemVisibleCount(5)
                .build()
            timePicker?.show()
        }

        clickTimeTextView.setOnClickListener {
            if (timePicker?.isShowing == true) {
                timePicker?.dismiss()
                timePicker = null
            }

            timePicker = TimePickerBuilder(this)
            { date, _ ->
                calendar.time = date
                val minutes = calendar.get(Calendar.MINUTE)
                val second = calendar.get(Calendar.SECOND)
                val mills = ((minutes * 60L) + second) * 1_000
                "time setted  mills=$mills".logd()
                setClickTime(mills)
                clickTimeTextView.text =
                    makeTitle(R.string.time_left_title, clicksTimeLeft.millsToSec().toTime())
            }
                .setType(booleanArrayOf(false, false, false, false, true, true))// type of date
                .setContentTextSize(24)
                .setItemVisibleCount(5)
                .build()
            timePicker?.show()
        }
    }

    private fun makeTitle(@StringRes str: Int, time: String): SpannableStringBuilder {
        spanBuilder.clearAll()
        val title = getString(str)
        spanBuilder.append(title)
        val colorBlack = getColor(android.R.color.black)
        spanBuilder.setSpan(ForegroundColorSpan(colorBlack),
                            0,
                            spanBuilder.length,
                            flag)
        spanBuilder.append(" ")
        spanBuilder.append(time)
        val color = getColor(R.color.colorPrimaryDark)
        spanBuilder.setSpan(ForegroundColorSpan(color),
                            title.length,
                            spanBuilder.length,
                            flag)
        spanBuilder.setSpan(StyleSpan(DEFAULT_BOLD.style),
                            title.length,
                            spanBuilder.length,
                            flag)
        return spanBuilder
    }

    private fun checkAccess(): Boolean {
        val string = getString(R.string.accessibility_service_id)
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list =
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (id in list) {
            if (string == id.id) {
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        val hasPermission = checkAccess()
        "has access? $hasPermission".logd()
        if (!hasPermission) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (!Settings.canDrawOverlays(this)) {
            askPermission()
        }
    }

    private fun askPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"))
        startActivityForResult(intent, PERMISSION_CODE)
    }

    override fun onDestroy() {
        timePicker?.dismiss()
        timePicker = null
        serviceIntent?.let {
            "stop floating click service".logd()
            stopService(it)
        }
        autoClickService?.let {
            "stop auto click service".logd()
            it.stopSelf()
            return it.disableSelf()
        }
        super.onDestroy()
    }
}
