/*
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.better.alarm.configuration

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.util.Log
import android.view.ViewConfiguration
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.better.alarm.R
import com.better.alarm.alert.BackgroundNotifications
import com.better.alarm.background.AlertServicePusher
import com.better.alarm.bugreports.BugReporter
import com.better.alarm.configuration.AlarmApplicationInit.hasStarted
import com.better.alarm.configuration.AlarmApplicationInit.startOnce
import com.better.alarm.createNotificationChannels
import com.better.alarm.logger.StringUtils
import com.better.alarm.model.AlarmValue
import com.better.alarm.model.Alarms
import com.better.alarm.model.AlarmsScheduler
import com.better.alarm.presenter.ScheduledReceiver
import com.better.alarm.presenter.ToastPresenter
import java.util.concurrent.atomic.AtomicBoolean

class AlarmApplication : MultiDexApplication() {
    override fun onCreate() {
        //Always initiates FIRST when alarm rings on app off
        Log.println(Log.WARN, TAG, "onCreate: $this")
        startOnce(this)
        super.onCreate()
    }

    companion object {
        private const val TAG = "AlarmApplication"
        @JvmStatic
        fun startOnce(application: Application) {
            val started = application.hasStarted()
            Log.println(Log.VERBOSE, TAG, "startOnce: application = $application" +
                ",\n hasStarted? $started")
            if (started) {
                Log.println(Log.ERROR, TAG, " " +
                    "\n startOnce(ALREADY STARTED!!!!<><><><>): " +
                    "\n from stack = ${StringUtils.getStackTrace()}")
            }
            application.startOnce()
        }
    }

    override fun toString(): String {
        return super.toString() + "@${hashCode()}"
    }
}

private object AlarmApplicationInit {
    private const val TAG = "AlarmApplication"
  private val started = AtomicBoolean(false)

    fun Application.hasStarted() : Boolean {
        return started.get()
    }

  @SuppressLint("SoonBlockedPrivateApi")
  fun Application.startOnce() {
      Log.println(Log.INFO, TAG, "startOnce: is started? ${started.get()}")
    if (started.getAndSet(true)) {
        Log.println(Log.ERROR, TAG, "startOnce: YES IT WAS!! from = ${StringUtils.getStackTrace()}")
        return
    }

    runCatching {
      ViewConfiguration::class
          .java
          .getDeclaredField("sHasPermanentMenuKey")
          .apply { isAccessible = true }
          .setBoolean(ViewConfiguration.get(this), false)
    }

      Log.println(Log.ERROR, TAG, "startOnce: this = $this")
    val koin = startKoin(applicationContext)

    koin.get<BugReporter>().attachToMainThread(this)

    PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

    koin.get<ScheduledReceiver>().start()
    koin.get<ToastPresenter>().start()
    koin.get<AlertServicePusher>()
    koin.get<BackgroundNotifications>()

    createNotificationChannels()

    // must be started the last, because otherwise we may loose intents from it.
//    val alarmsLogger = koin.logger("Alarms")
    koin.get<Alarms>().start()
//    alarmsLogger.debug { "Started alarms, SDK is " + Build.VERSION.SDK_INT }
    // start scheduling alarms after all alarms have been started
    koin.get<AlarmsScheduler>().start()

    with(koin.get<Store>()) {
      // register logging after startup has finished to avoid logging( O(n) instead of O(n log n) )
      alarms()
          .distinctUntilChanged()
          .map { it.toSet() }
          .startWith(emptySet<AlarmValue>())
          .buffer(2, 1)
          .map { (prev, next) -> next.minus(prev).map { it.toString() } }
          .distinctUntilChanged()
          .subscribe { lines -> lines.forEach {
//              alarmsLogger.debug { it }
          } }
    }
  }
}
