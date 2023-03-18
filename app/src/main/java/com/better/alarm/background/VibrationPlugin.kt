package com.better.alarm.background

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.better.alarm.logger.Logger
import com.better.alarm.oreo
import com.better.alarm.preOreo
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.functions.BiFunction
import java.util.concurrent.TimeUnit

/** Vibrates when told to. */
class VibrationPlugin(
    private val log: Logger,
    private val vibrator: Vibrator,
    private val fadeInTimeInMillis: Observable<Int>,
    private val scheduler: Scheduler,
    private val vibratePreference: Observable<Boolean>
) : AlertPlugin {
    companion object {
        private const val TAG = "VibrationPlugin"
    }
    override fun toString(): String {
        return super.toString() + "@${hashCode()}"
    }

  private val vibratePattern: LongArray = longArrayOf(500, 500)
  private var disposable = Disposables.empty()

  override fun go(
      alarm: PluginAlarmData,
      prealarm: Boolean,
      targetVolume: Observable<TargetVolume>
  ): Disposable {
    disposable.dispose()
    val subscription =
        Observable.combineLatest(
                vibratePreference,
                targetVolume,
                BiFunction<Boolean, TargetVolume, TargetVolume> { isEnabled, volume ->
                  if (isEnabled) volume else TargetVolume.MUTED
                })
            .distinctUntilChanged()
            .switchMap { volume ->
                Log.d(TAG, "go: volume = $volume")
//                Observable.just(255)
//                fadeInSlow(false)
              when (volume) {
                TargetVolume.MUTED -> Observable.just(0)
                TargetVolume.FADED_IN -> fadeInSlow(prealarm)
                TargetVolume.FADED_IN_FAST -> Observable.just(255)
              }
            }
            .distinctUntilChanged()
            .subscribe { amplitude ->
              if (amplitude != 0) {
                oreo {
//                  log.debug { "Starting vibration with amplitude $amplitude" }
                    Log.println(Log.INFO, TAG, "Oreo go: vibrating... $amplitude")
                  vibrator.vibrate(
                      VibrationEffect.createWaveform(vibratePattern, intArrayOf(0, amplitude), 0))
                }

                preOreo {
                    Log.println(Log.INFO, TAG, "preOreo go: vibrating... $amplitude")

//                  log.debug { "Starting vibration" }
                  @Suppress("DEPRECATION") // old target API
                  vibrator.vibrate(vibratePattern, 0)
                }
              } else {
//                log.debug { "Canceling vibration" }
                vibrator.cancel()
              }
            }

    disposable = CompositeDisposable(subscription, Disposables.fromAction { vibrator.cancel() })

    return disposable
  }

  private val defaultAmplidute: Int
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          VibrationEffect.DEFAULT_AMPLITUDE
        } else {
          -1
        }

  /**
   * TODO try this val amplitudes = when { prealarm -> listOf(5, 20, 45, 80, 128) else -> listOf(10,
   * 40, 90, 160, 255) }
   */
  private fun fadeInSlow(prealarm: Boolean): Observable<Int> {
      Log.d(TAG, "fadeInSlow: preAlarm? $prealarm")
    return when {
      prealarm -> Observable.just(0)
      else ->
          fadeInTimeInMillis.firstOrError().flatMapObservable { fadeInTimeInMillis ->
            Observable.just(defaultAmplidute)
                .delay(fadeInTimeInMillis.toLong(), TimeUnit.MILLISECONDS, scheduler)
                .startWith(0)
                .doOnComplete {
//                    log.debug { "Completed vibration fade-in" } 
                }
          }
    }
  }
}
