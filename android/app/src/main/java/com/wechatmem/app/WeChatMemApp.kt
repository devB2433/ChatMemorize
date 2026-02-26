package com.wechatmem.app

import android.app.Application

class WeChatMemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: WeChatMemApp
            private set
    }
}
