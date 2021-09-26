package com.houndlabs.retinascan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.houndlabs.retinascan.ml.Model;

import org.checkerframework.checker.units.qual.A;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ImageView imageView;
    private TextView v1;
    private TextView v2;
    private TextView title;

    private Handler handler;
    private HandlerThread handlerThread;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    private ArrayList<Result> results = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        v1 = findViewById(R.id.v1);
        v2 = findViewById(R.id.v2);
        title = findViewById(R.id.title);

        Button b = findViewById(R.id.run);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                run();
            }
        });

        title.setText("Tap Run to start");
        verifyStoragePermissions(this);

    }

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
    private void run() {
        results.clear();
        ArrayList<String> files = listFile();

        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    ImageAnalyzer analyzer = ImageAnalyzer.create(MainActivity.this, 2);
                    for (String file : files
                    ) {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                title.setText("Processing " + file);
                            }
                        });

                        analyzer.analyze(file);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.this.imageView.setImageBitmap(analyzer.bitmap);
                                MainActivity.this.v1.setText(String.valueOf(analyzer.result[0]));
                                MainActivity.this.v2.setText(String.valueOf(analyzer.result[1]));
                                results.add( new Result(file, analyzer.preprocessingTime, analyzer.inferenceTime, analyzer.result[0],
                                        analyzer.result[1]));
                            }
                        });

                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            title.setText("Done ");
                            try {
                                saveResult(results);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });


                } catch (Exception exception) {

                }
            }
        });

    }
    private ArrayList<String> listFile() {
        ArrayList<String> result = new ArrayList<>();

        String path = Environment.getExternalStorageDirectory().toString()+"/retinaScanner/input";
        File directory = new File(path);
        File[] files = directory.listFiles();

        for (int i = 0; i < files.length; i++)
        {
            result.add(files[i].getAbsolutePath());
        }
        return  result;
    }

    private  void saveResult(List<Result> results) throws IOException {
        String path = Environment.getExternalStorageDirectory().toString()+"/retinaScanner/result.csv";
        File file = new  File(path);
        if (!file.exists()){
            file.createNewFile();
        }

        FileWriter writer = new FileWriter(path);
        for (Result r: results
             ) {
            writer.write(r.file + "," + r.preProcessingTime + "," +
                    r.inferenceTime + "," + r.v1 + "," + r.v2 + "\n");
        }
        writer.close();

    }
    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
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

}