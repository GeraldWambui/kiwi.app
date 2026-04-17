package co.juneai.june.data.model

import java.util.Date

data class DateGroup(
    val date: Date,
    val label: String, // "Today", "Yesterday", or date string
    val items: List<MediaItem>
)