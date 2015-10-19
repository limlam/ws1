package com.wigl.wigl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class CaptureActivity extends Activity {
    private static final String TAG = "CaptureActivity";

    public static final String ACTION_CAPTURE = "com.wigl.wigl.ACTION_CAPTURE";

    public static final String CAPTURE_TIME = "com.wigl.wigl.CAPTURE_TIME";

    private Camera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.capture);

        Log.d(TAG, "**** calling activity: " + getCallingActivity());
        Log.d(TAG, "**** calling package: " + getCallingPackage());

        long captureTime = getIntent().getLongExtra(CAPTURE_TIME, 0);
        Log.d(TAG, "CaptureActivity.onCreate captureTime: " + captureTime);
        if (captureTime > 0) {
            TimerTask task = new CaptureTimer();
            new Timer().schedule(task, captureTime - System.currentTimeMillis());
        } else {
            Log.e(TAG, "Capture time was not extracted from intent");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (safeCameraOpen(0)) {
            // Create our Preview view and set it as the content of our activity.
            SurfaceView mPreview = new CameraPreview(this, mCamera);
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);

            setupCrosshair();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCameraAndPreview();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

    private void releaseCameraAndPreview() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null){
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here
            setCameraDisplayOrientation(getWindowManager(), 0, mCamera);

            // start preview with new settings
            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();

            } catch (Exception e){
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }
        }

        public void setCameraDisplayOrientation(WindowManager windowManager, int cameraId, android.hardware.Camera camera) {
            android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            int rotation = windowManager.getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
            camera.setDisplayOrientation(result);
        }
    }
    
    private class CaptureTimer extends TimerTask {
        @Override
        public void run() {
            mCamera.takePicture(null, null, getJpeg());
        }
    }

    @NonNull
    private Camera.PictureCallback getJpeg() {
        return new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                File pictureFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "wiglPic-" + System.currentTimeMillis() + ".jpg");
                if (pictureFile == null) {
                    Log.e(TAG, "Error creating media file");
                    return;
                }

                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                    Log.d(TAG, "File created: " + pictureFile.getAbsolutePath());

                    Intent resultData = new Intent();
                    resultData.setData(Uri.fromFile(pictureFile));
                    setResult(RESULT_OK, resultData);
                    Log.d(TAG, "Result set");
                    finish();
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }
        };
    }

    private void setupCrosshair() {

        (findViewById(R.id.crosshair_center)).bringToFront();

        float tickLength = getResources().getDimension(R.dimen.crosshair_tick_length);
        float tickWidth = getResources().getDimension(R.dimen.crosshair_tick_width);
        float cornerDistance = getResources().getDimension(R.dimen.corner_distance);
        float cornerDistancePrime = cornerDistance - (tickLength / 2) + tickWidth;

        View crosshair_br = findViewById(R.id.crosshair_br);
        crosshair_br.setTranslationX(cornerDistancePrime);
        crosshair_br.setTranslationY(cornerDistance);
        crosshair_br.bringToFront();

        View crosshair_rb = findViewById(R.id.crosshair_rb);
        crosshair_rb.setRotation(90);
        crosshair_rb.setTranslationX(cornerDistance);
        crosshair_rb.setTranslationY(cornerDistancePrime);
        crosshair_rb.bringToFront();

        View crosshair_bl = findViewById(R.id.crosshair_bl);
        crosshair_bl.setTranslationX(-cornerDistancePrime);
        crosshair_bl.setTranslationY(cornerDistance);
        crosshair_bl.bringToFront();

        View crosshair_lb = findViewById(R.id.crosshair_lb);
        crosshair_lb.setRotation(90);
        crosshair_lb.setTranslationX(-cornerDistance);
        crosshair_lb.setTranslationY(cornerDistancePrime);
        crosshair_lb.bringToFront();

        View crosshair_tr = findViewById(R.id.crosshair_tr);
        crosshair_tr.setTranslationX(cornerDistancePrime);
        crosshair_tr.setTranslationY(-cornerDistance);
        crosshair_tr.bringToFront();

        View crosshair_rt = findViewById(R.id.crosshair_rt);
        crosshair_rt.setRotation(90);
        crosshair_rt.setTranslationX(cornerDistance);
        crosshair_rt.setTranslationY(-cornerDistancePrime);
        crosshair_rt.bringToFront();

        View crosshair_tl = findViewById(R.id.crosshair_tl);
        crosshair_tl.setTranslationX(-cornerDistancePrime);
        crosshair_tl.setTranslationY(-cornerDistance);
        crosshair_tl.bringToFront();

        View crosshair_lt = findViewById(R.id.crosshair_lt);
        crosshair_lt.setRotation(90);
        crosshair_lt.setTranslationX(-cornerDistance);
        crosshair_lt.setTranslationY(-cornerDistancePrime);
        crosshair_lt.bringToFront();
    }
}
