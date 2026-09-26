package co.juneai.june.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import timber.log.Timber

data class BatteryState(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isEligibleForIndexing: Boolean
)

object BatteryStatusManager {

    const val MIN_BATTERY_PERCENT_FOR_INDEXING = 60

    fun getBatteryState(context: Context): BatteryState {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPercent = if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
            } else {
                100 // Fallback if unable to read
            }

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val isEligible = isCharging || batteryPercent >= MIN_BATTERY_PERCENT_FOR_INDEXING

            BatteryState(
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                isEligibleForIndexing = isEligible
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to read battery status, defaulting to eligible")
            BatteryState(batteryPercent = 100, isCharging = true, isEligibleForIndexing = true)
        }
    }

    fun isEligibleForIndexing(context: Context): Boolean {
        return getBatteryState(context).isEligibleForIndexing
    }
}
