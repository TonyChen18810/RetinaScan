package com.houndlabs.retinascan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {
    private final String deviceAddess = "D8:4B:33:33:70:90";
    private DeviceController deviceController;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private View takePicture, previewContainer;
    private View left, right, up, down, zoomIn, zoomOut;
    private TextView status;

    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.BLUETOOTH"};

    public final static String DEVICE_CONTROLL_CONNECTED =
            "com.nordicsemi.device.controll.connected";
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

        deviceController = new DeviceController();
        deviceController.serviceInit(this);

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, getIntentFilter());

        if(allPermissionsGranted()){
            startCamera(); //start camera if permission has been granted by user
        } else{
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
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
        int movement = 100;
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
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();
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
        File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    previewContainer.setForeground(new ColorDrawable(Color.WHITE));
                    previewContainer.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            previewContainer.setForeground(null);
                        }
                    }, 300);
                }
                //postImage(file.getAbsolutePath());
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {

            }
        });
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
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            status.append(intent.getAction() + "\n");

            if (intent.getAction() == DEVICE_CONTROLL_CONNECTED) {
                deviceController.connect(deviceAddess);
                status.append("Connecting to device " + deviceAddess + "\n");
            }
        }
    };
}

