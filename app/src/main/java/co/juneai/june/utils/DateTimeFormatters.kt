package co.juneai.june.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown"
    return SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(
        Date(timestamp)
    )
}
