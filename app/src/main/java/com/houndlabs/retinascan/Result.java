package com.houndlabs.retinascan;

import java.util.SplittableRandom;

public class Result {
    public Result(String file, long preProcessingTime, long inferenceTime, float v1, float v2){
        this.file = file;
        this.preProcessingTime = preProcessingTime;
        this.inferenceTime = inferenceTime;
        this.v1 = v1;
        this.v2 = v2;
    }
    public String file;
    public long preProcessingTime;
    public long inferenceTime;
    public float v1;
    public float v2;
}
