package co.juneai.june.ui.screen.photos

sealed class OverflowAction {
    object VideoEditor : OverflowAction()
    object Collage : OverflowAction()
    object FreeUpSpace : OverflowAction()
    object SortAndView : OverflowAction()
    object CreateAlbum : OverflowAction()
    object Settings : OverflowAction()
}
