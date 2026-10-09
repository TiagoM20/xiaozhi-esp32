package pt.lumistudio.lumihub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;

public final class HubService extends Service {
    private static final String CHANNEL = "lumi_hub_service";
    private BroadcastReceiver batteryReceiver;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            "LUMI Hub em funcionamento", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("LUMI Hub ativo")
            .setContentText("Monitorização local da bateria ativa")
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
        startForeground(101, notification);

        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                int pct = (scale > 0 && level >= 0) ? level * 100 / scale : -1;
                getSharedPreferences("hub", MODE_PRIVATE).edit()
                    .putInt("battery", pct)
                    .putInt("charging_status", status)
                    .putInt("temperature_tenths", temperature)
                    .putLong("last_update", System.currentTimeMillis())
                    .apply();
                // No smart plug connected yet. Monitoring ONLY; no switching hardware.
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        getSharedPreferences("hub", MODE_PRIVATE).edit().putBoolean("service_running", true).apply();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        getSharedPreferences("hub", MODE_PRIVATE).edit().putBoolean("service_running", false).apply();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
