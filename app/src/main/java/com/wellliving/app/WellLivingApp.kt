package com.wellliving.app

import android.app.Application
import com.wellliving.app.core.EssentialPackages
import com.wellliving.app.core.SettingsStore
import com.wellliving.app.core.UsageTracker
import com.wellliving.rules.RulesEngine

class WellLivingApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var usage: UsageTracker
        private set
    lateinit var essentials: EssentialPackages
        private set
    val engine = RulesEngine()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        usage = UsageTracker(this)
        essentials = EssentialPackages(this)
    }
}
