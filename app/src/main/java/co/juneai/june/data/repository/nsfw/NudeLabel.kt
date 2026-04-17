package co.juneai.june.data.repository.nsfw


enum class NudeLabel(val index: Int, val isSensitive: Boolean) {
    FEMALE_GENITALIA_COVERED(0, false),
    FACE_FEMALE(1, false),
    BUTTOCKS_EXPOSED(2, true),
    FEMALE_BREAST_EXPOSED(3, true),
    FEMALE_GENITALIA_EXPOSED(4, true),
    MALE_BREAST_EXPOSED(5, false),
    ANUS_EXPOSED(6, true),
    FEET_EXPOSED(7, false),
    BELLY_COVERED(8, false),
    FEET_COVERED(9, false),
    ARMPITS_COVERED(10, false),
    ARMPITS_EXPOSED(11, false),
    FACE_MALE(12, false),
    BELLY_EXPOSED(13, false),
    MALE_GENITALIA_EXPOSED(14, true),
    ANUS_COVERED(15, false),
    FEMALE_BREAST_COVERED(16, false),
    BUTTOCKS_COVERED(17, false);

    companion object {
        fun fromIndex(index: Int): NudeLabel? = entries.find { it.index == index }
    }
}
