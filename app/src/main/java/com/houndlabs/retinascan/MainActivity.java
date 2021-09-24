package com.houndlabs.retinascan;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.houndlabs.retinascan.ml.Model;

import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private ImageView imageView;
    private TextView v1;
    private TextView v2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        v1 = findViewById(R.id.v1);
        v2 = findViewById(R.id.v2);

        analyzeImage();
    }

    private void analyzeImage(){
        BitmapDrawable drawable = (BitmapDrawable) getResources().getDrawable(R.drawable.test, getTheme());
        Bitmap bitmap = drawable.getBitmap();

        try {
            ImageAnalyzer analyzer = ImageAnalyzer.create(this, 2);
            analyzer.analyze(bitmap);
            this.imageView.setImageBitmap(analyzer.bitmap);
            this.v1.setText(String.valueOf(analyzer.result[0]));
            this.v2.setText(String.valueOf(analyzer.result[1]));

        }catch (Exception exception){

        }

    }
}