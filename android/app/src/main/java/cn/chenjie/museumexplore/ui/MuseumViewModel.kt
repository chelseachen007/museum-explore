package cn.chenjie.museumexplore.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cn.chenjie.museumexplore.data.MuseumRepository
import cn.chenjie.museumexplore.model.MuseumRecord
import cn.chenjie.museumexplore.model.VisitPhoto
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

    /** 想把某件国宝加进「想看」清单。 */
    fun setRelicWished(id: Int, value: Boolean) { repository.setRelicWished(id, value); refresh() }

    /** 想把某家博物馆加进「想去」清单。 */
    fun setMuseumWished(name: String, value: Boolean) { repository.setMuseumWished(name, value); refresh() }

    /** 补录一次参观；日期不确定时传 null，时间线显示「日期待补充」。 */
    fun addVisit(museum: String, startedAt: Long?) { repository.addVisit(museum, startedAt); refresh() }

    fun setVisitDate(visitId: String, startedAt: Long): Boolean {
        val ok = repository.setVisitDate(visitId, startedAt)
        refresh()
        return ok
    }

    fun confirmVisit(visitId: String, startedAt: Long?) {
        repository.confirmVisit(visitId, startedAt)
        refresh()
    }

    fun updateVisitJournal(visitId: String, note: String, photos: List<VisitPhoto>) {
        repository.updateVisitJournal(visitId, note, photos)
        refresh()
    }

    fun removeVisit(visitId: String) { repository.removeVisit(visitId); refresh() }
    fun deleteMuseumRecord(museum: String, record: MuseumRecord) {
        repository.deleteMuseumRecord(museum, record)
        refresh()
    }
    fun saveNote(id: Int, value: String) { repository.saveNote(id, value); refresh() }
    fun saveMuseumRecord(museum: String, note: String, photos: List<VisitPhoto>) {
        repository.saveMuseumRecord(museum, note, photos)
        refresh()
    }

    fun saveBranchRecord(museum: String, branch: String?, note: String, photos: List<VisitPhoto>) {
        repository.saveBranchRecord(museum, branch, note, photos)
        refresh()
    }
    fun saveMapKey(value: String) { repository.saveMapKey(value); refresh() }

    // 博物馆目录：自建馆、改名、合并馆区
    fun addUserMuseum(name: String, city: String, officialUrl: String?, address: String?, notes: String?): Boolean {
        val ok = repository.addUserMuseum(name, city, officialUrl, address, notes)
        refresh()
        return ok
    }

    fun updateUserMuseum(name: String, city: String, officialUrl: String?, address: String?, notes: String?): Boolean {
        val ok = repository.updateUserMuseum(name, city, officialUrl, address, notes)
        refresh()
        return ok
    }

    fun removeUserMuseum(name: String) { repository.removeUserMuseum(name); refresh() }

    fun renameUserMuseum(oldName: String, newName: String): Boolean {
        val ok = repository.renameUserMuseum(oldName, newName)
        refresh()
        return ok
    }

    fun mergeMuseums(parent: String, city: String, members: List<Pair<String, String>>, trimSuffixes: List<String>): Boolean {
        val ok = repository.mergeMuseums(parent, city, members, trimSuffixes)
        refresh()
        return ok
    }
    fun saveLastPhoto(value: String) { repository.saveLastPhoto(value); refresh() }
}
