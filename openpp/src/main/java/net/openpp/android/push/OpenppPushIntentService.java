/*
 * Copyright (C) 2015 webware,Inc
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
package net.openpp.android.push;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * @author shiroko@webware.co.jp
 */
public class OpenppPushIntentService extends IntentService {
    public static final int NOTIFICATION_ID = 1;
    public static final String DEFAULT_TITLE = "GCM Notification";
    NotificationCompat.Builder mBuilder;

    public OpenppPushIntentService() {
        super("OpenppPushIntentService");
    }
    public static final String TAG = "OpenppPushIntentService";

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            /*
             * Filter messages based on message type. Since it is likely that GCM will be
             * extended in the future with new message types, just ignore any message types you're
             * not interested in, or that you don't recognize.
             */
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                Log.e(TAG, "Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                Log.i(TAG, "Deleted messages on server: " + extras.toString());
                // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                // This loop represents the service doing some work.
                for (int i = 0; i < 5; i++) {
                    Log.i(TAG, "Working... " + (i + 1)
                            + "/5 @ " + SystemClock.elapsedRealtime());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                    }
                }
                Log.i(TAG, "Completed work @ " + SystemClock.elapsedRealtime());
                // Post notification of received message.
                sendNotification(extras);
                Log.i(TAG, "Received: " + extras.toString());
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        OpenppPushBroadcastReceiver.completeWakefulIntent(intent);
    }

    /**
     * Put the message into a notification and post it.
     * @param extras
     */
    private void sendNotification(Bundle extras) {
        NotificationManager notificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, OpenppPushManager.getInstance().getWakeupActivity()), 0);

        String title = extras.getString("title");
        mBuilder = new NotificationCompat.Builder(this)
                        .setSmallIcon( OpenppPushManager.getInstance().getIconResourceId())
                        .setContentTitle(title == null ? DEFAULT_TITLE : title)
                        .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(extras.getString("message")))
                        .setContentText(extras.getString("message"))
                        .setAutoCancel(true);

        mBuilder.setContentIntent(contentIntent);
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }
}
