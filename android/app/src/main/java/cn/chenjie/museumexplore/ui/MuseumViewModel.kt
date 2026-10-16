package cn.chenjie.museumexplore.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cn.chenjie.museumexplore.data.MuseumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MuseumViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MuseumRepository(application)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun refresh() { _revision.value += 1 }
    fun setCollected(id: Int, value: Boolean) { repository.setCollected(id, value); refresh() }
    fun setVisited(name: String, value: Boolean) { repository.setVisited(name, value); refresh() }
    fun saveNote(id: Int, value: String) { repository.saveNote(id, value); refresh() }
    fun saveMapKey(value: String) { repository.saveMapKey(value); refresh() }
    fun saveLastPhoto(value: String) { repository.saveLastPhoto(value); refresh() }
}
