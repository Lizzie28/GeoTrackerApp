package com.example.geotrackerapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "geo_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null || geofencingEvent.hasError()) return;

        int transition = geofencingEvent.getGeofenceTransition();

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            if (triggeringGeofences != null && !triggeringGeofences.isEmpty()) {
                // Get the first geofence that triggered the event
                Geofence geofence = triggeringGeofences.get(0);
                String name = geofence.getRequestId();
                
                // We need to fetch the coordinates from DB to provide directions
                new Thread(() -> {
                    LocationEntity entity = null;
                    List<LocationEntity> list = AppDatabase.getInstance(context).locationDao().getAllLocations();
                    for (LocationEntity loc : list) {
                        if (loc.getName().equals(name)) {
                            entity = loc;
                            break;
                        }
                    }
                    
                    if (entity != null) {
                        sendNotification(context, "You are near: " + name, entity);
                    } else {
                        // Fallback if entity not found (shouldn't happen)
                        sendNotification(context, "You are near: " + name, null);
                    }
                }).start();
            }
        }
    }

    private void sendNotification(Context context, String message, LocationEntity entity) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Geofence Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MapsActivity.class);
        if (entity != null) {
            intent.putExtra("navigateLat", entity.getLatitude());
            intent.putExtra("navigateLng", entity.getLongitude());
            intent.putExtra("autoRoute", true);
        }
        
        // Use a unique requestCode so intents don't overwrite each other if multiple geofences trigger
        int requestCode = (entity != null) ? entity.getId() : 0;

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("GeoTracker Alert")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(requestCode, builder.build());
    }
}