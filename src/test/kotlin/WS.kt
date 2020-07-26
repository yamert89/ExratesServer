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
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

@RunWith(SpringJUnit4ClassRunner::class)
@WebAppConfiguration
class WS{

    @Autowired
    lateinit var context: ApplicationContext

    val writer = BufferedWriter(FileWriter(File("J:/2.txt")))



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
        val handler1 = Handler(writer)

        client.doHandshake(handler1, "wss://ws-feed-public.sandbox.pro.coinbase.com" )
        runBlocking {
            delay(100000)
            writer.close()
        }
    }

}

class Handler(val writer: BufferedWriter) : TextWebSocketHandler(){

    override fun afterConnectionEstablished(session: WebSocketSession) {
        super.afterConnectionEstablished(session)
        session.sendMessage(TextMessage("{\n" +
                "    \"type\": \"subscribe\",\n" +
                "    \"product_ids\": [\n" +
                "        \"ETH-BTC\"\n" +
                "    ],\n" +
                "    \"channels\": [\n" +
                "        \"level2\",\n" +
                "        \"heartbeat\",\n" +
                "        {\n" +
                "            \"name\": \"ticker\",\n" +
                "            \"product_ids\": [\n" +
                "                \"ETH-BTC\"\n" +
                "            ]\n" +
                "        }\n" +
                "    ]\n" +
                "}"))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {

    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        super.handleMessage(session, message)
        writer.newLine()
        writer.write(message.payload.toString())
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        super.handlePongMessage(session, message)
        writer.newLine()
        writer.write(message.payload.toString())
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        super.handleTransportError(session, exception)
        writer.newLine()
        writer.write(exception.message)
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        super.handleBinaryMessage(session, message)
        writer.newLine()
        writer.write(message.payload.toString())
    }

}