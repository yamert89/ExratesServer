import org.junit.Assert
import org.junit.Test
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import ru.exrates.func.EndpointStateChecker

class ObjectTest {

    @Test
    fun stateChecker(){
        val checker = EndpointStateChecker()
        val jsObj = JSONObject("{\"id\": 1}")
        val jsArr = JSONArray("[{\"id\": 2}]")
        val jsEmpty = JSONObject()
        val jsArrEmpty = JSONArray()
        Assert.assertEquals(false, checker.checkEmptyJson(jsObj, 1))
        Assert.assertEquals(false, checker.checkEmptyJson(jsArr, 1))
        Assert.assertEquals(true, checker.checkEmptyJson(jsArrEmpty, 1))
        Assert.assertEquals(true, checker.checkEmptyJson(jsEmpty, 1))
        checker.checkEmptyJson(jsEmpty, 1)
        checker.checkEmptyJson(jsEmpty, 1)
        checker.checkEmptyJson(jsEmpty, 1)
        checker.checkEmptyJson(jsEmpty, 1)
        Assert.assertEquals(false, checker.checkAccessible(1))
        checker.checkEmptyJson(jsObj, 1)
        Assert.assertEquals(true, checker.checkAccessible(1))
        Assert.assertEquals(true, checker.checkAccessible(2))

    }
}