package cn.chenjie.museumexplore.model

data class Relic(
    val id: Int,
    val name: String,
    val era: String,
    val category: String,
    val museum: String,
    val city: String,
    val reason: String,
    val story: String,
    val image: String?,
    val localImage: String?,
    val museumUrl: String?,
    val museumWechat: String?,
)

data class VisitPhoto(
    val path: String,
    val relicId: Int? = null,
)

data class MuseumRecord(
    val note: String = "",
    val photos: List<VisitPhoto> = emptyList(),
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = note.isBlank() && photos.isEmpty()
}

data class MuseumInfo(
    val name: String,
    val city: String,
    val officialUrl: String?,
    val wechat: String?,
    val address: String?,
    val intro: String?,
    val openingHours: String?,
    val reservation: String?,
    val wikiUrl: String?,
    val miniProgram: String?,
)

data class CitySummary(
    val name: String,
    val museumCount: Int,
    val relicCount: Int,
    val visitedCount: Int,
)
