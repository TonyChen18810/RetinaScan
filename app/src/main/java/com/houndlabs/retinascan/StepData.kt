package com.houndlabs.retinascan

import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.sqrt

enum class Direction {
    NONE,
    TOP, LEFT, DOWN, RIGHT,
    RIGHT_TOP, RIGHT_DOWN, LEFT_DOWN, LEFT_TOP
}

data class StepData(
    val img_num: Int,
    val steps: List<Int>,
    val steps_total: List<Int>,
    val steps_total_plane_normalized: List<Double>,
    val steps_last_three: List<Int>,
    val steps_last_three_normalized: List<Double>
)

@ExperimentalStdlibApi
fun generateMovements(): List<StepData> {
    val randomStepData = ArrayList<StepData>()

    var circularUnits = 8
    var revolutions = 1
    var xTotal = 0
    var yTotal = 0
    var zTotal = 0;

    var angle : Double = 0.0;
    //var angleIncrement : Double = (Math.PI * 2) / circularUnits

    var xLast = 0
    var yLast= 0
    //var zLast = 0

    val zIncrement = 1000
    var xyFactor = 20

    var radius = 100

    for (zRun in 1..15) {
        for (j in 1..revolutions) {
            for (i in 1..circularUnits) {
                val angleIncrement : Double = (Math.PI * 2) / circularUnits

                // val x1 = ( (angle + initialRadius) * Math.cos(angle) * xyFactor ).toInt() //new x position
                val x1 = (radius * Math.cos(angle)).toInt() //new x position
                //val y1 = ( (angle + initialRadius) * Math.sin(angle) * xyFactor ).toInt()  // new y position
                val y1 = (radius * Math.sin(angle)).toInt()  // new y position

                val steps = listOf(
                    x1 - xLast,
                    y1 - yLast,
                    0
                )
                xTotal += steps[0]
                yTotal += steps[1]

                val stepsTotal = listOf(xTotal, yTotal, zTotal)
                addStep(randomStepData, steps, stepsTotal)

                angle += angleIncrement
                xLast = x1
                yLast = y1
            }
            radius += getRadiusIncrement(zTotal);

        }

        val steps = listOf(  // zoom out in z and return to the center position
           0 - xLast,
            0 - yLast,
            getZIncrement(zTotal)
        )

        xTotal = 0;
        yTotal = 0;
        zTotal += getZIncrement(zTotal)

        xLast = xTotal; // should be zero
        yLast = yTotal; // should be zero


        val stepTotal = listOf(xTotal, yTotal, zTotal)
        addStep(randomStepData, steps, stepTotal)

        circularUnits += 8

    }
    return randomStepData
}

fun getRadiusIncrement(z: Int) : Int{
    return 300
}

fun getZIncrement(z:Int) : Int {
    return 1000
}

@ExperimentalStdlibApi
fun addStep(randomStepData: ArrayList<StepData>, step: List<Int>, steps_total: List<Int>){
    val steps_total_plane_normalized = normalize(steps_total)
    val steps_last_three =
        randomStepData.subList(max(randomStepData.size - 3, 0), randomStepData.size)
            .map { it.steps }
            .reduceOrNull { a, b ->
                listOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])
            }.orEmpty().ifEmpty { listOf(0, 0, 0) }
    val last_three_direction = normalize(steps_last_three)

    randomStepData.add(
        StepData(
            img_num = randomStepData.size + 1,
            steps = step,
            steps_total = steps_total,
            steps_total_plane_normalized = steps_total_plane_normalized,
            steps_last_three = steps_last_three,
            steps_last_three_normalized = last_three_direction
        )
    )
}
fun normalize(v: List<Int>): List<Double> {
    val l = 1.0f / length(v)
    return listOf(v[0] * l, v[1] * l)
}
inline fun length(v: List<Int>) = sqrt((v[0] * v[0] + v[1] * v[1]).toDouble())
inline fun length(v: List<Float>) = sqrt(v[0] * v[0] + v[1] * v[1])
