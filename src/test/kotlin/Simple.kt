import java.math.BigDecimal
import java.math.MathContext

fun main(){
    round()
}
fun round(){
    val changeVol = 0.123456789
    println(BigDecimal(changeVol).round(MathContext(3)).toDouble())
    println(BigDecimal(changeVol).round(MathContext(2)).toDouble())
    println(BigDecimal(changeVol).round(MathContext(1)).toDouble())
    println(BigDecimal(changeVol).round(MathContext(0)).toDouble())
    println(BigDecimal(changeVol, MathContext(2)))
}