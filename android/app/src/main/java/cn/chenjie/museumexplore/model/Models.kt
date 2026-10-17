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
    val closedDays: String? = null,
    val reservation: String?,
    val wikiUrl: String?,
    val miniProgram: String?,
    /** null 表示数据里没有声明，按「可能对公众开放」处理，但不会据此伪造地址。 */
    val openToPublic: Boolean? = null,
    val type: String? = null,
    val verifiedAt: String? = null,
    val status: String? = null,
)

data class OfficialEntry(
    val museum: String,
    val appName: String,
    val appId: String?,
    val landingUrl: String?,
    val schemeUrl: String?,
    val officialUrl: String?,
    val reservationUrl: String?,
    val verifiedAt: String,
    val wechatSearchName: String? = null,
    val guideUrl: String? = null,
    val exhibitionUrl: String? = null,
    val status: String? = null,
)

data class CitySummary(
    val name: String,
    val museumCount: Int,
    val relicCount: Int,
    val visitedCount: Int,
)
