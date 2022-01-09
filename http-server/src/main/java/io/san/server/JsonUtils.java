package io.san.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class JsonUtils {
    final static ObjectMapper jsonMapper;

    static {
        jsonMapper = new ObjectMapper();
        jsonMapper.setTimeZone(TimeZone.getDefault());
        jsonMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));
    }


    public static String toJson(Object src) {
        try {
            return jsonMapper.writeValueAsString(src);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize data", e);
        }
    }


    public static JsonNode toJsonNode(String jsonStr) throws IOException {
        return jsonMapper.readTree(jsonStr);
    }

    public static <T> T toObject(String jsonStr, Class<T> clazz) throws IOException {
        return jsonMapper.readValue(jsonStr, clazz);
    }

    public static <T> T toObject(String jsonStr, TypeReference<T> clazz) throws IOException {
        return jsonMapper.readValue(jsonStr, clazz);
    }

}
