package com.taplab.experiment.model

import org.json.JSONArray
import org.json.JSONObject

object BehaviorCodec {
    fun encode(mode: BehaviorMode): String {
        return encodeMode(mode.sanitized()).toString(2)
    }

    fun decode(text: String): BehaviorMode {
        return decodeMode(JSONObject(text)).sanitized()
    }

    private fun encodeMode(mode: BehaviorMode): JSONObject {
        return JSONObject()
            .put("version", 1)
            .put("id", mode.id)
            .put("name", mode.name)
            .put("grabs", JSONArray().apply { mode.grabs.forEach { put(encodeGrab(it)) } })
            .put("steps", JSONArray().apply { mode.steps.forEach { put(encodeStep(it)) } })
    }

    private fun encodeGrab(grab: GrabSpec): JSONObject {
        return JSONObject()
            .put("id", grab.id)
            .put("name", grab.name)
            .put("centerX", grab.centerX)
            .put("centerY", grab.centerY)
            .put("side", grab.side)
    }

    private fun encodeStep(step: Step): JSONObject {
        return when (step) {
            is Step.Click -> JSONObject()
                .put("type", "click")
                .put("id", step.id)
                .put("x", step.x)
                .put("y", step.y)
                .put("sigmaX", step.sigmaX)
                .put("sigmaY", step.sigmaY)
            is Step.Delay -> JSONObject()
                .put("type", "delay")
                .put("id", step.id)
                .put("millis", step.millis)
            is Step.Compare -> JSONObject()
                .put("type", "compare")
                .put("id", step.id)
                .put("grabId", step.grabId)
                .put("tolerance", step.tolerance)
                .put("matchPercent", step.matchPercent)
                .put("whenMatch", JSONArray().apply { step.whenMatch.forEach { put(encodeStep(it)) } })
                .put("whenMiss", JSONArray().apply { step.whenMiss.forEach { put(encodeStep(it)) } })
        }
    }

    private fun decodeMode(json: JSONObject): BehaviorMode {
        return BehaviorMode(
            id = json.getString("id"),
            name = json.optString("name"),
            grabs = json.optJSONArray("grabs").mapGrabs(),
            steps = json.optJSONArray("steps").mapSteps(),
        )
    }

    private fun decodeGrab(json: JSONObject): GrabSpec {
        return GrabSpec(
            id = json.getString("id"),
            name = json.optString("name"),
            centerX = json.optInt("centerX"),
            centerY = json.optInt("centerY"),
            side = json.optInt("side"),
        )
    }

    private fun decodeStep(json: JSONObject): Step {
        return when (json.getString("type")) {
            "click" -> Step.Click(
                id = json.getString("id"),
                x = json.optInt("x"),
                y = json.optInt("y"),
                sigmaX = json.optDouble("sigmaX", 0.0),
                sigmaY = json.optDouble("sigmaY", 0.0),
            )
            "delay" -> Step.Delay(
                id = json.getString("id"),
                millis = json.optLong("millis"),
            )
            "compare" -> Step.Compare(
                id = json.getString("id"),
                grabId = json.optString("grabId"),
                tolerance = json.optInt("tolerance", 18),
                matchPercent = json.optInt("matchPercent", 90),
                whenMatch = json.optJSONArray("whenMatch").mapSteps(),
                whenMiss = json.optJSONArray("whenMiss").mapSteps(),
            )
            else -> Step.Delay(id = json.optString("id", "unknown"), millis = 0)
        }
    }

    private fun JSONArray?.mapSteps(): List<Step> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(decodeStep(getJSONObject(index)))
        }
    }

    private fun JSONArray?.mapGrabs(): List<GrabSpec> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(decodeGrab(getJSONObject(index)))
        }
    }
}
