package com.houndlabs.retinascan

import android.content.*
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.nio.charset.Charset
import java.text.DateFormat
import java.util.*
import kotlin.math.roundToInt

enum class ConnectionState {
    UART_PROFILE_CONNECTED, UART_PROFILE_DISCONNECTED
}

data class DirectionPercentages(val x: Float, val y: Float)

fun Int.roundToByte() = this.toByte()
fun Float.roundToByte() = this.roundToInt().toByte()

class DeviceController() {
    private var state: ConnectionState = ConnectionState.UART_PROFILE_DISCONNECTED
    private val TAG = DeviceController::class.simpleName
    private var pos=0;
    private val bytes = byteArrayOf(0x0, 0x0, 0x0)

    private var velocityMode = 2
    private var byteType: Byte = 0x00
    private var sign: Byte = 0x00
    private val trainStep = 0

    private var mService: UartService? = null
    private var handler: Handler? = null

    fun setVelocityMode(vm: Int) {
        velocityMode = vm
    }
    fun setByteType(vm: Byte) {
        Log.i("BYTE_TYPE", "new byte type: $vm")
        byteType = vm


        //this is a hack sorry. couldn't totally follow what you were doing. but with this hack
        //everything works as expected- ex i can type 8,0,1 (i modded 0 to be 50 in mainactivity, it
        // can be hard to see a small number of steps) the motors move as expected
        bytes[pos]=vm;
        pos++
        if (pos==3){
            writeRx(bytes[0],bytes[1],bytes[2]);
            pos=0;
        }

        //this works, so must be some issue with assembling the bytes
        //writeRx(0x07, 0x50, 0x01);
        //writeRx(0x08, 0x50, 0x01);
    }

    private fun writeRx(setBytes: ByteArray.() -> Unit) = mService?.writeRXCharacteristic(ByteArray(3).apply(setBytes))
    private fun writeRx(one: Byte, two: Byte, three: Byte) = writeRx {
        this[0] = one
        this[1] = two
        this[2] = three
    }

    fun moveXY(direction: Direction) {
        if(direction === Direction.TOP) {
            sign = 0x01
            return
        }
        if(direction === Direction.DOWN) {
            sign = 0x00
            return
        }

        val secondByte = if(direction === Direction.RIGHT) {
            when(velocityMode) {
                1 -> 100.toByte()
                2 -> 10.toByte()
                3 -> 1.toByte()
                else -> 0x00
            }
        } else {
            0x00
        }
        writeRx(byteType, secondByte, sign)
        Log.d("moveXY", "byte: $byteType, value is: $secondByte, sign: $sign")
    }

    fun sign(steps: Byte) = (steps + 127).toByte()

    fun queryPosition() = writeRx(0x45,0,0)

    @ExperimentalStdlibApi
    fun randomSequence () {
        File(Environment.getExternalStorageDirectory().toString() + "/_scanned/").mkdirs()

        val file =
            File(Environment.getExternalStorageDirectory().toString() + "/_scanned/data.json")
        val path = generateMovements()
      //  val json = GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(path)
      //  file.writeText(json)

        val handler = Handler()
        var count = 0;
        processSeg(handler, path, count)
    }

    fun next(handler: Handler, path: List<StepData>, count : Int){
        handler.postDelayed( Runnable {
            val seg = path[count];
           // previewWriter.save(seg.img_num.toString())
            if (count < path.size-1){
                processSeg(handler, path, count + 1)
            }
        }, 1000);
    }
    fun moveX(v: Int){
        if (v > 0){
            val b2 = v
            var b3: Int = 1
            if (b2 >  255){
                b3 = b2 / 255
            }
            writeRx(0x05, b2.toByte(), b3.toByte())
        }else if (v<0){
            val b2 = v * -1;
            var b3: Int = 1
            if (b2 >  255){
                b3 = b2 / 255
            }
            writeRx(0x04, b2.toByte(), b3.toByte())
        }
    }

    fun moveY(v: Int) {
        if (v> 0){
            val b2 = v
            var b3: Int = 1
            if (b2 >  255){
                b3 = b2 / 255
            }
            writeRx(0x08, b2.toByte(), b3.toByte())  // z
        }else if (v<0){
            val b2 = v * -1;
            var b3: Int = 1
            if (b2 >  255){
                b3 = b2 / 255
            }
            writeRx(0x07, b2.toByte(), b3.toByte())  // z
        }
    }
    fun moveZ(v: Int) {
        if (v> 0){
            val b2 = v
            var b3: Int = 1
            if (b2 >  255){
                b3 = b2 / 255
            }
            writeRx(0x11, b2.toByte(), b3.toByte())  // z
        }else if (v<0){
            val b2 = v * -1;
            var b3: Int = 1
            if (b2 >  255){
                b3 = b2 / 255
            }
            writeRx(0x10, b2.toByte(), b3.toByte())  // z
        }
    }
    fun processSeg(handler: Handler,  path: List<StepData>, count : Int){
        handler.postDelayed( Runnable {
            val seg = path[count];
            Log.e("moving", "${count + 1} of ${path.size}  -> x: ${seg.steps[0]} y: ${seg.steps[1]} z: ${seg.steps[2]} ")

            // x movement
            if (seg.steps[0] > 0){
                val b2 = seg.steps[0]
                var b3: Int = 1
                if (b2 >  255){
                    b3 = b2 / 255
                }
                writeRx(0x05, b2.toByte(), b3.toByte())
            }else if (seg.steps[0]<0){
                val b2 = seg.steps[0] * -1;
                var b3: Int = 1
                if (b2 >  255){
                    b3 = b2 / 255
                }
                writeRx(0x04, b2.toByte(), b3.toByte())
            }

            // y movement
            if (seg.steps[1] > 0){
                val b2 = seg.steps[1]
                var b3: Int = 1
                if (b2 >  255){
                    b3 = b2 / 255
                }
                writeRx(0x08, b2.toByte(), b3.toByte())  // z
            }else if (seg.steps[1]<0){
                val b2 = seg.steps[1] * -1;
                var b3: Int = 1
                if (b2 >  255){
                    b3 = b2 / 255
                }
                writeRx(0x07, b2.toByte(), b3.toByte())  // z
            }

            // z movement
            writeRx(0x12, (seg.steps[0] + 127).toByte(), (seg.steps[1] + 127).toByte()) //x and y
            if (seg.steps[2] > 0){
                var b3: Int = 1
                if (seg.steps[2] >  255){
                    b3 = seg.steps[2] / 255
                }
                writeRx(0x10, seg.steps[2].toByte(), b3.toByte())  // z
            }

            next(handler, path, count)

        }, 200);
    }

