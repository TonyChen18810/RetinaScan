package com.houndlabs.retinascan;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.text.method.ScrollingMovementMethod;
import android.view.Surface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import kotlin.collections.AbstractMutableMap;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {
    private Handler handler;
    private HandlerThread handlerThread;

    private DeviceController deviceController;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private View takePicture, previewContainer;
    private View left, right, up, down, zoomIn, zoomOut;
    private TextView status;
    private Spinner velocity;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLeScanner;
    private boolean mScanning = false;
    private Handler mHandler;
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final long SCAN_PERIOD = 10000;
    private HashSet<String> addresses = new HashSet<String>();

    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
    };

    public final static String DEVICE_CONTROLL_CONNECTED =
            "com.nordicsemi.device.controll.connected";
    private Dialog scanningMessageBox;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.viewFinder);
        previewContainer = findViewById(R.id.previewContainer);
        previewContainer = findViewById(R.id.previewContainer);
        takePicture = findViewById(R.id.button_take_picture);
        takePicture.setOnClickListener(this);

        left = findViewById(R.id.left);
        left.setOnClickListener(this);

        right = findViewById(R.id.right);
        right.setOnClickListener(this);

        up = findViewById(R.id.up);
        up.setOnClickListener(this);

        down = findViewById(R.id.down);
        down.setOnClickListener(this);

        zoomIn = findViewById(R.id.zoomIn);
        zoomIn.setOnClickListener(this);

        zoomOut = findViewById(R.id.zoomOut);
        zoomOut.setOnClickListener(this);

        status = findViewById(R.id.status);
        status.setMovementMethod(new ScrollingMovementMethod());

        String[] velocity_values = { "100", "300", "800", "1000", "2000"};
        velocity = findViewById(R.id.velocity);
        ArrayAdapter aa = new ArrayAdapter(this,android.R.layout.simple_spinner_item, velocity_values);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        velocity.setAdapter(aa);

        enableControl(false);

        AlertDialog.Builder builder = new AlertDialog.Builder(CameraActivity.this);
        builder.setTitle("");
        builder.setCancelable(false);
        builder.setMessage("Scanning....");
        scanningMessageBox = builder.create();

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, getIntentFilter());

        if(allPermissionsGranted()){
            startCamera(); //start camera if permission has been granted by user
            deviceController = new DeviceController();
            deviceController.serviceInit(this);
        } else{
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mHandler = new Handler();

    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    // Device scan callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    runOnUiThread( new  Runnable() {
                        @Override
                        public void run() {
                            String address =  result.getDevice().getAddress();
                            if (!addresses.contains(address)){
                                status.append("Found devices " + address + "\n");
                                addresses.add(address);
                            }
                        }
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                }
            };

    private void scanLeDevice(final boolean enable) {
        if (mLeScanner != null) {
            if (enable) {
                // Stops scanning after a pre-defined scan period.
                addresses.clear();
                scanningMessageBox.show();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mScanning = false;
                        scanningMessageBox.hide();
                        mLeScanner.stopScan(mLeScanCallback);
                        List<String> list = new ArrayList<String>(addresses);
                        status.append("Found " + String.valueOf(list.size()) + " devices");

                        if (list.size() <= 0){
                            AlertDialog.Builder builder = new AlertDialog.Builder(CameraActivity.this);
                            builder.setTitle("");
                            builder.setMessage("No BlueFruit device were found!");
                            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // You don't have to do anything here if you just
                                    // want it dismissed when clicked
                                }
                            });

                            builder.create().show();
                    //    }else  if (list.size() == 1) {
                     //       deviceController.connect(list.get(0));
                        }else{
                            AlertDialog.Builder builder = new AlertDialog.Builder(CameraActivity.this);
                            builder.setTitle("Choose a BlueFruit");
                            builder.setCancelable(false);
                            builder.setItems(list.toArray( new String[list.size()]), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    deviceController.connect(list.get(which));
                                }
                            });

                            AlertDialog dialog = builder.create();
                            dialog.show();
                        }
                    }
                }, SCAN_PERIOD);

                mScanning = true;
                ParcelUuid serviceID = new ParcelUuid(RX_SERVICE_UUID);
                ScanFilter filter = new ScanFilter.Builder().setServiceUuid(serviceID).build();
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                        .build();
                List<ScanFilter> filters = new ArrayList<>();
                filters.add(filter);

                mLeScanner.startScan(filters, settings, mLeScanCallback);
            } else {
                mScanning = false;
                mLeScanner.stopScan(mLeScanCallback);
            }
        }
        // invalidateOptionsMenu();
    }
    private IntentFilter getIntentFilter()  {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DEVICE_CONTROLL_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onClick(View view) {
        String v = (String)velocity.getSelectedItem();
        int movement = Integer.parseInt(v);
      //  int movement = 300;
        int viewId = view.getId();
       if (viewId == takePicture.getId()){
            if (imageCapture != null ){
                captureImage();
            }
        }else if (viewId == left.getId()){
           deviceController.moveX(-1* movement);
       } else if (viewId == right.getId()){
            deviceController.moveX( movement);
        } else if (viewId == up.getId()){
            deviceController.moveY(movement);
        } else if (viewId == down.getId()) {
           deviceController.moveY(-1 * movement);
       } else if (viewId == zoomIn.getId()) {
           deviceController.moveZ(-1 * movement);
       } else if (viewId == zoomOut.getId()) {
           deviceController.moveZ(movement);
       }
    }

    private void startCamera() {

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {

                    cameraProvider = cameraProviderFuture.get();
                    bindPreview();

                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindPreview() {

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();


        ImageCapture.Builder builder = new ImageCapture.Builder();

        imageCapture = builder
        .build();
        imageCapture.setTargetRotation(Surface.ROTATION_0);
        preview.setSurfaceProvider(this.previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
    }
    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() +"/";
        return app_folder_path;
    }


    private void captureImage() {
        SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
       // File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");

        //ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
//        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
//            @Override
//            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    previewContainer.setForeground(new ColorDrawable(Color.WHITE));
//                    previewContainer.postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            previewContainer.setForeground(null);
//                        }
//                    }, 300);
//                }
//                //postImage(file.getAbsolutePath());
//            }
//
//            @Override
//            public void onError(@NonNull ImageCaptureException exception) {
//
//            }
//        });
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                super.onCaptureSuccess(image);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    previewContainer.setForeground(new ColorDrawable(Color.WHITE));
                    previewContainer.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            previewContainer.setForeground(null);
                        }
                    }, 300);
                }
                runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Image img = image.getImage();
                            ByteBuffer buffer = img.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);
                            Bitmap bmp = Rotate(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null), 90);

                            OutputStream outputStream = new FileOutputStream((getBatchDirectoryName() + mDateFormat.format(new Date()) + ".jpg"));
                            bmp.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

                            ImageAnalyzer analyzer = ImageAnalyzer.create(CameraActivity.this, 2);
                            analyzer.analyze((bmp));

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    status.append("x=" +  (int) analyzer.result[0] + "\n");
                                    status.append("y=" +  (int) analyzer.result[1] + "\n");
                                }
                            });
                        }
                        catch (Exception e) {

                        }
                        image.close();
                    }
                });

            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                super.onError(exception);
            }
        });
    }

    private Bitmap Rotate(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                deviceController = new DeviceController();
                deviceController.serviceInit(this);
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {

        }

        super.onPause();
    }
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            //status.append(intent.getAction() + "\n");

            if (intent.getAction() == DEVICE_CONTROLL_CONNECTED) {
               // deviceController.connect(deviceAddess);
                status.append("scanning for devices...\n");
                scanLeDevice(true);
            }
            if (intent.getAction() == UartService.ACTION_GATT_CONNECTED) {
                enableControl(true);
            }
            if (intent.getAction() == UartService.ACTION_GATT_DISCONNECTED) {
                enableControl(false);
            }
            if (intent.getAction() == UartService.ACTION_DATA_AVAILABLE) {
                byte[] data = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                String message = new String(data, StandardCharsets.UTF_8);
                status.append(message + "\n");
            }
        }
    };

    private void enableControl(Boolean flag){
        left.setEnabled(flag);
        right.setEnabled(flag);
        up.setEnabled(flag);
        down.setEnabled(flag);
        zoomIn.setEnabled(flag);
        zoomOut.setEnabled(flag);
    }
}

