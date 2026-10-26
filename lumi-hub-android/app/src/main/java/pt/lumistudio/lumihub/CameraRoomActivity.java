package pt.lumistudio.lumihub;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;

/**
 * Previsualizacao LOCAL da camera do dispositivo Android. Sem gravacao ou rede.
 * A camera e libertada quando se fecha o ecrã ou quando a app perde o foco.
 */
@SuppressWarnings("deprecation")
public final class CameraRoomActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView preview;
    private SurfaceHolder holder;
    private Camera camera;
    private TextView state;
    private int chosen = -1;
    private boolean ready = false;
    private boolean visible = false;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        chosen=getPreferences(MODE_PRIVATE).getInt("preferred_camera",-1);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12,18,31));
        root.setPadding(14,16,14,12);
        TextView title=new TextView(this);
        title.setText("CÂMARA LOCAL  ·  "+android.os.Build.MODEL);
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView privacy=new TextView(this);
        privacy.setText("Pré-visualização local. Não grava nem transmite imagens.");
        privacy.setTextColor(Color.rgb(180,205,220));
        privacy.setTextSize(13);
        privacy.setGravity(Gravity.CENTER);
        root.addView(privacy);
        preview=new SurfaceView(this);
        holder=preview.getHolder();
        holder.addCallback(this);
        LinearLayout.LayoutParams videoParams=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1f);
        videoParams.topMargin=16;
        root.addView(preview,videoParams);
        state=new TextView(this);
        state.setText("A preparar a câmara...");
        state.setTextSize(13);
        state.setTextColor(Color.WHITE);
        state.setGravity(Gravity.CENTER);
        root.addView(state);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button front=new Button(this);
        front.setText("Frontal");
        front.setAllCaps(false);
        front.setOnClickListener(v->useFacing(Camera.CameraInfo.CAMERA_FACING_FRONT));
        actions.addView(front,new LinearLayout.LayoutParams(0,64,1f));
        Button rear=new Button(this);
        rear.setText("Traseira");
        rear.setAllCaps(false);
        rear.setOnClickListener(v->useFacing(Camera.CameraInfo.CAMERA_FACING_BACK));
        actions.addView(rear,new LinearLayout.LayoutParams(0,64,1f));
        Button close=new Button(this);
        close.setText("Fechar");
        close.setAllCaps(false);
        close.setOnClickListener(v->finish());
        actions.addView(close,new LinearLayout.LayoutParams(0,64,1f));
        root.addView(actions);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        visible=true;
        startCamera();
    }
    @Override protected void onPause() {
        visible=false;
        stopCamera();
        super.onPause();
    }
    @Override protected void onDestroy() {
        stopCamera();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override public void surfaceCreated(SurfaceHolder surface) {
        ready=true;
        startCamera();
    }
    @Override public void surfaceChanged(SurfaceHolder surface,int format,int width,int height) {
        ready=width>0&&height>0;
        startCamera();
    }
    @Override public void surfaceDestroyed(SurfaceHolder surface) {
        ready=false;
        stopCamera();
    }

    private int selectCamera() {
        int n=Camera.getNumberOfCameras();
        if(n==0) return -1;
        int desired=chosen;
        if(desired>=0 && desired<n) return desired;
        Camera.CameraInfo info=new Camera.CameraInfo();
        for(int i=0;i<n;i++){
            Camera.getCameraInfo(i,info);
            if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return 0;
    }

    private void startCamera() {
        if(!visible || !ready || holder==null || camera!=null) return;
        try {
            int id=selectCamera();
            if(id<0) { state.setText("O tablet não tem câmaras disponíveis.");return; }
            camera=Camera.open(id);
            camera.setDisplayOrientation(rotationFor(id));
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            chosen=id;
            Camera.CameraInfo info=new Camera.CameraInfo();
            Camera.getCameraInfo(id,info);
            state.setText("Pré-visualização "+(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT
                ?"frontal":"traseira")+" · só neste dispositivo.");
        } catch(Exception ex) {
            stopCamera();
            state.setText("Não foi possível abrir a câmara: " +
                ex.getClass().getSimpleName());
        }
    }

    private int rotationFor(int cameraId) {
        Camera.CameraInfo info=new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId,info);
        int rotation=getWindowManager().getDefaultDisplay().getRotation();
        int degrees=rotation==Surface.ROTATION_90?90:
            rotation==Surface.ROTATION_180?180:
            rotation==Surface.ROTATION_270?270:0;
        if(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT)
            return (360-((info.orientation+degrees)%360))%360;
        return (info.orientation-degrees+360)%360;
    }

    private void useFacing(int facing) {
        int target=-1;
        Camera.CameraInfo info=new Camera.CameraInfo();
        for(int i=0;i<Camera.getNumberOfCameras();i++){
            Camera.getCameraInfo(i,info);
            if(info.facing==facing){target=i;break;}
        }
        if(target<0){
            state.setText(facing==Camera.CameraInfo.CAMERA_FACING_FRONT
                ?"Este dispositivo não tem câmara frontal disponível."
                :"Este dispositivo não tem câmara traseira disponível.");
            return;
        }
        if(chosen==target && camera!=null)return;
        stopCamera();
        chosen=target;
        getPreferences(MODE_PRIVATE).edit().putInt("preferred_camera",chosen).apply();
        state.setText("A abrir câmara "+(facing==Camera.CameraInfo.CAMERA_FACING_FRONT
            ?"frontal...":"traseira..."));
        startCamera();
    }

    private void stopCamera() {
        if(camera==null) return;
        try{camera.stopPreview();}catch(Exception ignored){}
        try{camera.setPreviewCallback(null);}catch(Exception ignored){}
        try{camera.release();}catch(Exception ignored){}
        camera=null;
    }
}