    fun moveInRectangles() {
        // fixme: flipped axises
        for(i in 0..8) {
            writeRx(0x12, sign(-0x63), sign(0x00));
        }
        for(i in 0..8) {
            writeRx(0x12, sign(-0x50), sign(0x50));
        }
        for(i in 0..8) {
            writeRx(0x12, sign(-0x50), sign(-0x50));
        }
        for(i in 0..8) {
            writeRx(0x12, sign(0x50), sign(-0x50));
        }

        //writeRx(0x33,0,0)
//        for(i in 0..3) {
//            writeRx(0x07, 0x50, 0x01);
//            Log.d("DEVICE","first loop")
//        }
//        for(i in 0..3) {
//            writeRx(0x04, 0x50, 0x01);
//            Log.d("DEVICE","second loop")
//        }
//        for(i in 0..3) {
//            writeRx(0x08, 0x50, 0x01);
//            Log.d("DEVICE","third loop")
//        }
//        for(i in 0..3) {
//            writeRx(0x05, 0x50, 0x01);
//            Log.d("DEVICE","fourth loop")
//        }
        // writeRx(0x08, 0x50, 0x01);
    }

    fun sendUpdateMessage(action: String){
        val intent = Intent(action)
        LocalBroadcastManager.getInstance(mService!!).sendBroadcast(intent)
    }

    //UART service connected/disconnected
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, rawBinder: IBinder) {
            mService = (rawBinder as UartService.LocalBinder).getService()
            Log.d(TAG, "onServiceConnected mService= $mService")
            if (!mService!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
//                finish()
            }else{
              sendUpdateMessage(CameraActivity.DEVICE_CONTROLL_CONNECTED)
            }
        }

        override fun onServiceDisconnected(classname: ComponentName) {
            ////     mService.disconnect(mDevice);
            mService = null
        }
    }

    private val UARTStatusChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val mIntent = intent
            //*********************//
            if (action == UartService.ACTION_GATT_CONNECTED) {
//                runOnUiThread(Runnable {
//                })
                val currentDateTimeString =
                    DateFormat.getTimeInstance().format(Date())
                Log.d(TAG, "UART_CONNECT_MSG")
                //btnConnectDisconnect.setText("Disconnect");
                //edtMessage.setEnabled(true);
                //btnSend.setEnabled(true);
                //((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                //listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                //messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                state = ConnectionState.UART_PROFILE_CONNECTED

                Handler().postDelayed({
                     queryPosition()
                    //moveInRectangles()
                }, 1500L)
            }

            //*********************//
            if (action == UartService.ACTION_GATT_DISCONNECTED) {
//                runOnUiThread(Runnable {
//                })
                val currentDateTimeString =
                    DateFormat.getTimeInstance().format(Date())
                Log.d(TAG, "UART_DISCONNECT_MSG")
                //btnConnectDisconnect.setText("Connect");
                //edtMessage.setEnabled(false);
                //btnSend.setEnabled(false);
                //((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                //listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                state = ConnectionState.UART_PROFILE_DISCONNECTED
                mService?.close()
                //setUiState();
            }


            //*********************//
            if (action == UartService.ACTION_GATT_SERVICES_DISCOVERED) {
                mService?.enableTXNotification()
            }
            //*********************//
            if (action == UartService.ACTION_DATA_AVAILABLE) {
                val txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA)
                try {
                    val text = txValue?.let { String(it, Charset.forName("UTF-8")) }
                    val currentDateTimeString =
                        DateFormat.getTimeInstance().format(Date())
                    if (text != null) {
                        Log.e(TAG, text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                }
            }
            //*********************//
            if (action == UartService.DEVICE_DOES_NOT_SUPPORT_UART) {
                //showMessage("Device doesn't support UART. Disconnecting");
                mService?.disconnect()
            }
        }
    }

    fun serviceInit(context: Context) {
        val bindIntent = Intent(context, UartService::class.java)
        context.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(context).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter())
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE)
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART)
        return intentFilter
    }

    fun onDestroy(context: Context) {
        Log.d(TAG, "onDestroy()")
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(UARTStatusChangeReceiver)
        } catch (ignore: java.lang.Exception) {
            Log.e(TAG, ignore.toString())
        }
        context.unbindService(mServiceConnection)
        mService?.stopSelf()
        mService = null
    }

    fun connect(deviceAddress: String?) {
        Log.d(TAG, "... onActivityResultdevice.address==" + deviceAddress + "mserviceValue" + mService)
        mService?.connect(deviceAddress)

    }
}