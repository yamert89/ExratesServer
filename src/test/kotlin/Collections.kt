import org.junit.Assert
import org.junit.Test

class Collections {
    @Test
    fun sortMap(){
        val map = hashMapOf("a" to 234, "b" to 2643, "c" to 1, "d" to 99)
        val list = mutableListOf<String>()
        list.addAll(map.entries.sortedBy { it.value }.map { it.key })
        Assert.assertEquals("c", list.first())
        Assert.assertEquals("d", list[1])
        Assert.assertEquals("a", list[2])
        Assert.assertEquals("b", list.last())
    }
}