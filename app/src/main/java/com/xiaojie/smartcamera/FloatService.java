package com.xiaojie.smartcamera;

import android.app.Service;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.hardware.Camera;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;
import java.util.List;


public class FloatService extends Service implements SurfaceHolder.Callback,Camera.FaceDetectionListener{

    public static boolean isStarted = false;

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private View mFloatLayout;

    private int mLastX = 0,mLastY = 0;
    private int mStartX = 0,mStartY = 0;

    private Camera mCamera;
    private SurfaceHolder mHolder;
    private FaceView mFaceView;
    private boolean threadDisable = false;
    private PointF mCenter;


    @Override
    public void onCreate() {
        super.onCreate();
        createWindowManager();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createFloatWindow();
        clickFloatWindow();
        return super.onStartCommand(intent, flags, startId);

    }

    private void createWindowManager() {
        isStarted = true;
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mLayoutParams = new WindowManager.LayoutParams();
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        mLayoutParams.format = PixelFormat.RGBA_8888;
        mLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;;
        mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.x = 70;
        mLayoutParams.y = 210;
    }


    private void createFloatWindow() {
        if (Settings.canDrawOverlays(this)) {
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            mFloatLayout = layoutInflater.inflate(R.layout.layout_window, null);

            SurfaceView surfaceView = (SurfaceView) mFloatLayout.findViewById(R.id.surfaceView);

            mFaceView = (FaceView) mFloatLayout.findViewById(R.id.faceView);
            mHolder = surfaceView.getHolder();
            mHolder.addCallback(this);
            openCamera();

            mWindowManager.addView(mFloatLayout, mLayoutParams);

        }
    }

    public void openCamera() {
        mCamera = Camera.open(1);
        mCamera.setFaceDetectionListener(this);
        Log.e("CameraActivity", "mCamera:" + mCamera);
    }
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        if (mCamera == null) {
            return;
        }


        Log.d("CameraActivity", "mCamera:" + mCamera);
        try {
            Camera.Parameters parameters = mCamera.getParameters();

            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains("continuous-video")) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }

            mCamera.setParameters(parameters);
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();

            /*先判断手机是否支持人脸检测功能，若支持则开启*/
            if (mCamera.getParameters().getMaxNumDetectedFaces() > 0) {
                mCamera.startFaceDetection();
            }
//            PointF mid = mFaceView.getCenterPoint();
//            Log.d("mid_x ","mid_x"+mid.x);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (mCamera != null) {
            if(mCamera.getParameters().getMaxNumDetectedFaces()>0) {
                mCamera.stopFaceDetection();
            }
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
       // Toast.makeText(this, "faces.length:" + faces.length, Toast.LENGTH_SHORT).show();
        mFaceView.setFaces(faces);
    }

    //用于将返回的人脸矩阵转换为可以正常在取景区显示的矩阵
    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                                     int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }


    private void clickFloatWindow() {

        mFaceView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mStartX = (int) event.getRawX();
                        mStartY = (int) event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mLastX = (int) event.getRawX();
                        mLastY = (int) event.getRawY();
                        if (needIntercept()) {
                            //getRawX是触摸位置相对于屏幕的坐标，getX是相对于按钮的坐标
                            mLayoutParams.x = (int) event.getRawX() - mFloatLayout.getMeasuredWidth() / 2;
                            mLayoutParams.y = (int) event.getRawY() - mFloatLayout.getMeasuredHeight() / 2;
                            mWindowManager.updateViewLayout(mFloatLayout, mLayoutParams);
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (needIntercept()) {
                            mLastX = (int) event.getX();
                            mLastY = (int) event.getY();
                            return true;
                        }
                        break;
                    default:
                        break;
                }
                return false;
            }
        });

        mFaceView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Intent intent = new Intent(FloatService.this, BluetoothActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
//                if(mFloatLayout != null){
//                    mWindowManager.removeView(mFloatLayout);
//                }
//                if (mCamera != null) {
//                    if(mCamera.getParameters().getMaxNumDetectedFaces()>0) {
//                        mCamera.stopFaceDetection();
//                    }
//
//                    mCamera.stopPreview();
//                mCamera.release();
//                mCamera = null;
//            }
            }
        });
    }

    /**
     * 是否拦截
     * @return true:拦截;false:不拦截.
     */
    private boolean needIntercept() {
        if (Math.abs(mStartX - mLastX) > 30 || Math.abs(mStartY - mLastY) > 30) {
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mFloatLayout != null){
            mWindowManager.removeView(mFloatLayout);
        }
        if (mCamera != null) {
            if(mCamera.getParameters().getMaxNumDetectedFaces()>0) {
                mCamera.stopFaceDetection();
            }
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            threadDisable = true;
        }


        Log.d("Camera","Destroy");
    }



}
