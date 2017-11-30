package com.salcedo.rapbot.motor;

import com.google.gson.annotations.SerializedName;

/**
 * The location of the motor relative to the bow of the robot.
 */
public enum Location {
    @SerializedName("0") BACK_LEFT, @SerializedName("1") BACK_RIGHT
}
