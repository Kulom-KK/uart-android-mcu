package com.example.project_10;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

// 引入 mike wakerly <opensource@hoho.com> 编写的USB串口驱动库
import com.example.project_10.driver.* ;
import com.example.project_10.util.SerialInputOutputManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**  主活动代码重写了USB外设的打开，关闭，写入，读出方法
 *   本类需要实现按钮动作监听类: View.OnClickListener
 *   和其它几个监听类： View.OnFocusChangeListener，AdapterView.OnItemSelectedListener
 *
 *   本类需要引入： mike wakerly <opensource@hoho.com> 编写的开源USB串口驱动库
 *
 *
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        View.OnFocusChangeListener,
        AdapterView.OnItemSelectedListener {


    private EditText editRead = null;
    private EditText editSend = null;
    private TextView tvRead = null;
    private TextView tvAuthor = null;

    private Button buttSend = null;

    private Button buttOpen = null;
    private Button buttClose = null;

    //    private Button buttLedxOn = null;
//    private Button buttLedxOff = null;
//    private Button buttTempOn = null;
    private Button buttTempOff = null;

    private Spinner spChip = null;
    private Spinner spBaud = null;




    private enum UsbPermission { Unknown, Requested, Granted, Denied };

    private UsbManager usbManager = null;

    //针对某一个特定的USB串口A要用到的几个对象组合
    private UsbSerialDriver driverA = null;
    private UsbDevice  deviceA = null;
    private UsbDeviceConnection connectionA = null;
    private UsbSerialPort portA = null;
    private SerialInputOutputManager usbIoManagerA= null;
    private UsbPermission usbPermissionA = UsbPermission.Unknown;
    private int nBaudA = 115200;

    //若本设备插有多个USB串口设备且要同时打开使用，则可能要添加多组这样的变量组合来操作,例如：
    //针对USB串口B(同时还需要添加：openUsbSerialB()和closeUsbSerialB()两个方法)
    //private UsbSerialDriver driverB = null;
    //private UsbDevice  deviceB = null;
    //private UsbDeviceConnection connectionB = null;
    //private UsbSerialPort portB = null;
    //private SerialInputOutputManager usbIoManagerB= null;
    //private UsbPermission usbPermissionB = UsbPermission.Unknown;
    //private int nBaudB = 115200;

    private static final String INTENT_ACTION_GRANT_USB =  "MY_GRANT_USB";

    //接收USB设备插入后所发送消息的广播接收器
    private BroadcastReceiver broadcastReceiver;
    private PendingIntent penIntent = null;

    //检测所有USB串口设备的探头类
    private UsbSerialProber prober = null;


    private List<String> chipList = new ArrayList<>();
    private List<String> baudList = new ArrayList<>();

    private ArrayAdapter<String> adapterChip ;
    private ArrayAdapter<String> adapterBaud ;


    //标题栏
    private ActionBar actionBar = null;

    //行缓冲区,每一行最多1024字节,超过将被忽略
    private byte[] lineBuff ;

    //此行的真实长度
    private int nLineActLen =0 ;



    //[收到数据]editRead这个文本框的光标位置
    //private int nCursorPos =0 ;


    //[收到数据]editRead这个文本框的当前内容行数
    private int nContentRows = 0;

    //[收到数据]editRead这个文本框能显示的行数
    private int nPageRows =1 ;

    //[收到数据]editRead这个文本框的页面计数器
    private int nPage =1 ;


    @Override
    protected void onStart() {
        super.onStart();

    }


    @Override
    protected void onResume() {
        super.onResume();

        //开始时的输入焦点
        spChip.setFocusable(true);
        spChip.setFocusableInTouchMode(true);

    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

        //反注册广播接收器
        unregisterReceiver(broadcastReceiver);

        //需关闭端口并且退出读线程
        closeUsbSerialA();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        editRead = (EditText) findViewById(R.id.editRead);
        editSend = (EditText) findViewById(R.id.editSend);

        tvRead   =   (TextView) findViewById(R.id.tvRead);
//        tvAuthor =   (TextView) findViewById(R.id.tvAuthor);

        buttSend = (Button) findViewById(R.id.buttSend);
        buttOpen = (Button) findViewById(R.id.buttOpen);
        buttClose = (Button) findViewById(R.id.buttClose);


//        buttLedxOn = (Button) findViewById(R.id.buttLedxOn);
//        buttLedxOff = (Button) findViewById(R.id.buttLedxOff);
//        buttTempOn = (Button) findViewById(R.id.buttTempOn);
        buttTempOff =(Button) findViewById(R.id.buttTempOff);

        spChip = (Spinner) findViewById(R.id.spChip);
        spBaud = (Spinner) findViewById(R.id.spBaud);


        //注册按键动作监听器
        //入参为: View.OnClickListener 的接口实例,即 this
        buttSend.setOnClickListener(this);
        buttOpen.setOnClickListener(this);
        buttClose.setOnClickListener(this);
//        buttLedxOn.setOnClickListener(this);
//        buttLedxOff.setOnClickListener(this);
//        buttTempOn.setOnClickListener(this);
        buttTempOff.setOnClickListener(this);

        //取得或失去焦点时的监听
        editSend.setOnFocusChangeListener(this);
        spChip.setOnFocusChangeListener(this);
        spBaud.setOnFocusChangeListener(this);
        buttTempOff.setOnFocusChangeListener(this);

        //不能弹出输入键盘
        editRead.setKeyListener(null);
        //不能取得输入焦点
        editRead.setFocusable(false);
        editRead.setFocusableInTouchMode(false);
        buttClose.setEnabled(false);

        //修改标题栏文字内容
        actionBar=getSupportActionBar();
        if(actionBar != null){
            actionBar.setTitle("USB串口调试器");
        }

        //芯片选择
        //创建一个数组适配器
        adapterChip = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, chipList);
        //设置下拉列表框的下拉选项样式
        adapterChip.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spChip = (Spinner)findViewById(R.id.spChip);
        spChip.setAdapter(adapterChip);


        //行缓冲区,每一行最多1024字节,超过将被忽略
        lineBuff = new byte[1024];
        Arrays.fill(lineBuff, (byte) 0);
        //此行的真实长度
        nLineActLen =0 ;



        //只装入几个常用的波特率
        baudList.clear();
        baudList.add("115200");
        baudList.add("38400");
        baudList.add("19200");
        baudList.add("9600");
        //创建一个数组适配器
        adapterBaud = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, baudList);
        //设置下拉列表框的下拉选项样式
        adapterBaud.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBaud = (Spinner)findViewById(R.id.spBaud);
        spBaud.setAdapter(adapterBaud);


        //注册监听器
        spChip.setOnItemSelectedListener(this);

        //注册监听器
        spBaud.setOnItemSelectedListener(this);

        //更新芯片类列表
        updateChipList();

        //定义一个广播接收器, 当它收到特定的广播后会重新设置授权标记 usbPermission 的值
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(INTENT_ACTION_GRANT_USB)) {

                    //usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    //? UsbPermission.Granted : UsbPermission.Denied;

                    //需要判断用户是按下了: [确定] 或 [取消] 按键
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        //标记授权成功
                        usbPermissionA = UsbPermission.Granted;

                        Log.d("USB PermissionA", "收到广播: 已在授权界面取得USB运行期授权");

                    } else {
                        //标记授权未成功
                        usbPermissionA = UsbPermission.Denied;

                        Log.d("USB PermissionA", "收到广播: 未能在授权界面取得USB运行期授权");

                    }


                }  else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                    //此广播能正常收到
                    //获取此USB设备信息。
                    UsbDevice ud =  intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    int nDID = ud.getDeviceId() ;
                    int nVID = ud.getVendorId() ;
                    int nPID = ud.getProductId() ;

                    Log.d("USB Search", "收到广播: 刚插入USB设备->"+getChipType(nVID, nPID)+
                            " "+nDID+
                            " VID="+nVID +
                            " PID="+nPID);

                    runOnUiThread(() -> {
                        //加入列表框
                        chipList.add(getChipType(nVID, nPID) + " " +
                                nDID +
                                " VID" + nVID + "/PID" + nPID);

                        spChip.setSelection(chipList.size()-1);

                        //更新界面显示
                        adapterChip.notifyDataSetChanged();

                    });



                }  else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    //此广播能正常收到

                    //先执行关闭(因为设备突然拔掉,通讯很可能异常)
                    closeUsbSerialA() ;
                    editRead.append("串口设备异外关闭\n");
                    //按钮使能
                    buttOpen.setEnabled(true);
                    spChip.setEnabled(true);
                    spBaud.setEnabled(true);
                    buttClose.setEnabled(false);

                    UsbDevice ud =  intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    int nDID = ud.getDeviceId() ;
                    int nVID = ud.getVendorId() ;
                    int nPID = ud.getProductId() ;
                    Log.d("USB PermissionA", "收到广播: 有USB设备拔掉->"+getChipType(nVID, nPID)+
                            " "+nDID+
                            " VID="+nVID +
                            " PID="+nPID);

                    runOnUiThread(() -> {
                        //移出列表框
                        String strDID =" "+nDID + " ";
                        String strRow ="";
                        for (int i=0; i<chipList.size(); i++){
                            strRow = chipList.get(i) ;
                            if (strRow.indexOf(strDID)>0) {
                                chipList.remove(i);
                                break;
                            }
                        }

                        //没有任何芯片，加入一个空行
                        if (chipList.size() == 0) chipList.add(" ") ;

                        spChip.setSelection(0);

                        //更新界面显示
                        adapterChip.notifyDataSetChanged();

                    });



                }


            }
        };

        //运行期授权的意图
        //获取一个能处理自定义广播发送的意图penIntent, 后面将通过penIntent发送一个自定义广播(类似调用sendBroadcast()方法) INTENT_ACTION_GRANT_USB 给广播接收器broadcastReceiver
        penIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), PendingIntent.FLAG_IMMUTABLE);

        //意图过滤器filter
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_ACTION_GRANT_USB);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        //注册此广播接收器,此广播接收器将只处理上面的filter定义的广播消息
        registerReceiver(broadcastReceiver, filter);


    }


    //如果需要，可在这里处理机顶盒或电视设备的遥控器按键
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        switch (keyCode) {

            case KeyEvent.KEYCODE_ENTER:     //确定键enter
            case KeyEvent.KEYCODE_DPAD_CENTER:
                //Log.d("Key","enter");

                break;

            case KeyEvent.KEYCODE_BACK:    //返回键
                //Log.d("Key","back");

                //改变消息流向
                //由于返回键会退出，如果不需要父级处理该退出则直接返回真
                //return true;
                break;

            case KeyEvent.KEYCODE_SETTINGS: //设置键
                //Log.d("Key","setting");

                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:   //向下键

                /*   有时候会触发两次，所以要判断一下按下： KeyEvent.ACTION_DOWN 时才触发
                 *   按键松开:KeyEvent.ACTION_UP 不需处理
                 */
                if (event.getAction() == KeyEvent.ACTION_DOWN){

                    //Log.d("Key","down");
                }

                break;

            case KeyEvent.KEYCODE_DPAD_UP:   //向上键
                //Log.d("Key","up");

                break;

            case     KeyEvent.KEYCODE_0:   //数字键0
                //Log.d("Key","0");

                break;

            case KeyEvent.KEYCODE_DPAD_LEFT: //向左键

                //Log.d("Key","left");

                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:  //向右键
                //Log.d("Key","right");
                break;


            default:
                //Log.d("Key","此按键值为: " +keyCode);
                break;
        }

        return super.onKeyDown(keyCode, event);

    }



    //更新芯片类列表
    private void updateChipList(){

        int nDID =0 ;
        int nVID =0 ;
        int nPID =0 ;


        //取回USB设备管理器
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        //芯片列表先清空
        chipList.clear();
        for(UsbDevice v : usbManager.getDeviceList().values()) {

            nDID = v.getDeviceId() ;
            nVID = v.getVendorId() ;
            nPID = v.getProductId() ;

            Log.d("USB Search", "USB设备名称: "+v.getDeviceName()+
                    "    DeviceID="+nDID+
                    "    VID="+nVID +
                    "    PID="+nPID
            );

            //加入列表框
            chipList.add(getChipType(nVID, nPID) +" "+
                    nDID +
                    " VID"+nVID +"/PID"+nPID);


        }

        //没有任何芯片，加入一个空行
        if (chipList.size() == 0) chipList.add(" ") ;

        //默认显示第一行
        spChip.setEnabled(true);
        spBaud.setEnabled(true);
        spChip.setSelection(0);
        spBaud.setSelection(0);

    }




    //从一个设备的VID和PID中找到其对应的芯片类型
    // 暂列出三种国内常见的USB转串口芯片，可参考 device_filter.xml 文件添加
    private String getChipType(int nVID, int nPID){
        String strChip = "Unknown" ;

        switch(nVID){

            case 4292 :
                if ( (nPID == 60000) || (nPID == 60016) || (nPID == 60017) )
                    strChip = "CP210x"   ;
                break ;

            case 1659 :
                if  (nPID == 8963)
                    strChip = "PL2303"   ;
                break ;

            case 6790 :
                if ( (nPID == 21795) || (nPID == 29987)  )
                    strChip = "CH34x"   ;
                break ;

        }

        return  strChip;

    }



    //从一个被选中的列表框字符串中找到一个可用的USB串口设备
    //入参格式类似于：CP21xx   1002  VID4292/PID60000(空格分隔的三个字符串)
    private UsbDevice getDeviceFromDetail(String strDetail){

        UsbDevice ud = null ;

        //移除串中的空值，[ ]内是一个空格，而+表示允许多个空格。
        String[] ss= strDetail.split("[ ]+") ;

        if  (ss.length > 2){

            //第二个字符串是设备ID
            int nDeviceId = Integer.parseInt(ss[1]);

            if (usbManager == null)
                usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            for(UsbDevice v : usbManager.getDeviceList().values()) {

                if (v.getDeviceId() == nDeviceId){
                    ud = v;
                    break ;
                }
            }

        }

        return ud ;

    }



    //关闭USB串行口A
    private void closeUsbSerialA(){

        //需要退出读线程
        if  (usbIoManagerA !=null){
            usbIoManagerA.stop();
        }

        //需关闭端口
        if  (portA.isOpen()){
            try {
                portA.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        deviceA = null ;

    }



    //打开USB串行口A，成功返回真 （可能需要运行期授权）
    private void openUsbSerialA(){


        //直接创建并启动一个匿名线程接口类的实例
        new Thread(new Runnable() {

            @Override
            public void run() {


                try {

                    //执行打开USB串行口A的代码
                    if  ( execUsbSerialA() ) {

                        //UI界面的按钮使能
                        runOnUiThread(() -> {
                            editRead.append("USB串口初始化和打开成功!\n");
                            if ((portA != null) && portA.isOpen()) {
                                buttOpen.setEnabled(false);
                                spChip.setEnabled(false);
                                spBaud.setEnabled(false);
                                buttClose.setEnabled(true);
                            }
                        });

                    } else {
                        runOnUiThread(() -> {
                            editRead.append("USB串口初始化和打开失败!\n");
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }


            }   //public void run() ... end

        }).start();



    }





    //打开USB串行口A，成功返回真 （可能需要运行期授权）
    //本代码需放在一个线程内执行
    private boolean execUsbSerialA()
    {

        boolean bSuccess = false ;

        String strTemp = (String) spChip.getSelectedItem();
        strTemp=strTemp.trim();

        //必须在设备列表框中选中可用设备
        if  ( (strTemp.indexOf("Unknown")>=0 ) || (strTemp == null) ) {

            runOnUiThread(() -> {
                editRead.append("必须在芯片类选中一台可用设备!\n");
            });
            return false ;
        }

        deviceA =getDeviceFromDetail(strTemp);

        if  ( deviceA == null ){
            runOnUiThread(() -> {
                editRead.append("此芯片类设备尚未有相应的驱动!\n");
            });
            return false ;
        }

        // Find all available drivers from attached devices.
        //usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        //if (availableDrivers.isEmpty()) {

        if (prober == null) {

            //全部受支持的USB串口设备检测表customTable
            ProbeTable customTable = new ProbeTable();

            //把每一个设备的PID/VID加入到设备检测表ProbeTable
            //例如 VID=0x10C4=4292 , PID=0xEA60=60000  , 为CP21XX 芯片
            //加入常见的几种芯片驱动

            customTable.addProduct(4292, 60000, Cp21xxSerialDriver.class);
            customTable.addProduct(4292, 60016, Cp21xxSerialDriver.class);
            customTable.addProduct(4292, 60017, Cp21xxSerialDriver.class);
            customTable.addProduct(1659, 8963, ProlificSerialDriver.class);
            customTable.addProduct(6790, 21795, Ch34xSerialDriver.class);
            customTable.addProduct(6790, 29987, Ch34xSerialDriver.class);


            //从一个设备的VID和PID中找到对应的驱动类
            // final Class<? extends UsbSerialDriver> driverClass = customTable.findDriver(vendorId, productId);


            prober = new UsbSerialProber(customTable);
            //availableDrivers = prober.findAllDrivers(usbManager);

        }


        //从一个USB设备找到其对应的驱动还可以这样做
        driverA =prober.probeDevice(deviceA);

        // Open a connection to the first available driver.
        //driverA = availableDrivers.get(0);
        //deviceA= driverA.getDevice() ;


        if(usbManager.hasPermission(deviceA)) {
            Log.d("USB PermissionA", "已取得此USB设备的授权:  " + deviceA);
            //打开USB串行口A设备及对应端口
            execThisDevicePortA() ;
            bSuccess =true ;

        } else {
            Log.d("USB PermissionA", "未取得此USB设备的授权:  " + deviceA);

            //需取得USB管理权限 ： 运行期授权
            //这里将通过penIntent发送一个广播INTENT_ACTION_GRANT_USB 给广播接收器broadcastReceiver
            usbPermissionA = UsbPermission.Requested ;
            usbManager.requestPermission(deviceA, penIntent);


            //这里应该等待授权界面最多30秒后才关闭......
            int timeout = 0 ;
            while (timeout>30){

                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                timeout += 1 ;

                if  ((usbPermissionA == UsbPermission.Granted) || (usbPermissionA == UsbPermission.Denied))
                    break ;
            }


            //授权OK了，才能打开此设备及对应端口
            if  (usbPermissionA == UsbPermission.Granted){

                Log.d("USB PermissionA", "已取得此USB设备的运行期授权:  " + deviceA);

                //打开USB串行口A设备及对应端口
                execThisDevicePortA() ;
                bSuccess =true ;

            } else {

                Log.d("USB PermissionA", "在界面操作中仍未取得此USB设备的运行期授权:  " + deviceA);
                bSuccess =false ;

            }


        }

        return  bSuccess ;

    }

    //打开USB串行口A设备及对应端口
    private void execThisDevicePortA(){

        //先打开设备连接
        connectionA = usbManager.openDevice(deviceA);

        if (connectionA != null) {

            Log.d("USB OpenDeviceA", "打开此USB设备OK:  " + deviceA);

            // Most devices have just one port (port 0)
            portA = driverA.getPorts().get(0);

            //打开设备的端口并置定波特率
            try {
                portA.open(connectionA);
                portA.setParameters(nBaudA, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                Log.d("USB OpenPortA", "打开此USB设备的端口OK:  " + deviceA);

                //回调方式读取 ： 实际使用时应该用此方法

                //第二个入参为:  SerialInputOutputManager.Listener 的接口实例,即 portAListener
                usbIoManagerA = new SerialInputOutputManager(portA, portAListener);
                usbIoManagerA.stop();
                //读取超时不要设置太大了，否则读线程被阻塞的时间很长，读取响应很慢
                //但也不要设为0，因为0表示无限等候读取
                //在115200波特率下，每秒大约能读取13KB字节，而一般发送端的数据包都不会超过1KB大小
                //因此把等候时间设为100ms是可行的
                usbIoManagerA.setReadTimeout(100);
                Executors.newSingleThreadExecutor().submit(usbIoManagerA);

                Log.d("USB RunReadThreadA", "运行此USB设备的读取线程OK: " + deviceA);

            } catch (IOException e) {
                e.printStackTrace();
                Log.d("USB OpenPortA", "打开此USB设备的端口Error:  " + deviceA);

            }

        } else {

            Log.d("USB OpenDeviceA", "打开此USB设备Error:  " + deviceA);

        }

    }


    //在指定的usb串口上发送字符串
    private void writeString(UsbSerialPort port , String strOut){

        //本APP的接收和发送都采用GBK编码,与通讯另一端的单片机或其它windows串口控制台软件一致.
        //这样确保了汉字的发送也是正常的
        byte[] bytesOut= new byte[0];
        try {
            bytesOut = strOut.getBytes("GBK");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        writeBytes(port,bytesOut) ;
    }


    //在指定的usb串口上发送字节流
    private void writeBytes(UsbSerialPort port ,byte[] bytesOut){

        if (bytesOut.length > 0){

            //直接创建并启动一个匿名线程接口类的实例
            new Thread(new Runnable() {

                @Override
                public void run() {

                    try {

                        if( (port != null) && (port.isOpen()) ) {
                            int cnt=port.write(bytesOut, 2000);
                            String strMsg = "USB串口"+port.getPortNumber()+"已发送总字节数:  "+cnt +"\n";
                            Log.d("USB Write", strMsg );

                            //UI界面的更新
                            runOnUiThread(() -> {
                                editRead.append(strMsg);
                            });

                        } else {
                            //UI界面提示
                            runOnUiThread(() -> {
                                editRead.append("USB串口" + port.getPortNumber() + "对象为空或端口尚未打开\n");
                            });
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                }   //public void run() ... end

            }).start();

        }



    }


    //发送R.id.editSend文本编辑框的字符串
    private void sendEditTextString()
    {

        //自动在最后面添加换行符和回车符 : 0x0A=换行\n  ， 0x0D=回车\r
        if ((portA !=null ) && portA.isOpen()) {

            String strTemp = editSend.getText().toString();
            //先替换字符串中的转义符: \n和\r
            strTemp=strTemp.replace("\\r","\r") ;
            strTemp=strTemp.replace("\\n","\n") ;
            //再在最后添加换行符和回车符
            strTemp += "\n\r";
            writeString(portA, strTemp);
            editRead.append("已发送: "+editSend.getText().toString()+"\n");
        }
        else
        {
            editRead.setText("请先打开串口设备\n");
        }

    }


    /**
     * 返回EditText控件在某一行之前的字符总数
     * @param editText 需要统计字符个数的EditText
     * @param  nLine 指定的行数
     * @return ：int  此行之前的总字符个数
     */
    private int getCharsCount(EditText editText,int nLine) {
        int cnt =0;
        String s=editText.getText().toString();
        String[] ss =s.split("\n");
        if (ss.length <= nLine){
            cnt =s.length() ;
        } else {
            for (int i=0; i<nLine;i++){
                cnt +=ss[i].length() ;
            }
        }

        return cnt;

    }



    /**
     *  判定EditText控件垂直方向是否可以滚动
     * @param  editText=需要判断的EditText
     * @return true=滚动  false=不可以滚动
     */
    private boolean isVerticalScroll(EditText editText) {

        //控件的能滚动到的最顶点,在此顶点之外不能绘制任何像素
        //垂直滚动的距离 : 垂直能滚动时该值大于0,不滚动时为0
        int scrollY = editText.getScrollY();

        //控件内容的总高度
        int scrollContent = editText.getLayout().getHeight();

        //实际控件显示的可用高度
        int scrollAvailable = editText.getHeight() - editText.getCompoundPaddingTop() -editText.getCompoundPaddingBottom();

        //控件内容总高度与实际控件显示的可用高度的差值,如果该差值大于1个像点时也表示需要垂直滚动才能看到被遮挡的内容
        int scrollDifference = scrollContent - scrollAvailable;

        nContentRows = editText.getLineCount();
        int  nMaxLines= editText.getMaxLines();
        int  nLineHeight=editText.getLineHeight();

        //这个文本框每页能显示的行数
        if (nLineHeight>0)   nPageRows =  (scrollAvailable / nLineHeight)-1 ;
        if  (nPageRows<=0)   nPageRows =1;

        Log.d("ScrollY: ", "此文本框当前内容总行数为: " +nContentRows+"   每页行数为: " +nPageRows);
        Log.d("ScrollY: ", "允许显示最大行数为: " + nMaxLines + "    行高为: "+nLineHeight);
        Log.d("ScrollY: ", "内容Y值为: " +scrollContent+ "      控件Y值为: " + scrollAvailable);
        Log.d("ScrollY: ", "滚动Y的最顶点为: " +scrollY+ "      内容Y超出控件为: " + scrollDifference);

        if(scrollDifference <=1 ) {
            return false;
        }

        return (scrollY > 0) || ( 1 < scrollDifference) ;
    }


    // ***************************** 下面实现本类的几个监听器接口 ************************************

    //实现本类的View.OnClickListener接口要求实现的唯一方法: onClick(View v)
    public void onClick(View v) {



        switch (v.getId()) {

            case R.id.buttSend:

                //在USB串行口A上发送R.id.editSend编辑框的数据
                sendEditTextString();
                break;


            case R.id.buttOpen:

                //打开USB串行口A（可能需要运行期授权）

                openUsbSerialA() ;
                editRead.setText("");
                break;


            case R.id.buttClose:

                //关闭USB串行口A
                closeUsbSerialA() ;
                editRead.append("串口设备已关闭\n");
                //按钮使能
                buttOpen.setEnabled(true);
                spChip.setEnabled(true);
                spBaud.setEnabled(true);
                buttClose.setEnabled(false);
                break;

//            case R.id.buttLedxOn:
//
//                //点亮蓝色LED的指令串
//                editSend.setText("SetLedx:1");
//
//                //在USB串行口A上发送R.id.editSend编辑框的数据
//                sendEditTextString();
//
//                break;
//
//            case R.id.buttLedxOff:
//
//                //熄灭蓝色LED的指令串
//                editSend.setText("SetLedx:0");
//
//                //在USB串行口A上发送R.id.editSend编辑框的数据
//                sendEditTextString();
//
//                break;
//
//            case R.id.buttTempOn:
//
//                //连续取回环境温度的指令串
//                //测试插入换行回车符 : 0x0A=换行\n ， 0x0D=回车\r
//                //editSend.setText("GetTemp:1\\n\\r");
//
//                editSend.setText("GetTemp:1");
//                //在USB串行口A上发送R.id.editSend编辑框的数据
//                sendEditTextString();
//
//                break;

            case R.id.buttTempOff:

                String strOld =editSend.getText().toString().trim();

                //以下实现按钮复用: 反复地按这个停止键可以让[收到数据]这个文本框上下滚动,以便用户查看其全部的内容
                //需要用到如下变量:
                //[收到数据]editRead这个文本框的当前内容行数 : nContentRows
                //[收到数据]editRead这个文本框能显示的行数 : nPageRows
                //[收到数据]editRead这个文本框的页面计数器: nPage
                if ( strOld.equals("GetTemp:0")  && isVerticalScroll(editRead) ) {
                    //Log.d("VerticalScroll", "editRead的垂直滚动条出现了!" );
                    //暂时复用为翻页键,失去焦点后会还原为停止键
                    //总页数
                    if (nPageRows <=0) nPageRows=1;
                    int nPageAll = nContentRows / nPageRows ;
                    if ((nContentRows % nPageRows) != 0) nPageAll +=1 ;

                    buttTempOff.setText("翻页");
                    //tvRead.setText(1+"/"+nPageAll+"页");

                    int nChars = 0 ;
                    int nLine =  1 ;
                    if (nPage > 1) {
                        nLine = nPage * nPageRows ;
                        nChars = getCharsCount(editRead, nLine);
                    }
                    tvRead.setText(nPage+"/"+nPageAll+"页");
                    editRead.setSelection(nChars);
                    if (nPage < nPageAll) {
                        //页面计数增加
                        nPage += 1;
                    } else {
                        nPage = 1;
                    }

                } else {
                    nPage =1 ;
                    buttTempOff.setText("复位");
                    tvRead.setText("收到数据:");

                    //停止取回环境温度的指令串
//                    editSend.setText("GetTemp:0");
                    //在USB串行口A上发送R.id.editSend编辑框的数据
                    sendEditTextString();
                }

                break;


            default:

        }

    }


    //USB串口A上的读取线程要回调的接口
    SerialInputOutputManager.Listener portAListener = new SerialInputOutputManager.Listener() {


        //本类的SerialInputOutputManager.Listener 接口中的两个方法
        @Override
        public void onNewData(byte[] data) {

            //有换行符的完整的一行字符串
            String strALL = "";
            //当前读到的数据块的字节数
            int cnt=data.length ;

            try {

                //在这里收到单片机发来的汉字字符串时有时会出现乱码,本博主实际按以下思路得到完美解决:
                //为了防止字符串有汉字时显示乱码(因为串口有时读到字节刚好是把某个GBK汉字的两个编码分成两个数据块读入了),
                // 因此我们需要按换行符来处理每一行文字
                //即: 收到的字节流先添加到缓冲区里,然后检查此缓冲区里是否出现了换行符,如果是则把此行读出来,
                //然后将此行按GBK处理(附注: STM32单片机发送过来的是GBK编码)
                //处理完后需同时清空此行缓冲区lineBuff

                //当前字节先无条件放入lineBuff,逐个检查此字节是否为换行符,,
                // 若是则处理行缓冲区lineBuff的UI更新后才清空lineBuff并且继续放入余下的字节
                for (int i=0; i < cnt; i++){

                    //数组越界检查
                    //只要行缓冲区未满就无条件将当前字节数放入行缓冲区
                    if (nLineActLen < (lineBuff.length-1)) {
                        lineBuff[nLineActLen] = data[i];
                        nLineActLen += 1;
                    }

                    if (data[i] == (byte)('\n') ){
                        //只要换行符出现了,则立即处理行缓冲区，
                        //发送端过来的回车符不用管（回车符只为以后使用本APP发送AT指令时的兼容）
                        //对于单片机,发送过来的多是GB2312或GBK编码
                        //如果发送过来的是UTF-8编码,则应该用: strALL += new String(data, 0, data.length, "UTF-8");
                        strALL += new String(lineBuff, 0, nLineActLen, "GBK");
                        //处理完了行缓冲区后需要清空
                        Arrays.fill(lineBuff, (byte) 0);
                        nLineActLen = 0;
                    }

                }

            }
            catch (Exception ee)
            {

                strALL = "未能识别的字符串编码: " +  ee.getMessage()+"\n";

            }
            finally
            {

                if   (strALL != null) {

                    //UI界面的更新
                    String finalStrALL = strALL;
                    runOnUiThread(() -> {
                        editRead.append(finalStrALL);
                    });

                }

            }

        }

        @Override
        public void onRunError(Exception e) {
            //接收线程出错了
            e.printStackTrace();

        }

    };


    //本类的OnItemSelectedListener接口要求实现的两个方法

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        String strTemp;
        String strCmd;

        switch (parent.getId()) {

            //芯片选择
            case R.id.spChip :

                strTemp = (String) spChip.getItemAtPosition(position);
                strTemp =strTemp.trim();

                if ( (strTemp.indexOf("Unknown")>=0 )|| (strTemp == null) ){
                    editRead.append("驱动不存在将无法打开此设备端口\n");
                    deviceA =null ;
                }  else {
                    deviceA = getDeviceFromDetail(strTemp);
                    if (deviceA !=null)
                        editRead.append("已找到此USB串口设备\n");
                }

                break;

            //波特率选择
            case R.id.spBaud :

                strTemp = (String) spBaud.getItemAtPosition(position);
                strTemp =strTemp.trim();
                nBaudA = Integer.parseInt(strTemp);
                break;


        }


        //更新界面显示
        adapterChip.notifyDataSetChanged();
        adapterBaud.notifyDataSetChanged();


    }
    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }


    //某个控件的焦点变化时的监听器(获得焦点时高亮显示)
    @Override
    public void onFocusChange(View v, boolean hasFocus) {


        switch (v.getId()) {

            case R.id.editSend:
                if (hasFocus) {
                    // 获得焦点
                    //Log.d("Focus", "editSend取得了焦点" );
                    //光标移到文本末尾
                    int len= editSend.getText().length();
                    if (len>0)  editSend.setSelection(len);
                    editSend.setBackgroundColor(getResources().getColor(R.color.normalhighlight));
                } else {
                    // 失去焦点
                    //Log.d("Focus", "editSend失去了焦点" );
                    editSend.setBackgroundColor(getResources().getColor(R.color.normalbackground));
                }
                break ;

            case R.id.spChip:
                if (hasFocus) {
                    // 获得焦点
                    //Log.d("Focus", "spChip取得了焦点" );
                    spChip.setBackgroundColor(getResources().getColor(R.color.normalhighlight));
                } else {
                    // 失去焦点
                    //Log.d("Focus", "spChip失去了焦点" );
                    spChip.setBackgroundColor(getResources().getColor(R.color.normalbackground));
                }
                break ;

            case R.id.spBaud:
                if (hasFocus) {
                    // 获得焦点
                    //Log.d("Focus", "spBaud取得了焦点" );
                    spBaud.setBackgroundColor(getResources().getColor(R.color.normalhighlight));
                } else {
                    // 失去焦点
                    //Log.d("Focus", "spBaud失去了焦点" );
                    spBaud.setBackgroundColor(getResources().getColor(R.color.normalbackground));
                }
                break ;


            case R.id.buttTempOff:
                if (!hasFocus) {
                    // 失去焦点
                    //Log.d("Focus", "buttTempOff失去了焦点" );
                    buttTempOff.setText("停止");
                    tvRead.setText("收到数据:");
                }
                break ;
        }

    }

    // ***************************** 实现本类的几个监听器接口 end ************************************

}
