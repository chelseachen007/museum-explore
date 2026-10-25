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

    fun navigate(context: Context, landmark: String, address: String?) {
        val target = listOfNotNull(landmark.trim().takeIf { it.isNotEmpty() }, address?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(" ")
        if (target.isEmpty()) {
            Toast.makeText(context, "这家馆还没有可用的地址", Toast.LENGTH_SHORT).show()
            return
        }

        if (launchAmap(context, target)) return
        if (launchSystemMap(context, target)) return

        Toast.makeText(context, "没有找到可用的地图应用", Toast.LENGTH_SHORT).show()
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
