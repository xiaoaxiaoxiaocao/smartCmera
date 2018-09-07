package com.xiaojie.smartcamera;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

public class BluetoothActivity extends AppCompatActivity implements AdapterView.OnItemClickListener{

    //该dialog是扫描提示对话框
    private ProgressDialog dialogScan;
    //该dialog是连接提示对话框
    private ProgressDialog dialogConnect;
    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> paireDevices;
    private BluetoothSocket socket = null;
    private boolean mFloatWindowFlag = false;

    private Context context = this;
    private List<BluetoothDevice> list;
    private ListView listViewDevice;
    private ListViewAdapter mAdapter;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private AcceptThread mAcceptThread;
    private int mState = 0;

    private EditText editText = null;
    private FaceView mFaceView ;
    private boolean mConnectSuccessFlag = false;

    //回调得到人脸的中心坐标
    private Float Center_x;
    private Float Center_y;
    private boolean mConnectFinishFlag = false; //连接完成标志

    public static final int STATE_NONE = 0;       // 初始状态
    public static final int STATE_LISTEN = 1;     // 等待连接
    public static final int STATE_CONNECTING = 2; // 正在连接
    public static final int STATE_CONNECTED = 3;  // 已经连接上设备

    private static final int REQUEST_ENABLE_BT = 1;

    private String[] data ={"可用设备"};
    private static final String TAG = "Bluetooth";
    public static final UUID MY_UUID =UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private Handler handler = new Handler();
    //定时发送
    Runnable runnable = new Runnable() {
        @Override
        public void run() {

            if (true == mConnectSuccessFlag){
                mConnectSuccessFlag = false;
                Toast.makeText(BluetoothActivity.this,"连接成功！",Toast.LENGTH_SHORT).show();
            }
            if (true == mConnectFinishFlag && mConnectedThread!=null) {

                Log.d("data","Center_x"+Center_x);
                if(Center_x != null || Center_y != null) {
                    DecimalFormat df = new DecimalFormat("#.0");
                    String x = df.format(Center_x);
                    String y = df.format(Center_y);
                    String s = "["+"("+x+","+y+")"+"]";
                    write(s.getBytes());
                }
                //Toast.makeText(BluetoothActivity.this, "进来了", Toast.LENGTH_SHORT).show();
                Log.d("Bluetooth","in");

            }
            handler.postDelayed(runnable, 100);

        }
    };

