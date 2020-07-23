import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.web.socket.*
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler

@RunWith(SpringJUnit4ClassRunner::class)
@WebAppConfiguration
class WS{

    @Autowired
    lateinit var context: ApplicationContext

   /* @Autowired
    lateinit var objectMapper: ObjectMapper*/

    /*@Test
    fun wsConnect(){
        val client = org.springframework.web.socket.client.standard.StandardWebSocketClient()
        val stompClient = WebSocketStompClient(client).apply {
           // messageConverter = MessageConverter

        }

        val handler: StompSessionHandler = object : StompSessionHandlerAdapter(){
                lateinit var session: StompSession

            override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
                print("WS connected")
                this.session = session
                session.send(
                    StompHeaders(), "{\n" +
                        "  \"sub\": \"market.btcusdt.kline.1min\",\n" +
                        "  \"id\": \"id1\"\n" +
                        "}")
                session.subscribe("market.ethbtc.kline.1min", this)
            }

            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                super.handleFrame(headers, payload)
                val res = JSONObject(payload as String)
                val v = res.get("ping")
                var end: String = ""
                session.send(StompHeaders(), "{pong:$v}")
                if (v == null) {
                    end = res.get("status") as String
                    Assert.assertEquals("ok", end)
                }


            }

            override fun getPayloadType(headers: StompHeaders): Type {
                return String::class.java
            }
        }

        stompClient.connect("wss://api.huobi.pro/ws", handler )
        runBlocking {
            delay(10000)
        }

    }
*/
    @Test
    fun nativeWs(){
        val client = StandardWebSocketClient()
        val handler1 = Handler()
        val handler2 = object : org.springframework.web.socket.WebSocketHandler{
            override fun handleTransportError(session: WebSocketSession, exception: Throwable) {

            }

            override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {

            }

            override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
                val res = JSONObject(message.payload.toString())
                val v = res.get("ping")
                var end: String = ""
                if (v == null) {
                    end = res.get("status") as String
                    Assert.assertEquals("ok", end)
                }else session.sendMessage(TextMessage("{pong:$v}"))
            }

            override fun afterConnectionEstablished(session: WebSocketSession) {
                session.sendMessage(TextMessage("{\n" +
                        "  \"sub\": \"market.btcusdt.kline.1min\",\n" +
                        "  \"id\": \"1\"\n" +
                        "}"))
            }

            override fun supportsPartialMessages(): Boolean {
                return true
            }
        }


        client.doHandshake(handler1, "wss://api.huobi.pro/ws" )
        runBlocking {
            delay(20000)
        }
    }

}

class Handler : TextWebSocketHandler(){
    override fun afterConnectionEstablished(session: WebSocketSession) {
        super.afterConnectionEstablished(session)
        session.sendMessage(TextMessage("{\n" +
                "  \"sub\": \"market.btcusdt.kline.1min\",\n" +
                "  \"id\": \"1\"\n" +
                "}"))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)
        val res = JSONObject(message.payload.toString())
        val v = res.get("ping")
        var end: String = ""
        if (v == null) {
            end = res.get("status") as String
            Assert.assertEquals("ok", end)
        }else session.sendMessage(TextMessage("{pong:$v}"))
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        super.handlePongMessage(session, message)
        val buffer = message.payload// flip the buffer for reading
        buffer.flip()

        val bytes =
            ByteArray(buffer.remaining()) // create a byte array the length of the number of bytes written to the buffer

        buffer.get(bytes) // read the bytes that were written

        val packet = String(bytes)
        val res = JSONObject(packet)
        val v = res.get("ping")
        session.sendMessage(TextMessage("{pong:$v}"))
    }
}