package com.xalies.tiktapremote.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xalies.tiktapremote.Action
import com.xalies.tiktapremote.TriggerType

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromActionsMap(value: Map<TriggerType, Action>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toActionsMap(value: String): Map<TriggerType, Action> {
        val mapType = object : TypeToken<Map<TriggerType, Action>>() {}.type
        return gson.fromJson(value, mapType) ?: emptyMap()
    }
}