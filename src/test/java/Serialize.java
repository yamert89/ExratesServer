import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Serialize {

    public static void main(String[] args) throws JsonProcessingException {
        List<Object> list = new ArrayList<>();
        list.add(new String[]{"result", "1"});
        list.add(new String[]{"result", "2"});
        System.out.println(new ObjectMapper().writeValueAsString(new Model(list)));

    }

    @JsonSerialize(using = MySerializer2.class)
    public static class Model{
        List<Object> list;
        public Model(List<Object> list){
            this.list = list;
        }
    }


    public static class MySerializer2 extends JsonSerializer<Model>{

        public MySerializer2() {
        }

        @Override
        public void serialize(Model model, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            List<Object> value = model.list;
            gen.writeStartArray();
            value.forEach(it ->{
                try {
                    gen.writeStartObject();
                    String[] arr = (String[]) it;
                    gen.writeFieldName(arr[0]);
                    gen.writeString(arr[1]);
                    gen.writeEndObject();
                }catch (IOException e){
                    e.printStackTrace();
                }
            });
            gen.writeEndArray();
        }
    }




}


