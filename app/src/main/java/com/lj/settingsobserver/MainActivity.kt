package com.lj.settingsobserver

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() ,SettingsObserver.Callback{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var handlerThread = HandlerThread("bg");
        var observer = SettingsObserverImpl(this, Handler(handlerThread.looper))
        observer.addCallback(
            this,
            SettingsObserver.SETTINGS_TYPE_SECURE,
            "doze_always_on",
            "aod_using_super_wallpaper"
        )
    }

    override fun onContentChanged(key: String?, newValue: String?) {

    }
}