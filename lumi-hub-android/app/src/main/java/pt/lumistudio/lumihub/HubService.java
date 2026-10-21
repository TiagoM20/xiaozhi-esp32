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
    private XiaozhiMcpBridge mcpBridge;
    private DeskVoiceGateway deskGateway;
    private LumiCoreSyncServer coreSync;

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
        mcpBridge = new XiaozhiMcpBridge(this);
        mcpBridge.start();
        syncCoreConnection();
        syncDeskGateway();
    }

    /** Permite ligar/desligar a ponte sem reiniciar o serviço e sem cortar o CORE. */
    private void syncDeskGateway() {
        boolean enabled=getSharedPreferences("hub",MODE_PRIVATE)
            .getBoolean("desk_gateway_enabled",false);
        if(enabled) {
            if(deskGateway!=null)return;
            deskGateway=new DeskVoiceGateway(this);
            deskGateway.start();
        } else if(deskGateway!=null) {
            deskGateway.close();
            deskGateway=null;
        }
    }

    /** Configura apenas a porta CORE, sem derrubar a ponte da DESK ou a central. */
    private void syncCoreConnection() {
        boolean enabled=getSharedPreferences("hub",MODE_PRIVATE)
            .getBoolean("lumi_core_sync_enabled",false);
        if(enabled) {
            if(coreSync!=null)return;
            try {
                coreSync=new LumiCoreSyncServer(this);
                coreSync.start();
                getSharedPreferences("hub",MODE_PRIVATE).edit()
                    .putBoolean("lumi_core_sync_running",true)
                    .putString("lumi_core_sync_error","").apply();
            }catch(Exception error){
                if(coreSync!=null)coreSync.close();
                coreSync=null;
                getSharedPreferences("hub",MODE_PRIVATE).edit()
                    .putBoolean("lumi_core_sync_running",false)
                    .putString("lumi_core_sync_error",
                        error.getClass().getSimpleName()).apply();
                DeskLog.event(this,"CORE","Servidor 8770: falha "+error.getClass().getSimpleName());
            }
        }else{
            if(coreSync!=null){coreSync.close();coreSync=null;}
            getSharedPreferences("hub",MODE_PRIVATE).edit()
                .putBoolean("lumi_core_sync_running",false)
                .putString("lumi_core_sync_error","").apply();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        syncCoreConnection();
        syncDeskGateway();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (coreSync != null) {coreSync.close();coreSync=null;}
        getSharedPreferences("hub",MODE_PRIVATE).edit()
            .putBoolean("lumi_core_sync_running",false).apply();
        if (deskGateway != null) deskGateway.close();
        if (mcpBridge != null) mcpBridge.stop();
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        getSharedPreferences("hub", MODE_PRIVATE).edit().putBoolean("service_running", false).apply();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
