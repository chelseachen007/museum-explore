package cn.chenjie.museumexplore.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 唤起外部地图导航到某个地点。
 *
 * 优先试高德——国内地址的 POI 命中率明显高于系统 `geo:` 方案，
 * 而且用户手机上大概率装了。没装或唤起失败再退回系统地图选择器，
 * 两个都不行才提示。
 *
 * 这里不申请任何定位权限：地址是数据里写死的文案，导航完全交给外部应用。
 */
object MapNavigator {

    /** 高德地图原生 scheme 的包名。 */
    private const val AMAP_PACKAGE = "com.autonavi.minimap"

    /**
     * 没收录具体地址时直接用馆名导航。
     *
     * 博物馆在高德地图里基本都有 POI，搜「浙江省博物馆」比让用户自己复制粘贴强；
     * 地址缺失不该变成「不能导航」。
     */
    fun navigate(context: Context, landmark: String, address: String?) {
        val target = searchTarget(landmark, address)
        if (target.isEmpty()) {
            Toast.makeText(context, "这家馆还没有可用的地址", Toast.LENGTH_SHORT).show()
            return
        }

        if (launchAmap(context, target)) return
        if (launchSystemMap(context, target)) return

        Toast.makeText(context, "没有找到可用的地图应用", Toast.LENGTH_SHORT).show()
    }

    /**
     * 拼出交给地图应用的关键词。
     *
     * 有地址用「馆名 + 地址」，没地址就只用馆名——博物馆在高德基本都有 POI，
     * 缺地址不该让「导航」按钮消失。
     */
    internal fun searchTarget(landmark: String, address: String?): String {
        val name = landmark.trim()
        val detail = address?.trim().orEmpty()
        return when {
            detail.isEmpty() -> name
            name.isEmpty() -> detail
            // 地址里已经带了馆名的话不重复拼一遍。
            detail.contains(name) -> detail
            else -> "$name $detail"
        }
    }

    /** 高德 URL scheme：`androidamap://keywordNavi` 直接按关键词搜索并进入导航。 */
    private fun launchAmap(context: Context, target: String): Boolean {
        val uri = Uri.parse("androidamap://keywordNavi")
            .buildUpon()
            .appendQueryParameter("sourceApplication", "cn.chenjie.museumexplore")
            .appendQueryParameter("keyword", target)
            .appendQueryParameter("style", "2")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // 不指定包名时高德自己会拦截，指定了则系统能直接路由过去。
            setPackage(AMAP_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 退路：系统地图选择器。 */
    private fun launchSystemMap(context: Context, target: String): Boolean {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(target)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
