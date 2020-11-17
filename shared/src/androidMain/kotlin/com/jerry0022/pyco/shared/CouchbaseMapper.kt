package com.jerry0022.pyco.shared

import com.couchbase.lite.MutableDocument
import com.couchbase.lite.Result
import com.couchbase.lite.ResultSet
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor


/*
 * Enriches Couchbase database methods and data object methods sorted by Data Objects.
 */

interface PersistentObject {
    fun couchbaseID() : String = hashCode().toString()

    /**
     * Serializes an [PersistentObject] into a [MutableDocument] by the taking the primary constructor of the base class.
     *
     * @return Returns a new [MutableDocument]!
     */
    fun getMutableDocument() : MutableDocument
    {
        val mutableDocument = MutableDocument(couchbaseID())
        for(member in this::class.primaryConstructor!!.parameters)
        {
            when(member.type)
            {
                String::class.createType() -> mutableDocument.setString(member.name!!, member.readInstanceProperty(this))
                Date::class.createType() -> mutableDocument.setLong(
                        member.name!!, member.readInstanceProperty<Date>(this).toInstant().toEpochMilli()
                )
                else -> throw IllegalStateException("Data member type is not recognized!")
            }
        }
        return mutableDocument
    }
}

internal fun <T : PersistentObject> ResultSet.toObjectList(clazz : Class<T>) : List<T> {
    return this.map { it.toObject(clazz) }
}

/**
 * Maps a result to an [PersistentObject].
 *
 * @return Returns a new [PersistentObject] object.
 */
private fun <T : PersistentObject> Result.toObject(clazz : Class<T>) : T {
    val value = JsonDeserializer { json, typeOfT, _ ->
        val jsonObject = json!!.asJsonObject
        val parameters = Class.forName(typeOfT!!.typeName).kotlin.primaryConstructor!!
        val argParameters: MutableMap<KParameter, Any> = HashMap(parameters.parameters.size)
        for (parameter in parameters.parameters)
            argParameters[parameter] = when (parameter.type) {
                String::class.createType() -> jsonObject.get(parameter.name).asString
                Date::class.createType() -> Date(jsonObject.get(parameter.name).asLong)
                else -> throw IllegalStateException("Data member type is not recognized!")
            }
        parameters.callBy(argParameters) as PersistentObject
    }

    // TODO Kotlinx use does not work yet
    val toString = this.toMap()[this.toMap().keys.last()].toString()
    //Log.w("SERIAL", Json.decodeFromString<Article>(toString).toString())

    val gson = GsonBuilder()
            .registerTypeAdapter(
                    clazz,
                    value
            ).create()
    return gson.fromJson(toString, clazz)
}

/**
 * Read property value by property name without calling explicitly the class before.
 *
 * @param R
 * @param persistentObject
 * @return
 */
@Suppress("UNCHECKED_CAST")
private fun <R> KParameter.readInstanceProperty(persistentObject: PersistentObject): R {
val property = persistentObject::class.members
    .first { it.name == this.name } as KProperty1<Any, *>
// force a invalid cast exception if incorrect type here
return property.get(persistentObject) as R
}

@ExperimentalSerializationApi
@Serializer(forClass = Date::class)
object DateSerializer : KSerializer<Date> {
    private val serializer = Long.serializer()

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Date {
        return Date(decoder.decodeLong())
    }

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeLong(value.toInstant().toEpochMilli())
    }

}