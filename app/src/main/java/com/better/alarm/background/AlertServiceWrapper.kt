package com.better.alarm.background

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Vibrator
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.better.alarm.CHANNEL_ID
import com.better.alarm.R
import com.better.alarm.configuration.AlarmApplication
import com.better.alarm.configuration.Prefs
import com.better.alarm.configuration.globalGet
import com.better.alarm.configuration.globalInject
import com.better.alarm.configuration.logger
import com.better.alarm.interfaces.IAlarmsManager
import com.better.alarm.interfaces.Intents
import com.better.alarm.logger.LoggerFactory
import com.better.alarm.notificationBuilder
import com.better.alarm.oreo
import com.better.alarm.util.Service
import com.better.alarm.wakelock.WakeLockManager
import com.better.alarm.wakelock.Wakelocks
import io.reactivex.Observable
import io.reactivex.Scheduler
import java.util.concurrent.Executor
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * This wraps [AlertService], does dependency injection and delegates everything to it. This way we
 * can unit-test [AlertService], [KlaxonPlugin], [VibrationPlugin] and [NotificationsPlugin].
 */
class AlertServiceWrapper : Service() {
  companion object {
      private const val TAG = "AlertServiceWrapper"
    fun module(): Module = module {
      factory(named("inCall")) {
        val telephonyManager: TelephonyManager = get()
        when {
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                  telephonyManager.observeCallState(get<Context>().mainExecutor)
              else -> telephonyManager.observePhoneState()
            }
            .distinctUntilChanged()
            .onErrorReturn { e ->
              logger("inCall").error { "Failed to subscribe with PhoneStateListener: $e" }
              false
            }
            .replay(1)
            .refCount()
      }

      factory<AlertPlugin>(named("KlaxonPlugin")) {
        KlaxonPlugin(
            log = logger("AlertService"),
            playerFactory = { PlayerWrapper(get(), get(), logger("AlertService")) },
            prealarmVolume = get<Prefs>().preAlarmVolume.observe(),
            fadeInTimeInMillis = get<Prefs>().fadeInTimeInSeconds.observe().map { it * 1000 },
            inCall = get(named("inCall")),
            scheduler = get()
        )
      }

      factory<AlertPlugin>(named("VibrationPlugin")) {
        VibrationPlugin(
            log = logger("AlertService"),
            vibrator = get(),
            fadeInTimeInMillis = get<Prefs>().fadeInTimeInSeconds.observe().map {
                it * 1000
                                                                                },
            scheduler = get(),
            vibratePreference = get<Prefs>().vibrate.observe())
      }

      single<NotificationsPlugin> {
        NotificationsPlugin(
            logger = logger("AlertService"), mContext = get(), nm = get(), enclosingService = get())
      }

      single {
        AlertService(
            log = logger("AlertService"),
            wakelocks = get(),
            alarms = get(),
            inCall = get(named("inCall")),
            plugins = getAll(),
            notifications = get(),
            enclosing = get())
      }
    }

    @Suppress("DEPRECATION")
    private fun TelephonyManager.observePhoneState() =
        Observable.create<Boolean> { emitter ->
          val listener =
              object : PhoneStateListener() {
                @Deprecated(
                    "Deprecated in Java", ReplaceWith("TelephonyCallback.CallStateListener"))
                override fun onCallStateChanged(state: Int, ignored: String) {
                  emitter.onNext(state != TelephonyManager.CALL_STATE_IDLE)
                }
              }

          emitter.onNext(callState != TelephonyManager.CALL_STATE_IDLE)

          emitter.setCancellable { listen(listener, PhoneStateListener.LISTEN_NONE) }

          listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun TelephonyManager.observeCallState(
        executor: Executor,
    ): Observable<Boolean> {
      return Observable.create { emitter ->
        val cb =
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
              override fun onCallStateChanged(state: Int) {
                emitter.onNext(state != TelephonyManager.CALL_STATE_IDLE)
              }
            }

        emitter.setCancellable { unregisterTelephonyCallback(cb) }
        registerTelephonyCallback(executor, cb)
      }
    }
  }

//    init {
//        Log.println(Log.WARN, TAG, ": built this $this")
//    }

