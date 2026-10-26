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
    /**
     * 详情页正文（轻量 markdown），讲「前世今生 / 为什么重要 / 为什么不可替代 / 为什么不能出国」。
     *
     * 空串表示这件文物还没人写深度正文，详情页退回显示旧的 [story] 单段。
     * 不能把 null 写成「暂无」占位——那样用户分不清是「这文物没故事」还是「作者还没写」。
     */
    val detail: String = "",
) {
    val hasDetail: Boolean get() = detail.isNotBlank()
}

/**
 * 用户在博物馆现场自己拍到的「其他国宝」。
 *
 * 195 件永禁文物是固定名单，id 是 1..195；自建国宝走独立的 UserRelicsCodec 存储，
 * id 用负数（-1 起递减），这样两类记录在同一个界面上不会撞号，
 * 也不会因为名单扩编被顶掉。
 */
data class UserRelic(
    val id: Int,
    val name: String,
    /** 所在博物馆。已经不在 195 名单里也没关系，用户可以填任意馆名。 */
    val museum: String,
    val city: String,
    val era: String? = null,
    val category: String? = null,
    val reason: String? = null,
    val story: String? = null,
    /** 用户拍摄或相册导入的照片文件名（存在 visit_photos 目录下，与参观照片同址）。 */
    val photo: String? = null,
    val museumUrl: String? = null,
    val createdAt: Long = 0L,
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

/** 一条参观记录 + 它在时间线上的位置，用于详情页并列多张卡片。 */
data class MuseumRecordWithDate(
    val record: MuseumRecord,
    val visitId: String? = null,
)

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

/**
 * 用户自己新增的博物馆。
 *
 * 195 件文物名单之外的馆（包括国外、私人小馆、临时展览馆）都走这里，
 * 只存最基本的几项，其余一律留空，不编造地址和开放时间。
 */
data class UserMuseum(
    val name: String,
    val city: String,
    val officialUrl: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val createdAt: Long = 0L,
    /** 非空表示这条已经被并进同名/同名的内置馆，名字不能再被当作独立馆名使用。 */
    val mergedInto: String? = null,
) {
    val isMerged: Boolean get() = !mergedInto.isNullOrBlank()
}

/**
 * 同一座博物馆下的一个馆区（分馆）。
 *
 * 例如「浙江省博物馆」是父馆，「孤山馆区」「之江馆区」是馆区；
 * 参观记录落到 null 馆区表示「整个馆」，落回具体馆区才能区分这次去了哪一片。
 */
data class MuseumBranch(
    val parent: String,
    val name: String,
    val city: String,
    val address: String? = null,
    /** 展示用的馆区名，如「孤山馆区」「本馆」。为空时从 name 里推。 */
    val label: String? = null,
)

/** 博物馆列表里的一行。内置馆、用户自建馆统一走这个类型，界面不必关心来源。 */
data class MuseumEntry(
    val name: String,
    val city: String,
    val source: MuseumSource,
    val closed: Boolean = false,
)

enum class MuseumSource { RELIC, BUILT_IN, USER }

/** 可以用来记录参观的选项：要么整馆，要么某个馆区。 */
data class VisitTarget(
    val parent: String,
    val branch: String?,
    val city: String,
    /** 该馆区地址；父馆那一行的地址在地址栏里并排列出，这里只用于展示。 */
    val address: String? = null,
    /** 展示用馆区名。父馆那一行是「本馆」，拆成馆区的馆是「孤山馆区」这种。 */
    val label: String? = null,
) {
    /** 落库时使用的馆名。馆区记录成「父馆 · 馆区」，整馆就是父馆本身。 */
    val displayName: String get() = if (branch.isNullOrBlank()) parent else "$parent · $branch"
}
