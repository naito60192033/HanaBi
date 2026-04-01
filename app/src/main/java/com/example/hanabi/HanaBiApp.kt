package com.example.hanabi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class HanaBiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android の BC プロバイダーは MD4 を含まないため、フル版に置き換える
        // jcifs-ng の NTLM 認証（NAS接続）に必要
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
