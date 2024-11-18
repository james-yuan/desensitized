package com.actualiti.desensiti;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ObjectConvert
 */
@Slf4j
@Component
public class ObjectConvert {

    public static boolean desensitize;

    @Value("${desensitize:true}")
    public void setDesensitize(boolean desensitize) {
        ObjectConvert.desensitize = desensitize;
    }

    /**
     * 深度克隆对象
     *
     * @throws IllegalAccessException IllegalAccessException
     * @throws InstantiationException InstantiationException
     */
    public static Object deepClone(Object objSource) throws InstantiationException, IllegalAccessException {
        // 是否jdk类型、基础类型、枚举类型
        if (null == objSource || isJDKType(objSource.getClass())
                || objSource.getClass().isPrimitive() || objSource instanceof Enum<?>) {
            return objSource;
        }
        // 获取源对象类型
        Object objDes = objSource.getClass().newInstance();
        // 获得源对象所有属性
        Field[] fields = getAllFields(objSource);
        // 循环遍历字段，获取字段对应的属性值
        for (Field field : fields) {
            if (null == field) {
                continue;
            }
            AccessController.doPrivileged((PrivilegedAction) () -> {
                field.setAccessible(true);
                return null;
            });
            Object value = field.get(objSource);
            if (null == value || isStaticFinal(field)) {
                continue;
            }
            setBeanValue(objSource, field, value, objDes);
        }
        return objDes;
    }

    @SneakyThrows
    private static void setBeanValue(Object objSource, Field field, Object value, Object objDes) {
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len < 1) {
                return;
            }
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                Object arrayObject = Array.get(value, i);
                Array.set(list, i, deepClone(arrayObject));
            }
        }
        if (value instanceof Collection<?>) {
            Collection newCollection = (Collection) value.getClass().newInstance();
            Collection<?> c = (Collection<?>) value;
            Iterator<?> it = c.iterator();
            while (it.hasNext()) {
                Object collectionObj = it.next();
                newCollection.add(deepClone(collectionObj));
            }
            field.set(objDes, newCollection);
            return;
        }
        if (value instanceof Map<?, ?>) {
            Map newMap = (Map) value.getClass().newInstance();
            Map<?, ?> m = (Map<?, ?>) value;
            Set<?> set = m.entrySet();
            for (Object o : set) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object mapVal = entry.getValue();
                newMap.put(entry.getKey(), deepClone(mapVal));
            }
            field.set(objDes, newMap);
            return;
        }
        // 是否jdk类型或基础类型
        if (isJDKType(field.get(objSource).getClass())
                || field.getClass().isPrimitive()
                || isStaticType(field)
                || value instanceof Enum<?>) {
            if ("java.lang.String".equals(value.getClass().getName())) {
                field.set(objDes, value);
            } else {
                field.set(objDes, field.get(objSource));
            }
            return;
        }
        if (value.getClass().isEnum()) {
            field.set(objDes, field.get(objSource));
            return;
        }
        // 是否自定义类
        if (isUserDefinedType(value.getClass())) {
            field.set(objDes, deepClone(value));
        }
    }


    /**
     * 是否静态变量
     *
     * @param field field
     * @return boolean
     */
    private static boolean isStaticType(Field field) {
        return field.getModifiers() == Modifier.STATIC;
    }

    private static boolean isStaticFinal(Field field) {
        return Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers());
    }

    /**
     * 是否jdk类型变量
     *
     * @param clazz clazz
     * @return boolean
     * @throws IllegalAccessException
     */
    private static boolean isJDKType(Class clazz) throws IllegalAccessException {
        return StringUtils.startsWith(clazz.getPackage().getName(), "javax.")
                || StringUtils.startsWith(clazz.getPackage().getName(), "java.")
                || StringUtils.startsWith(clazz.getName(), "javax.")
                || StringUtils.startsWith(clazz.getName(), "java.");
    }

    /**
     * 是否用户自定义类型
     *
     * @param clazz clazz
     * @return boolean
     */
    private static boolean isUserDefinedType(Class<?> clazz) {
        return clazz.getPackage() != null
                && !StringUtils.startsWith(clazz.getPackage().getName(), "javax.")
                && !StringUtils.startsWith(clazz.getPackage().getName(), "java.")
                && !StringUtils.startsWith(clazz.getName(), "javax.")
                && !StringUtils.startsWith(clazz.getName(), "java.");
    }

    /**
     * 获取包括父类所有的属性
     *
     * @param objSource objSource
     * @return result
     */
    public static Field[] getAllFields(Object objSource) {
        // 获得当前类的所有属性(private、protected、public)
        List<Field> fieldList = new ArrayList<>();
        Class tempClass = objSource.getClass();
        while (tempClass != null && !tempClass.getName().equalsIgnoreCase("java.lang.object")) {
            // 当父类为null的时候说明到达了最上层的父类(Object类).
            fieldList.addAll(Arrays.asList(tempClass.getDeclaredFields()));
            // 得到父类,然后赋给自己
            tempClass = tempClass.getSuperclass();
        }
        Field[] fields = new Field[fieldList.size()];
        fieldList.toArray(fields);
        return fields;
    }

    public static <T> T parseObject(T obj, Class<T> clazz) {
        if (!desensitize) {
            return obj;
        }
        return JSONObject.parseObject(DesensitizedUtils.getJson(obj), clazz);
    }
}
