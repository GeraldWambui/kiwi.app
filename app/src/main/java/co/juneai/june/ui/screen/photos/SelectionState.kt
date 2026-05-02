package co.juneai.june.ui.screen.photos

import androidx.compose.runtime.mutableStateListOf

class SelectionState {
    private val _selected = mutableStateListOf<Long>()
    val selected: List<Long> get() = _selected

    val isSelectionMode: Boolean get() = _selected.isNotEmpty()

    fun toggle(id: Long) {
        if (_selected.contains(id)) _selected.remove(id)
        else _selected.add(id)
    }

    fun clear() = _selected.clear()

    fun selectAll(ids: List<Long>) {
        _selected.clear()
        _selected.addAll(ids)
    }

    fun isAllSelected(totalCount: Int): Boolean {
        return totalCount > 0 && _selected.size == totalCount
    }
}