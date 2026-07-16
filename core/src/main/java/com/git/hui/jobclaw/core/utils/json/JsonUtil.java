package com.git.hui.jobclaw.core.utils.json;

import lombok.Getter;
import org.hibernate.Hibernate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author YiHui
 * @date 2025/7/14
 */
public class JsonUtil {
    // AIDEV-NOTE: Shared Jackson 3 mapper.
    @Getter
    private static final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .addModule(new SimpleModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private JsonUtil() {
    }

    public static String toStr(Object value) {
        try {
            if (value.getClass().getName().contains("HibernateProxy")) {
                value = Hibernate.unproxy(value);
            }
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize JSON", e);
        }
    }

    public static <T> List<T> toList(String value, Class<T> clazz) {
        try {
            return mapper.readValue(value, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON list", e);
        }
    }

    public static <T> T toObj(String value, Class<T> clazz) {
        try {
            return mapper.readValue(value, clazz);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T toObj(String value, Type type) {
        try {
            return mapper.readValue(value, new TypeReference<>() {
                @Override
                public Type getType() {
                    return type;
                }
            });
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON type", e);
        }
    }

    public static <T> T toObj(String value, TypeReference<T> typeReference) {
        try {
            return mapper.readValue(value, typeReference);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON type", e);
        }
    }

    public static JsonNode toJsonNode(String value) {
        try {
            return mapper.readTree(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse JSON tree", e);
        }
    }
}
