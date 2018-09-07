package com.xiaojie.smartcamera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * 　　　　　　　　┏┓　　　┏┓+ +
 * 　　　　　　　┏┛┻━━━┛┻┓ + +
 * 　　　　　　　┃　　　　　　　┃
 * 　　　　　　　┃　　　━　　　┃ ++ + + +
 * 　　　　　　 ████━████ ┃+
 * 　　　　　　　┃　　　　　　　┃ +
 * 　　　　　　　┃　　　┻　　　┃
 * 　　　　　　　┃　　　　　　　┃ + +
 * 　　　　　　　┗━┓　　　┏━┛
 * 　　　　　　　　　┃　　　┃
 * 　　　　　　　　　┃　　　┃ + + + +
 * 　　　　　　　　　┃　　　┃　　　　Code is far away from bug with the animal protecting
 * 　　　　　　　　　┃　　　┃ + 　　　　神兽保佑,代码无bug
 * 　　　　　　　　　┃　　　┃
 * 　　　　　　　　　┃　　　┃　　+
 * 　　　　　　　　　┃　 　　┗━━━┓ + +
 * 　　　　　　　　　┃ 　　　　　　　┣┓
 * 　　　　　　　　　┃ 　　　　　　　┏┛
 * 　　　　　　　　　┗┓┓┏━┳┓┏┛ + + + +
 * 　　　　　　　　　　┃┫┫　┃┫┫
 * 　　　　　　　　　　┗┻┛　┗┻┛+ + + +
 */

public class FaceView extends View {
    private Camera.Face[] mFaces;
    private Paint mPaint;
    private Matrix matrix = new Matrix();
    private RectF mRectF = new RectF();
    private PointF mid;
    private static DataCallback mDataCallback;


    public void setFaces(Camera.Face[] faces) {
        mFaces = faces;
        invalidate();
    }

    public FaceView(Context context) {
        super(context);
        init(context);
    }

    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void init(Context context) {
        mPaint = new Paint();
        mPaint.setColor(Color.MAGENTA);
        mPaint.setStrokeWidth(3f);
        mPaint.setStyle(Paint.Style.STROKE);



    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //绘制检测的人脸
        if ( null == mFaces || mFaces.length < 1) {
            return;
        }
        FloatService.prepareMatrix(matrix, true, 90, getWidth(), getHeight());
        canvas.save();
        matrix.postRotate(0);
        canvas.rotate(-0);
        for (int i = 0; i < mFaces.length; i++) {
            mRectF.set(mFaces[i].rect);
            matrix.mapRect(mRectF);
            canvas.drawRect(mRectF, mPaint);
            mid = new PointF();
            mid.x = mRectF.centerX();
            mid.y = mRectF.centerY();
            if (null == mDataCallback)
            {
                Log.d("mDataCallback is ","full");
            }
            else {
            mDataCallback.getCenterData(mid);
            Log.d("mid_x","mid_x"+mid.x);
            }

        }
        canvas.restore();
    }
    public PointF getCenterPoint(){
        return mid;
    }
    public interface DataCallback{
        void getCenterData(PointF data);
    }
    public void SetDataCallback(DataCallback mDataCallback){
        this.mDataCallback = mDataCallback;
    }
}
