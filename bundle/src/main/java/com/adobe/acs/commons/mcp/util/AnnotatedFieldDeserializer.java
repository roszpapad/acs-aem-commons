/*
 * Copyright 2017 Adobe.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.adobe.acs.commons.mcp.util;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.resource.ValueMap;
import com.adobe.acs.commons.mcp.FormField;

/**
 * Processing routines for handing ProcessInput within a FormProcessor
 */
public class AnnotatedFieldDeserializer {

    public static void processInput(Object target, ValueMap input) throws DeserializeException {
        List<Field> fields = FieldUtils.getFieldsListWithAnnotation(target.getClass(), FormField.class);
        for (Field field : fields) {
            try {
                parseInput(target, input, field);
            } catch (ParseException | ReflectiveOperationException | NullPointerException ex) {
                throw new DeserializeException("Error when processing field "+field.getName(), ex);
            }
        }
    }

    private static void parseInput(Object target, ValueMap input, Field field) throws ReflectiveOperationException, ParseException {
        FormField inputAnnotation = field.getAnnotation(FormField.class);
        if (input.get(field.getName()) == null) {
            if (inputAnnotation.required()) {
                throw new NullPointerException("Required field missing: "+field.getName());
            } else {
                return;
            }
        }
        
        Object value = input.get(field.getName());
        if (isListOrArray(field)) {
            if (value instanceof String[]) {
                parseInputList(target, (String[]) value, field);
            } else {
                parseInputList(target, new String[]{String.valueOf(value)}, field);                
            }
        } else {
            if (value instanceof String[]) {
                parseInputValue(target, ((String[]) value)[0], field);
            } else {
                parseInputValue(target, (String) value, field);            
            }
        }        
    }

    private static void parseInputList(Object target, String[] values, Field field) throws ReflectiveOperationException, ParseException {
        List convertedValues = new ArrayList();
        for (String value : values) {
            convertedValues.add(convertValue(value, field.getType()));
        }
        if (field.getType().isArray()) {
            FieldUtils.writeField(field, target, convertedValues.toArray(), true);
        } else {
            Collection c = (Collection) field.getType().newInstance();
            c.addAll(convertedValues);
            FieldUtils.writeField(field, target, c, true);
        }
    }

    private static void parseInputValue(Object target, String value, Field field) throws ReflectiveOperationException, ParseException {
        FieldUtils.writeField(field, target, convertValue(value, field.getType()), true);
    }

    private static boolean isListOrArray(Field field) {
        return field.getType().isArray() && ! field.getType().isInstance(Collection.class);
    }

    private static Object convertValue(String value, Class<?> type) throws ParseException {
        Class clazz = type.isArray() ? type.getComponentType() : type;
        if (clazz.isPrimitive()) {
            return convertPrimitiveValue(value, clazz);
        } else if (clazz.isInstance(String.class)) {
            return value;
        } else if (clazz.isEnum()) {
            return Enum.valueOf((Class<Enum>) clazz, value);
        }
        return null;
    }

    private static Object convertPrimitiveValue(String value, Class<?> type) throws ParseException {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        Number num = numberFormat.parse(value);
        if (type.equals(Byte.class) || type.equals(Byte.TYPE)) {
            return num.byteValue();
        } else if (type.equals(Double.class) || type.equals(Double.TYPE)) {
            return num.doubleValue();
        } else if (type.equals(Float.class) || type.equals(Float.TYPE)) {
            return num.floatValue();
        } else if (type.equals(Integer.class) || type.equals(Integer.TYPE)) {
            return num.intValue();
        } else if (type.equals(Long.class) || type.equals(Long.TYPE)) {
            return num.longValue();
        } else if (type.equals(Short.class) || type.equals(Short.TYPE)) {
            return num.shortValue();
        } else {
            return null;
        }
    }
    
    private AnnotatedFieldDeserializer() {
        // Utility class has no constructor
    }
}
