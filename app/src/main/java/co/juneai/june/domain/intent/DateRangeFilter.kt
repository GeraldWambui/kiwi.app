package co.juneai.june.domain.intent

import java.util.Calendar
import java.util.regex.Pattern

data class DateRangeFilter(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val description: String
)

object DateRangeParser {

    private val MONTH_MAP = mapOf(
        "january" to Calendar.JANUARY, "jan" to Calendar.JANUARY,
        "february" to Calendar.FEBRUARY, "feb" to Calendar.FEBRUARY,
        "march" to Calendar.MARCH, "mar" to Calendar.MARCH,
        "april" to Calendar.APRIL, "apr" to Calendar.APRIL,
        "may" to Calendar.MAY,
        "june" to Calendar.JUNE, "jun" to Calendar.JUNE,
        "july" to Calendar.JULY, "jul" to Calendar.JULY,
        "august" to Calendar.AUGUST, "aug" to Calendar.AUGUST,
        "september" to Calendar.SEPTEMBER, "sep" to Calendar.SEPTEMBER, "sept" to Calendar.SEPTEMBER,
        "october" to Calendar.OCTOBER, "oct" to Calendar.OCTOBER,
        "november" to Calendar.NOVEMBER, "nov" to Calendar.NOVEMBER,
        "december" to Calendar.DECEMBER, "dec" to Calendar.DECEMBER
    )

    fun parseDateRange(input: String): Pair<String, DateRangeFilter?> {
        val lower = input.lowercase().trim()
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)

        // 1. Holiday & Event Matching (Christmas, New Year, Halloween, Thanksgiving)
        val holidayRegex = Pattern.compile("\\b(christmas|new year|halloween|thanksgiving)(?:\\s+(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE)
        val holidayMatcher = holidayRegex.matcher(input)
        if (holidayMatcher.find()) {
            val holiday = holidayMatcher.group(1).lowercase()
            val yearStr = holidayMatcher.group(2)
            val year = yearStr?.toIntOrNull() ?: currentYear
            val filter = getHolidayRange(holiday, year)
            if (filter != null) {
                val clean = input.replace(holidayMatcher.group(), "").trim()
                return clean to filter
            }
        }

        // 2. Season Matching (Summer 2024, Spring 2023, Fall 2022, Winter 2023)
        val seasonRegex = Pattern.compile("\\b(summer|spring|winter|fall|autumn)(?:\\s+(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE)
        val seasonMatcher = seasonRegex.matcher(input)
        if (seasonMatcher.find()) {
            val season = seasonMatcher.group(1).lowercase()
            val yearStr = seasonMatcher.group(2)
            val year = yearStr?.toIntOrNull() ?: currentYear
            val filter = getSeasonRange(season, year)
            if (filter != null) {
                val clean = input.replace(seasonMatcher.group(), "").trim()
                return clean to filter
            }
        }

        // 3. Specific Month + Year (e.g., "July 2023", "October 2022", "in Dec 2024")
        val monthYearRegex = Pattern.compile("\\b(in\\s+|from\\s+)?(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE)
        val monthYearMatcher = monthYearRegex.matcher(input)
        if (monthYearMatcher.find()) {
            val monthName = monthYearMatcher.group(2).lowercase()
            val year = monthYearMatcher.group(3).toInt()
            val monthInt = MONTH_MAP[monthName] ?: Calendar.JANUARY

            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthInt)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, maxDay)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            val end = cal.timeInMillis

            val clean = input.replace(monthYearMatcher.group(), "").trim()
            return clean to DateRangeFilter(start, end, "${monthName.replaceFirstChar { it.uppercase() }} $year")
        }

        // 4. Standalone Specific Year (e.g., "photos from 2021", "in 2023", "2022")
        val yearRegex = Pattern.compile("\\b(?:in\\s+|from\\s+|during\\s+)?(19\\d{2}|20\\d{2})\\b", Pattern.CASE_INSENSITIVE)
        val yearMatcher = yearRegex.matcher(input)
        if (yearMatcher.find()) {
            val year = yearMatcher.group(1).toInt()
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 31)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            val end = cal.timeInMillis

            val clean = input.replace(yearMatcher.group(), "").trim()
            return clean to DateRangeFilter(start, end, "$year")
        }

