package com.better.alarm.interfaces;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.better.alarm.BuildConfig;
import com.better.alarm.OreoKt;
import com.better.alarm.model.AlarmsReceiver;

public class PresentationToModelIntents {

  public static final String ACTION_REQUEST_SNOOZE =
      BuildConfig.APPLICATION_ID + ".model.interfaces.ServiceIntents.ACTION_REQUEST_SNOOZE";
  public static final String ACTION_REQUEST_DISMISS =
      BuildConfig.APPLICATION_ID + ".model.interfaces.ServiceIntents.ACTION_REQUEST_DISMISS";
  public static final String ACTION_REQUEST_SKIP =
      BuildConfig.APPLICATION_ID + ".model.interfaces.ServiceIntents.ACTION_REQUEST_SKIP";

  public static PendingIntent createPendingIntent(Context context, String action, int id) {
    Intent intent = new Intent(action);
    intent.putExtra(Intents.EXTRA_ID, id);
    intent.setClass(context, AlarmsReceiver.class);
    return PendingIntent.getBroadcast(context, id, intent, OreoKt.pendingIntentUpdateCurrentFlag());
  }

//  public static PendingIntent createIntentFilter(BroadcastReceiver receiver, Context context, String action, int id) {
//    IntentFilter intentF = new IntentFilter(action);
//    context.registerReceiver(receiver, intentF);
//    return PendingIntent.getBroadcast(context, id, intentF, OreoKt.pendingIntentUpdateCurrentFlag());
////    Intent intent = new Intent(action);
////
////    intent.putExtra(Intents.EXTRA_ID, id);
////    intent.setClass(context, AlarmsReceiver.class);
////    return PendingIntent.getBroadcast(context, id, intent, OreoKt.pendingIntentUpdateCurrentFlag());
//  }
}
