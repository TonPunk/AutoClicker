package com.github.tonpunk.autoclicker

import android.content.Context
import android.content.res.Resources
import android.icu.text.SimpleDateFormat
import android.text.SpannableStringBuilder
import android.util.Log
import java.util.Date
import java.util.Locale


/**
 * Created on 2018/9/28.
 * By nesto
 */
private const val TAG = "AutoClickService"
private val dateFormatter = SimpleDateFormat("mm:ss", Locale("en"))

fun Any.logd(tag: String = TAG) {
    if (!BuildConfig.DEBUG) return
    if (this is String) {
        Log.d(tag, this)
    } else {
        Log.d(tag, this.toString())
    }
}

fun Int.toPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()


fun SpannableStringBuilder.clearAll() {
    this.clear()
    this.clearSpans()
}

fun Long.toTime(): String {
    return dateFormatter.format(Date(this * 1000))
}

fun Long.millsToSec(): Long {
    return this / 1000
}

fun Any.loge(tag: String = TAG) {
    if (!BuildConfig.DEBUG) return
    if (this is String) {
        Log.e(tag, this)
    } else {
        Log.e(tag, this.toString())
    }
}

fun Context.dp2px(dpValue: Float): Int {
    val scale = resources.displayMetrics.density
    return (dpValue * scale + 0.5f).toInt()
}

typealias Action = () -> Unit