package co.juneai.june.ui.screen.photos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SelectionState {
    private val _selected = mutableStateListOf<Long>()
    val selected: List<Long> get() = _selected

    var isManualSelectionMode by mutableStateOf(false)

    val isSelectionMode: Boolean get() = isManualSelectionMode || _selected.isNotEmpty()

    fun enterSelectionMode() {
        isManualSelectionMode = true
    }

    fun toggle(id: Long) {
        if (_selected.contains(id)) _selected.remove(id)
        else _selected.add(id)
    }

    fun clear() {
        _selected.clear()
        isManualSelectionMode = false
    }

    fun selectAll(ids: List<Long>) {
        _selected.clear()
        _selected.addAll(ids)
    }

    fun isAllSelected(totalCount: Int): Boolean {
        return totalCount > 0 && _selected.size == totalCount
    }
}