package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * 1. ملف المعرفات الرسمي لإعلانات Google AdMob.
 * يحتوي على معرفات الاختبار الرسمية من Google (Test IDs).
 * عند إطلاق التطبيق وتحقيق الأرباح، استبدل هذه المعرفات بالمعرفات الحقيقية من حسابك في AdMob.
 */
object AdMobIds {
    // المعرفات الحقيقية الرسمية الخاصة بحسابك في Google AdMob
    const val APP_ID = "ca-app-pub-1241288532566123~7520011293"
    const val BANNER_ID = "ca-app-pub-1241288532566123/9891143904"
    const val INTERSTITIAL_ID = "ca-app-pub-1241288532566123/3936109640"
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    // معرفات الاختبار الاحتياطية (Google Test IDs)
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
}

/**
 * 2. مدير إعلانات Google AdMob (AdMobManager).
 * مسؤول عن تهيئة مكتبة الإعلانات، وتحميل الإعلانات البينية مسبقاً، وعرضها بأمان عند التنقل.
 */
object AdMobManager {

    // للتوافق مع أي استدعاءات سابقة في التطبيق
    const val APP_ID_TEST = AdMobIds.APP_ID
    const val BANNER_TEST_ID = AdMobIds.BANNER_ID
    const val INTERSTITIAL_TEST_ID = AdMobIds.INTERSTITIAL_ID

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    /**
     * تهيئة مكتبة إعلانات AdMob وبدء تحميل أول إعلان بيني في الخلفية.
     * يتم استدعاء هذه الدالة في دالة onCreate داخل MainActivity.
     */
    fun init(context: Context) {
        try {
            MobileAds.initialize(context) { initializationStatus ->
                Log.d("AdMobManager", "AdMob Initialized: $initializationStatus")
                // تحميل الإعلان البيني فور اكتمال التهيئة
                loadInterstitial(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e("AdMobManager", "Error initializing AdMob: ${e.message}", e)
        }
    }

    /**
     * تحميل إعلان بيني (Interstitial Ad) مسبقاً في الخلفية ليكون جاهزاً للعرض فوراً عند طلبه.
     */
    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isLoadingInterstitial) {
            return
        }
        isLoadingInterstitial = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdMobIds.INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdMobManager", "Interstitial Ad Loaded successfully.")
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdMobManager", "Interstitial Ad Failed to load: ${error.message} (Code: ${error.code})")
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    /**
     * التحقق مما إذا كان الإعلان البيني جاهزاً للعرض حالياً.
     */
    fun isInterstitialLoaded(): Boolean {
        return interstitialAd != null
    }

    /**
     * عرض الإعلان البيني (Interstitial) بأمان عند الانتقال بين الشاشات أو الضغط على الأزرار.
     * @param activity شاشة التطبيق الحالية (Activity).
     * @param onAdClosed دالة يتم تنفيذها بمجرد إغلاق الإعلان (أو فوراً إذا لم يكن الإعلان جاهزاً) لمتابعة التنقل.
     */
    fun showInterstitial(activity: Activity?, onAdClosed: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad != null && activity != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdMobManager", "Interstitial Ad Dismissed.")
                    interstitialAd = null
                    // تحميل الإعلان التالي فوراً في الخلفية ليكون جاهزاً للانتقال القادم
                    loadInterstitial(activity)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e("AdMobManager", "Interstitial Ad Failed to show: ${error.message}")
                    interstitialAd = null
                    loadInterstitial(activity)
                    onAdClosed()
                }
            }
            ad.show(activity)
        } else {
            if (activity != null) {
                loadInterstitial(activity)
            }
            onAdClosed()
        }
    }
}

/**
 * 3. مكون Jetpack Compose لعرض إعلان البانر (Banner Ad) في واجهات التطبيق.
 * يحتوي على إطار زجاجي شفاف مؤقت (Glassmorphism Placeholder) يتيح معينة مكان الإعلان بوضوح.
 * تمت معالجة دورة الحياة (Lifecycle) بدقة فائقة لتجنب تسرب الذاكرة (Memory Leaks) أو انهيار التطبيق.
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobIds.BANNER_ID
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var adView by remember { mutableStateOf<AdView?>(null) }
    var isAdLoaded by remember { mutableStateOf(false) }

    // إدارة دورة حياة الإعلان (Pause, Resume, Destroy) لمنع أي تسرب للذاكرة عند التنقل
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView?.pause()
                Lifecycle.Event.ON_RESUME -> adView?.resume()
                Lifecycle.Event.ON_DESTROY -> adView?.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView?.destroy()
            adView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // إطار زجاجي شفاف مؤقت لإظهار موقع الإعلان واختبار التنسيق بشكل ممتاز
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0x1810B981), // خلفية زجاجية خفيفة شفافة
            border = BorderStroke(1.dp, Color(0x4410B981))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                // عرض إعلان AdMob الحقيقي / التجريبي
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = adUnitId
                            adListener = object : AdListener() {
                                override fun onAdLoaded() {
                                    isAdLoaded = true
                                }
                            }
                            loadAd(AdRequest.Builder().build())
                            adView = this
                        }
                    },
                    onRelease = { view ->
                        view.destroy()
                    }
                )

                // في حالة تحميل الإعلان أو وضع المعاينة بدون استجابة شبكة، يظهر مؤشر شفاف يوضح موقع الإعلان
                if (!isAdLoaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📢 إعلان مدعوم من Google AdMob",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}
