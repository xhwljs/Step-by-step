package com.feiyu.stepbystepmod.data

data class FeatureConfig(
    val name: String,
    var enabled: Boolean = false,
    var description: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FeatureConfig
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
