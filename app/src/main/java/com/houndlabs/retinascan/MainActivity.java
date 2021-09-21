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
        Bitmap bitmap = crop(scale(drawable.getBitmap()));

        this.imageView.setImageBitmap(bitmap);

        try {
            ImageAnalyzer analyzer = ImageAnalyzer.create(this, 2);
            float[] steps =  analyzer.analyze(bitmap);
            this.v1.setText(String.valueOf(steps[0]));
            this.v2.setText(String.valueOf(steps[1]));

        }catch (Exception exception){

        }

    }
    private void processImage() {
        try {
            Model model = Model.newInstance(this);

            BitmapDrawable drawable = (BitmapDrawable) getResources().getDrawable(R.drawable.test, getTheme());
            Bitmap bitmap = crop(scale(drawable.getBitmap()));

            this.imageView.setImageBitmap(bitmap);

            // Creates inputs for reference.
            TensorImage image = TensorImage.fromBitmap(bitmap);

           // TensorImage image2 = normalize(image);

            // Runs model inference and gets result.
            Model.Outputs outputs = model.process(image);
            TensorBuffer calculatedSteps = outputs.getCalculatedStepsAsTensorBuffer();
            float [] steps = calculatedSteps.getFloatArray();

            this.v1.setText(String.valueOf(steps[0]));
            this.v2.setText(String.valueOf(steps[1]));

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {

        }
    }

    private Bitmap scale(Bitmap input) {
        final int targetWidth = 504;
        final int targetHeight = 378;
        return Bitmap.createScaledBitmap(input, targetWidth, targetHeight, true);
    }

    private Bitmap crop(Bitmap input){
        final int targetWidth  = 434;
        final int targetHeight = 363;

        Bitmap output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);

        int dstL = 0;
        int dstR = targetWidth;
        int srcL = 20;
        int srcR = srcL + targetWidth;
        int dstT = 0;
        int dstB = targetHeight;
        int srcT = 15;
        int srcB = srcT + targetHeight;

        Rect src = new Rect(srcL, srcT, srcR, srcB);
        Rect dst = new Rect(dstL, dstT, dstR, dstB);

        new Canvas(output).drawBitmap(input, src, dst, null);

        return output;
    }

    private TensorImage normalize(TensorImage inputImageBuffer) {
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(getPreprocessNormalizeOp())
                        .build();
        return imageProcessor.process(inputImageBuffer);
    }

    protected TensorOperator getPreprocessNormalizeOp() {
        return new NormalizeOp(new float[] {127.5f }, new float[] {127.5f});
    }
}