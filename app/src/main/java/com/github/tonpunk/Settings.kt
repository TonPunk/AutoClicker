package com.github.tonpunk

object Settings {
    var startAgainIn: Long = 1_680_000
        private set
    var clicksTimeLeft: Long = 90_000
        private set


    fun setPeriodTime(timeMills: Long){
        startAgainIn = timeMills
    }

    fun setClickTime(timeMills: Long){
        clicksTimeLeft = timeMills
    }
}