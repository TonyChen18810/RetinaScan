package com.houndlabs.retinascan;


        import org.checkerframework.checker.nullness.qual.NonNull;
        import org.tensorflow.lite.DataType;
        import org.tensorflow.lite.support.common.SupportPreconditions;
        import org.tensorflow.lite.support.common.TensorOperator;
        import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
        import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

public class CustomNormalizeOp implements TensorOperator {


    public CustomNormalizeOp() {

    }

    @NonNull
    public TensorBuffer apply(@NonNull TensorBuffer input) {
        int[] shape = input.getShape();
        float[] values = input.getFloatArray();

        for (int i = 0; i < values.length - 3; i=i+3) {
           float v0 = values[i] / 255.0f;
           float v1 = values[i+1] / 255.0f;
           float v2 = values[i+2] / 255.0f;

           values[i] = v2;
           values[i+1] = v1;
           values[i+2] = v0;
        }

        TensorBuffer output;
        if (input.isDynamic()) {
            output = TensorBufferFloat.createDynamic(DataType.FLOAT32);
        } else {
            output = TensorBufferFloat.createFixedSize(shape, DataType.FLOAT32);
        }

        output.loadArray(values, shape);
        return output;
    }
}

