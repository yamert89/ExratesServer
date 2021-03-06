import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.math.BigDecimal
import java.math.MathContext
import java.util.*
import kotlin.collections.ArrayList

fun main(){
    //round()
    //val my = My()
    //println(ObjectMapper().writeValueAsString(my))
    //varargFun()
    clientToken()

}
fun round(){
    val changeVol = 0.123456789
    println(BigDecimal(changeVol).round(MathContext(3)).toDouble())
    println(BigDecimal(changeVol).round(MathContext(2)).toDouble())
    println(BigDecimal(changeVol).round(MathContext(1)).toDouble())
    println(BigDecimal(changeVol).round(MathContext(0)).toDouble())
    println(BigDecimal(changeVol, MathContext(2)))
}

fun varargFun(){
    val s1 = "1"
    val s2 = "2"
    val s3 = "3"
    argFun(*arrayOf(s1, s2, s3))
}
fun argFun(vararg args: String){
    println(args.joinToString())
}

fun clientToken(){
    println(UUID.randomUUID())
}

class MySerializer: JsonSerializer<MutableList<Any>>(){
    override fun serialize(value: MutableList<Any>?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen!!.writeStartArray()
        val array = value as MutableList<Array<String>>
        array.forEach {
            gen.writeStartObject()
            gen.writeFieldName(it[0])
            gen.writeString(it[1])
            gen.writeEndObject()
        }
        gen.writeEndArray()
    }

}

class My{
    @JsonSerialize(using = MySerializer::class)
    val list : MutableList<Any> = ArrayList<Any>()
    init {
        list.add(arrayOf("result", "1"))
        list.add(arrayOf("result", "2"))
    }
}