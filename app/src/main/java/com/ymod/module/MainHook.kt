package com.ymod.module

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.Reader
import java.io.StringReader

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TARGET_PKG = "ru.yandex.music"

        private val AD_DOMAINS = listOf(
            "an.yandex.ru",
            "awaps.yandex.net",
            "ads.adfox.ru",
            "adsdk.yandex.ru",
            "mobile.yandexadexchange.net",
            "/ads/",
            "/advertisement"
        )

        private val RE_HAS_PLUS = Regex("\"hasPlus\"\\s*:\\s*false")
        private val RE_IS_ACTIVE = Regex("\"isActive\"\\s*:\\s*false")
        private val RE_SUB_EMPTY = Regex("\"autoRenewable\"\\s*:\\s*\\[\\s*\\]")
        private val RE_CAN_START_TRIAL = Regex("\"canStartTrial\"\\s*:\\s*true")
        private val RE_AD_ARRAY = Regex("\"advertisements\"\\s*:\\s*\\[[^\\]]*\\]")
        private val RE_PRE_ROLL = Regex("\"prerolls\"\\s*:\\s*\\[[^\\]]*\\]")
        private val RE_HAS_ADS = Regex("\"hasAds\"\\s*:\\s*true")

        private const val FAKE_SUB =
            "\"autoRenewable\":[{\"expires\":\"2099-12-31T23:59:59+00:00\",\"vendor\":\"YANDEX\",\"vendorHelpUrl\":\"\",\"productId\":\"ru.yandex.plus.month\",\"finished\":false,\"product\":{\"productId\":\"ru.yandex.plus.month\",\"type\":\"subscription\",\"commonPeriodDuration\":\"P30D\",\"duration\":30,\"trialDuration\":0,\"price\":{\"amount\":0,\"currency\":\"RUB\"},\"feature\":\"basic-plus\",\"plus\":true,\"features\":[\"basic-music\",\"basic-plus\",\"high-quality\",\"offline\"]}}]"

        fun isAdUrl(url: String): Boolean {
            val lower = url.lowercase()
            return AD_DOMAINS.any { lower.contains(it) }
        }

        fun patchJson(raw: String): String {
            if (!raw.contains("hasPlus") &&
                !raw.contains("autoRenewable") &&
                !raw.contains("advertisements") &&
                !raw.contains("prerolls") &&
                !raw.contains("hasAds")
            ) {
                return raw
            }

            var out = raw
            out = RE_HAS_PLUS.replace(out, "\"hasPlus\":true")
            out = RE_SUB_EMPTY.replace(out, FAKE_SUB)
            out = RE_CAN_START_TRIAL.replace(out, "\"canStartTrial\":false")
            out = RE_AD_ARRAY.replace(out, "\"advertisements\":[]")
            out = RE_PRE_ROLL.replace(out, "\"prerolls\":[]")
            out = RE_HAS_ADS.replace(out, "\"hasAds\":false")

            if (out.contains("\"plus\"") || out.contains("\"subscription\"")) {
                out = RE_IS_ACTIVE.replace(out, "\"isActive\":true")
            }
            return out
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PKG) return
        val cl = lpparam.classLoader

        hookGson(cl)
        hookJsonObject(cl)
        hookOkHttpResponse(cl)
        hookOkHttpAdBlocker(cl)
    }

    private fun hookGson(cl: ClassLoader) {
        try {
            val gsonCls = XposedHelpers.findClassIfExists("com.google.gson.Gson", cl) ?: return
            XposedBridge.hookAllMethods(gsonCls, "fromJson", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.isNullOrEmpty()) return
                    when (val first = param.args[0]) {
                        is String -> {
                            val patched = patchJson(first)
                            if (patched !== first) param.args[0] = patched
                        }
                        is Reader -> {
                            try {
                                val text = first.readText()
                                val patched = patchJson(text)
                                param.args[0] = StringReader(patched)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookJsonObject(cl: ClassLoader) {
        try {
            val jsonCls = XposedHelpers.findClassIfExists("org.json.JSONObject", cl) ?: return
            XposedHelpers.findAndHookConstructor(
                jsonCls,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val raw = param.args[0] as? String ?: return
                        val patched = patchJson(raw)
                        if (patched !== raw) param.args[0] = patched
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun hookOkHttpResponse(cl: ClassLoader) {
        try {
            val rbCls = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl) ?: return
            XposedBridge.hookAllMethods(rbCls, "string", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val body = param.result as? String ?: return
                    val patched = patchJson(body)
                    if (patched !== body) param.result = patched
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookOkHttpAdBlocker(cl: ClassLoader) {
        try {
            val reqBuilder = XposedHelpers.findClassIfExists("okhttp3.Request\$Builder", cl) ?: return
            XposedHelpers.findAndHookMethod(
                reqBuilder,
                "url",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.args[0] as? String ?: return
                        if (isAdUrl(url)) {
                            param.args[0] = "http://127.0.0.1/"
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }
}