    private IntentFilter filter;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(!list.contains(device)){
                    mAdapter.addData(device);
                }
            }else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                dialogScan.dismiss();
                Toast.makeText(context,"扫描完毕",Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final android.os.Handler mHandler = new android.os.Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case Constants.MESSAGE_TOAST:
                    if(null!=context){
                        Toast.makeText(context,msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setTitle("蓝牙相机");

        //悬浮窗打开标志
        mFloatWindowFlag = true;

        //editText = (EditText) findViewById(R.id.editText);

        mFaceView = new FaceView(this);
        mFaceView.SetDataCallback(new FaceView.DataCallback() {
            @Override
            public void getCenterData(PointF data) {
                Center_x = data.x;
                Center_y = data.y;
            }
        });


        //可用设备列表
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(BluetoothActivity.this,android.R.layout.simple_list_item_1,data);
        ListView listViewText = (ListView)findViewById(R.id.textName);
        listViewText.setAdapter(adapter);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //扫描到的设备列表
        list = new ArrayList<BluetoothDevice>();
        listViewDevice = (ListView)findViewById(R.id.deviceList);
        mAdapter = new ListViewAdapter(BluetoothActivity.this);
        mAdapter.setDataSource(list);
        listViewDevice.setAdapter(mAdapter);
        listViewDevice.setOnItemClickListener(this);

        checkBluetoothPermission();
        //将配过对的设备加入list
        paireDevices = mBluetoothAdapter.getBondedDevices();
        if(paireDevices.size()>0){
            for(BluetoothDevice device: paireDevices){
                mAdapter.addData(device);
            }
        }

        //注册
        filter = getIntentFilter();
        registerReceiver(receiver,filter);
        handler.postDelayed(runnable, 100);
    }


    @Override
    protected void onStop() {
        super.onStop();
        mFloatWindowFlag = false;
        Log.d("BlueTooth : ","OnStop");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK){
            //启用
        }else if(requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED){
            //未启用
            Toast.makeText(BluetoothActivity.this,"请打开蓝牙",Toast.LENGTH_SHORT).show();
            finish();
        }
        if (requestCode == 0) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show();
                mFloatWindowFlag = true;
                //startService(new Intent(BluetoothActivity.this, FloatService.class));
                //startActivity(new Intent(BluetoothActivity.this, BluetoothActivity.class));
                Log.d("MainActivity","Float Window");
                // Toast.makeText(this, "悬浮窗", Toast.LENGTH_SHORT).show();
            }
        }

    }

    /*
       校验蓝牙权限
      */
    private void checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            //校验是否已具有模糊定位权限
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(BluetoothActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_ENABLE_BT);
            } else {
                //具有权限

            }
        } else {
            //系统不高于6.0直接执行

        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bluetooth_list,menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.floatwindow, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
            case R.id.open:
                openBluetooth();
                break;
            case R.id.scan:
                scan();
                break;
            case R.id.close:
               closeBluetooth();
               break;
            case R.id.camera:
                if (!mBluetoothAdapter.isEnabled()){
                    Toast.makeText(this, "请先开启蓝牙！", Toast.LENGTH_LONG).show();
                }else {
                    if (mFloatWindowFlag == true) {
                        mFloatWindowFlag = false;
                        floatWindowPermission();
                    }
                }
                break;
            default:
                break;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void floatWindowPermission(){
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "当前无权限，请授权", Toast.LENGTH_SHORT);
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 0);
        } else {
            startService(new Intent(BluetoothActivity.this, FloatService.class));
            //Toast.makeText(this, "悬浮窗", Toast.LENGTH_SHORT).show();
            Log.d("MainActivity","Float Window");
        }
    }


    private void openBluetooth() {

        if (!mBluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);  //In order to enable bluetooth
            startActivityForResult(turnOn, 0);
            //Toast.makeText(this,"请打开蓝牙",Toast.LENGTH_SHORT).show();
            return;
        }else {
            Toast.makeText(getApplicationContext(), "蓝牙已打开", Toast.LENGTH_LONG).show();
            return;
        }
    }

    private void closeBluetooth() {
        mBluetoothAdapter.disable();  //to disable bluetooth
        Toast.makeText(getApplicationContext(),"蓝牙已关闭" ,Toast.LENGTH_LONG).show();
    }

    private void scan(){
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this,"请打开蓝牙",Toast.LENGTH_SHORT).show();
            return;
        }
        //开始扫描,在dialog中处理后退键事件，取消扫描
        mBluetoothAdapter.startDiscovery();
        dialogScan = new ProgressDialog(context);
        dialogScan.setMessage("正在扫描...");
        dialogScan.setCancelable(true);
        dialogScan.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                if(i == KeyEvent.KEYCODE_BACK){
                    Log.i(TAG,"Back down");
                    dialogScan.dismiss();
                    mBluetoothAdapter.cancelDiscovery();
                    Log.i(TAG,"Cancel Discovery");
                }
                return false;
            }
        });
        dialogScan.show();
    }


    @Override
    public void onBackPressed() {

        new CommonDialog(this, R.style.dialog, "可以点击Home键后台运行！", new CommonDialog.OnCloseListener() {
            @Override
            public void onClick(Dialog dialog, boolean confirm) {
                if(confirm){
                    //Toast.makeText(this,"点击确定", Toast.LENGTH_SHORT).show();
                    handler.removeCallbacks(runnable);
                    stopService(new Intent(BluetoothActivity.this, FloatService.class));
                    BluetoothActivity.this.finish();
                    dialog.dismiss();

                }
            }}).setTitle("确认退出？").show();

    }


    private IntentFilter getIntentFilter(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        return intentFilter;
    }


    @Override
    protected void onDestroy() {
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }
        //取消广播注册
        unregisterReceiver(receiver);
        stop();
        mConnectFinishFlag = false;
        Log.d("BlueActivity","Destroy");
        super.onDestroy();
    }

    private void connect(BluetoothDevice device){
        setState(STATE_CONNECTING);
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }
        //获取MAC地址，用于连接
        String address = list.get(i).getAddress();
        Log.i(TAG,address);
        BluetoothDevice btDev = mBluetoothAdapter.getRemoteDevice(address);
        connect(btDev);

        dialogConnect= new ProgressDialog(context);
        dialogConnect.setMessage("正在连接...");
        dialogConnect.setCancelable(true);
        dialogConnect.show();
    }


    //用于蓝牙连接的线程
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;


        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                //尝试建立安全的连接
                tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.i(TAG,"获取 BluetoothSocket失败");
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            if(mBluetoothAdapter.isDiscovering()){
                mBluetoothAdapter.cancelDiscovery();
            }
            try {
                mmSocket.connect();
                mConnectFinishFlag = true;
                dialogConnect.dismiss();
                mConnectSuccessFlag = true;
                //Looper.prepare();
               // Toast.makeText(BluetoothActivity.this,"连接成功",Toast.LENGTH_SHORT).show();
                //Looper.loop();
            } catch (IOException e) {
                Log.i(TAG,"socket连接失败");
                setState(STATE_LISTEN);
                Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.TOAST,"Socket连接失败");
                msg.setData(bundle);
                mHandler.sendMessage(msg);
                return;
            }

            synchronized (BluetoothActivity.this){
                mConnectThread = null;
            }
            //启动用于传输数据的线程connectedThread
            connected(mmSocket);
        }

        public void cancel(){
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //连接完成后启动ConnectedThread
    public synchronized void connected(BluetoothSocket socket){

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }


        setState(STATE_CONNECTED);
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

    }

    //蓝牙连接完成后进行输入输出流的绑定
    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            while(mState == STATE_CONNECTED){
                try {
                    // Read from the InputStream
                    Scanner in = new Scanner(mmInStream,"UTF-8");
                    String str = in.nextLine();
                    Log.i(TAG,"read: "+str);
                    //利用handle传递数据
                    Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
                    Bundle bundle = new Bundle();
                    //bundle.putString(Constants.TOAST,str);
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);
                } catch (Exception e) {
                    Log.e(TAG, "disconnected", e);
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    //用于接收连接请求
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("name",
                        MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "BEGIN mAcceptThread" + this);

            BluetoothSocket socket = null;

            // 在没有连接上的时候accept
            while (mState!=3) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                if (socket != null) {
                    synchronized (BluetoothActivity.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // 准备通信
                                connected(socket);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread");

        }

        public void cancel() {
            Log.d(TAG, "Socket cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket close() of server failed", e);
            }
        }
    }

    //发送字符串
    private void write(byte[] out){
        ConnectedThread r = null;
        try{
            r = mConnectedThread;
            r.write(out);
        }catch (NullPointerException e){
            Toast.makeText(context,"无法发送",Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

    }

    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        setState(STATE_NONE);
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    public synchronized void start() {
        Log.d(TAG, "start");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if(mAcceptThread != null){
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        setState(STATE_LISTEN);

        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }

    }

}
