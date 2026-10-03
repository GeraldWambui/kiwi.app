package co.juneai.june.utils

fun formatSize(size: Long): String = when {
    size < 1_024 -> "$size B"
    size < 1_048_576 -> "%.2f KB".format(size / 1_024.0)
    size < 1_073_741_824 -> "%.2f MB".format(size / 1_048_576.0)
    else -> "%.2f GB".format(size / 1_073_741_824.0)
}
