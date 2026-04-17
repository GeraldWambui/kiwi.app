package co.juneai.june.ui.screen.mediadetail.ui

sealed class MediaDetailEvent {
    data object ToggleFavorite : MediaDetailEvent()
    data object ToggleInfo : MediaDetailEvent()
    data object Share : MediaDetailEvent()
    data object Edit : MediaDetailEvent()
    data object Delete : MediaDetailEvent()
    data object Back : MediaDetailEvent()
    data object MoreOptions : MediaDetailEvent()
    data class SetMedia(val media: MediaDetail) : MediaDetailEvent()
    data class SaveEdit(val newTitle: String, val newDescription: String?) : MediaDetailEvent()
}