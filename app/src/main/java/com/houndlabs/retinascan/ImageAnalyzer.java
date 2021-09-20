package com.houndlabs.retinascan;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;

import static java.lang.Math.min;

/** A classifier specialized to label images using TensorFlow Lite. */
public class ImageAnalyzer {
  public static final String TAG = "ImageAnalyzer";

  /** Image size along the x axis. */
  private final int imageSizeX;

  /** Image size along the y axis. */
  private final int imageSizeY;

  /** Optional GPU delegate for accleration. */
  private GpuDelegate gpuDelegate = null;

  /** Optional NNAPI delegate for accleration. */
  private NnApiDelegate nnApiDelegate = null;

  /** An instance of the driver class to run model inference with Tensorflow Lite. */
  protected Interpreter tflite;

  /** Options for configuring the Interpreter. */
  private final Interpreter.Options tfliteOptions = new Interpreter.Options();

  /** Input image TensorBuffer. */
  private TensorImage inputImageBuffer;

  /** Output TensorBuffer. */
  private final TensorBuffer outputBuffer;

  /**
   * Creates a ImageAnalyzer with the provided configuration.
   *
   * @param activity The current Activity.
   * @param numThreads The number of threads to use for analyzer.
   * @return A analyzer with the desired configuration.
   */
  public static ImageAnalyzer create(Activity activity, int numThreads) throws IOException {
      return new ImageAnalyzer(activity,  numThreads);
  }

  /** Initializes a {@code Classifier}. */
  protected ImageAnalyzer(Activity activity,  int numThreads) throws IOException {
    MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());

    tfliteOptions.setNumThreads(numThreads);
    tflite = new Interpreter(tfliteModel, tfliteOptions);

    // Reads type and shape of input and output tensors, respectively.
    int imageTensorIndex = 0;
    int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
    imageSizeY = imageShape[1];
    imageSizeX = imageShape[2];
    DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();

    int outputTensorIndex = 0;
    int[] outputShape =
        tflite.getOutputTensor(outputTensorIndex).shape(); // {1, NUM_CLASSES}
    DataType outputDataType = tflite.getOutputTensor(outputTensorIndex).dataType();

    // Creates the input tensor.
    inputImageBuffer = new TensorImage(imageDataType);

    // Creates the output tensor and its processor.
    outputBuffer = TensorBuffer.createFixedSize(outputShape, outputDataType);

    Log.d(TAG, "Created a Tensorflow Lite Image ImageAnalyzer.");
  }

  /** Runs inference and returns the  results. */
  public float[] analyze(final Bitmap bitmap, int sensorOrientation) {
    // Logs this method so that it can be analyzed with systrace.
    Trace.beginSection("analyze");

    Trace.beginSection("loadImage");
    long startTimeForLoadImage = SystemClock.uptimeMillis();
    inputImageBuffer = loadImage(bitmap, sensorOrientation);
    long endTimeForLoadImage = SystemClock.uptimeMillis();
    Trace.endSection();
    Log.v(TAG, "Timecost to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));

    // Runs the inference call.
    Trace.beginSection("runInference");
    long startTimeForReference = SystemClock.uptimeMillis();
    tflite.run(inputImageBuffer.getBuffer(), outputBuffer.getBuffer().rewind());
    long endTimeForReference = SystemClock.uptimeMillis();
    Trace.endSection();
    Log.v(TAG, "Timecost to run model inference: " + (endTimeForReference - startTimeForReference));

    return outputBuffer.getFloatArray();
  }

  /** Closes the interpreter and model to release resources. */
  public void close() {
    if (tflite != null) {
      tflite.close();
      tflite = null;
    }
    if (gpuDelegate != null) {
      gpuDelegate.close();
      gpuDelegate = null;
    }
    if (nnApiDelegate != null) {
      nnApiDelegate.close();
      nnApiDelegate = null;
    }
  }

  /** Get the image size along the x axis. */
  public int getImageSizeX() {
    return imageSizeX;
  }

  /** Get the image size along the y axis. */
  public int getImageSizeY() {
    return imageSizeY;
  }

  /** Loads input image, and applies preprocessing. */
  private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
    // Loads bitmap into a TensorImage.
    inputImageBuffer.load(bitmap);

    // Creates processor for the TensorImage.
    int cropSize = min(bitmap.getWidth(), bitmap.getHeight());
    int numRotation = sensorOrientation / 90;
    // TODO(b/143564309): Fuse ops inside ImageProcessor.
    ImageProcessor imageProcessor =
        new ImageProcessor.Builder()
            .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
            // TODO(b/169379396): investigate the impact of the resize algorithm on accuracy.
            // To get the same inference results as lib_task_api, which is built on top of the Task
            // Library, use ResizeMethod.BILINEAR.
            .add(new ResizeOp(imageSizeX, imageSizeY, ResizeMethod.NEAREST_NEIGHBOR))
            .add(new Rot90Op(numRotation))
            .add(getPreprocessNormalizeOp())
            .build();
    return imageProcessor.process(inputImageBuffer);
  }

  /** Gets the name of the model file stored in Assets. */
  protected  String getModelPath(){
    return "model";
  }

  /** Gets the TensorOperator to nomalize the input image in preprocessing. */
  protected  TensorOperator getPreprocessNormalizeOp(){
      return new NormalizeOp(0, 255);
  }

}