        // 5. Relative Numeric Offsets (e.g., "3 days ago", "past 6 months", "2 weeks ago", "last 5 days")
        val offsetRegex = Pattern.compile("\\b(?:past|last)?\\s*(\\d+)\\s+(day|days|week|weeks|month|months|year|years)(?:\\s+ago)?\\b", Pattern.CASE_INSENSITIVE)
        val offsetMatcher = offsetRegex.matcher(input)
        if (offsetMatcher.find()) {
            val amount = offsetMatcher.group(1).toInt()
            val unit = offsetMatcher.group(2).lowercase()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            when {
                unit.startsWith("day") -> cal.add(Calendar.DAY_OF_YEAR, -amount)
                unit.startsWith("week") -> cal.add(Calendar.WEEK_OF_YEAR, -amount)
                unit.startsWith("month") -> cal.add(Calendar.MONTH, -amount)
                unit.startsWith("year") -> cal.add(Calendar.YEAR, -amount)
            }
            val start = cal.timeInMillis
            val end = System.currentTimeMillis()

            val clean = input.replace(offsetMatcher.group(), "").trim()
            return clean to DateRangeFilter(start, end, "Past $amount $unit")
        }

        // 6. Common Named Phrases (yesterday, today, last week, last month, this year, last year)
        when {
            lower.contains("yesterday") -> {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                val clean = input.replace("yesterday", "", ignoreCase = true).trim()
                return clean to DateRangeFilter(start, end, "Yesterday")
            }

            lower.contains("today") -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val clean = input.replace("today", "", ignoreCase = true).trim()
                return clean to DateRangeFilter(start, System.currentTimeMillis(), "Today")
            }

            lower.contains("last week") -> {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.WEEK_OF_YEAR, -1)
                }
                val start = cal.timeInMillis
                val clean = input.replace("last week", "", ignoreCase = true).trim()
                return clean to DateRangeFilter(start, System.currentTimeMillis(), "Last Week")
            }

            lower.contains("last month") -> {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                }
                val start = cal.timeInMillis
                val clean = input.replace("last month", "", ignoreCase = true).trim()
                return clean to DateRangeFilter(start, System.currentTimeMillis(), "Last Month")
            }

            lower.contains("this year") -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val clean = input.replace("this year", "", ignoreCase = true).trim()
                return clean to DateRangeFilter(start, System.currentTimeMillis(), "$currentYear")
            }

            lower.contains("last year") -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear - 1)
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                val clean = input.replace("last year", "", ignoreCase = true).trim()
                return clean to DateRangeFilter(start, end, "${currentYear - 1}")
            }
        }

        return input to null
    }

    private fun getHolidayRange(holiday: String, year: Int): DateRangeFilter? {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (holiday) {
            "christmas" -> {
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 24)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, 26)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "Christmas $year")
            }
            "new year" -> {
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                val start = cal.timeInMillis
                cal.set(Calendar.YEAR, year + 1)
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 2)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "New Year $year")
            }
            "halloween" -> {
                cal.set(Calendar.MONTH, Calendar.OCTOBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "Halloween $year")
            }
            "thanksgiving" -> {
                cal.set(Calendar.MONTH, Calendar.NOVEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 20)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, 28)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "Thanksgiving $year")
            }
            else -> null
        }
    }

    private fun getSeasonRange(season: String, year: Int): DateRangeFilter? {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (season) {
            "summer" -> {
                cal.set(Calendar.MONTH, Calendar.JUNE)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.AUGUST)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "Summer $year")
            }
            "spring" -> {
                cal.set(Calendar.MONTH, Calendar.MARCH)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.MAY)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "Spring $year")
            }
            "fall", "autumn" -> {
                cal.set(Calendar.MONTH, Calendar.SEPTEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.NOVEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 30)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "Fall $year")
            }
            "winter" -> {
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.set(Calendar.YEAR, year + 1)
                cal.set(Calendar.MONTH, Calendar.FEBRUARY)
                cal.set(Calendar.DAY_OF_MONTH, 28)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                DateRangeFilter(start, cal.timeInMillis, "Winter $year")
            }
            else -> null
        }
    }
}