    override fun toString(): String {
        return super.toString() + "@${hashCode()}"
    }

  private val wakelocks: WakeLockManager by globalInject()
  private lateinit var alertService: AlertService

  override fun onCreate() {
//      Log.d(TAG, "onCreate: this = $this")
    AlarmApplication.startOnce(application)
    alertService =
        koinApplication {
              modules(module())
              modules(
                  module {
                    factory { globalGet<LoggerFactory>() }
                    factory { globalGet<Wakelocks>() }
                    factory { globalGet<IAlarmsManager>() }
                    factory { globalGet<TelephonyManager>() }
                    factory<Context> { this@AlertServiceWrapper }
                    factory { globalGet<NotificationManager>() }
                    factory { globalGet<Prefs>() }
                    factory { globalGet<Scheduler>() }
                    factory { globalGet<Vibrator>() }
                    factory { globalGet<Resources>() }
                  })
              modules(
                  module {
                    factory<EnclosingService> {
                      class EnclosingServiceIR : EnclosingService {
                        override fun handleUnwantedEvent() {
                          logger("SERVICE").warning { "handleUnwantedEvent()" }
                          oreo {
                            val notification =
                                notificationBuilder(CHANNEL_ID) {
                                  setContentTitle("Background")
                                  setContentText("Background")
                                  setSmallIcon(R.drawable.stat_notify_alarm)
                                  setOngoing(true)
                                }
                            this@AlertServiceWrapper.startForeground(42, notification)
                          }
                          this@AlertServiceWrapper.stopSelf()
                        }

                        override fun startForeground(id: Int, notification: Notification) {
                          logger("SERVICE").debug { "startForeground!!! $id, $notification" }
                          this@AlertServiceWrapper.startForeground(id, notification)
                        }

                        override fun stopSelf() {
                          logger("SERVICE").warning { "stopSelf()" }
                          this@AlertServiceWrapper.stopSelf()
                        }
                      }

                      EnclosingServiceIR()
                    }
                  })
            }
            .koin
            .get()
  }

  override fun onDestroy() {
    alertService.onDestroy()

    oreo { stopForeground(true) }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//      Log.println(Log.WARN, TAG, "onStartCommand: this = $this" +
//          ",\n intent is = $intent")
    return if (intent == null) {
      // this also has to be delivered, because someone has to call startForeground()
      alertService.onStartCommand(Event.NullEvent())
      START_NOT_STICKY
    } else {
//        Log.println(Log.INFO, TAG, "onStartCommand: intent action = ${intent.action}")
        Log.println(Log.ERROR, TAG, "onStartCommand: this service is: $this" +
            ",\n action is: ${intent.action}")
      alertService.onStartCommand(
          when (intent.action) {
            Intents.ALARM_ALERT_ACTION -> Event.AlarmEvent(intent.getIntExtra(Intents.EXTRA_ID, -1))
            Intents.ALARM_PREALARM_ACTION ->
                Event.PrealarmEvent(intent.getIntExtra(Intents.EXTRA_ID, -1))
            Intents.ACTION_MUTE -> Event.MuteEvent()
            Intents.ACTION_DEMUTE -> Event.DemuteEvent()
            Intents.ALARM_DISMISS_ACTION ->
                Event.DismissEvent(intent.getIntExtra(Intents.EXTRA_ID, -1))
            else -> throw RuntimeException("Unknown action ${intent.action}")
          })

      val ret =
          when (intent.action) {
            Intents.ALARM_ALERT_ACTION,
            Intents.ALARM_PREALARM_ACTION,
            Intents.ACTION_MUTE,
            Intents.ACTION_DEMUTE -> START_STICKY
            Intents.ALARM_SNOOZE_ACTION,
            Intents.ALARM_DISMISS_ACTION,
            Intents.ACTION_SOUND_EXPIRED -> START_NOT_STICKY
            else -> throw RuntimeException("Unknown action ${intent.action}")
          }

      wakelocks.releaseTransitionWakeLock(intent)
      ret
    }
  }
}
