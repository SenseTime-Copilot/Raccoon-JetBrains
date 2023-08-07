package com.sensetime.sensecore.sensecodeplugin.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ModelSerializer::class)
enum class Model(val code: String) {
    PENROSE_411("penrose-411");

    companion object {
        fun fromCode(code: String): Model =
            values().firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown Model code: $code")
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Model::class)
object ModelSerializer {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Model", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Model) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Model {
        val code = decoder.decodeString()
        return Model.fromCode(code)
    }
}
