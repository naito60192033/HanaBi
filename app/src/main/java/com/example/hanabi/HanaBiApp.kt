package com.example.hanabi

import android.app.Application
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class HanaBiApp : Application() {

    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        // Android の BC プロバイダーは MD4 を含まないため、フル版に置き換える
        // jcifs-ng の NTLM 認証（NAS接続）に必要
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        // SMB対応のカスタムImageLoaderをCoilシングルトンとして登録
        Coil.setImageLoader(imageLoader)
    }
}
