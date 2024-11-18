package com.actuality.desensiti;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class DesensitizedUtils {

    /**
     * 获取脱敏json串
     *
     * @param javaBean javaBean
     * @return result
     */
    public static String getJson(Object javaBean) {
        String json = null;
        if (null == javaBean) {
            return json;
        }
        try {
            if (javaBean.getClass().isInterface()) {
                return json;
            }
            Object clone = ObjectConvert.deepClone(javaBean);
            Set<Integer> referenceCounter = new HashSet<Integer>();
            /* 对克隆实体进行脱敏操作 */
            DesensitizedUtils.replace(ObjectConvert.getAllFields(clone), clone, referenceCounter);
            json = JSON.toJSONString(clone, SerializerFeature.WriteMapNullValue, SerializerFeature.WriteNullListAsEmpty);
            referenceCounter.clear();
        } catch (Throwable ex) {
            log.error("getJson error {}", ex.getMessage());
        }
        return json;
    }

    /**
     * 对需要脱敏的字段进行转化
     *
     * @param fields fields
     * @param javaBean javaBean
     * @param referenceCounter referenceCounter
     * @throws IllegalArgumentException IllegalArgumentException
     * @throws IllegalAccessException IllegalAccessException
     */
    private static void replace(Field[] fields, Object javaBean, Set<Integer> referenceCounter) throws IllegalArgumentException, IllegalAccessException {
        if (ObjectUtils.isEmpty(fields)) {
            return;
        }
        for (Field field : fields) {
            if (null == field || null == javaBean) {
                continue;
            }
            AccessController.doPrivileged((PrivilegedAction) () -> {
                field.setAccessible(true);
                return null;
            });
            Object value = field.get(javaBean);
            if (null == value) {
                continue;
            }
            Class<?> type = value.getClass();
            if (value instanceof Enum<?>) {
                continue;
            }
            if (type.isArray()) {
                processArray(referenceCounter, value);
            }
            if (value instanceof Collection<?>) {
                processCollection(referenceCounter, (Collection<?>) value);
            }
            if (value instanceof Map<?, ?>) {
                processMap(referenceCounter, (Map<?, ?>) value);
            }
            // 除基础类型、jdk类型的字段之外，对其他类型的字段进行递归过滤
            boolean param1 = !type.isPrimitive() && type.getPackage() != null;
            boolean param2 = !StringUtils.startsWith(type.getPackage().getName(), "javax.")
                    && !StringUtils.startsWith(type.getPackage().getName(), "java.")
                    && !StringUtils.startsWith(field.getType().getName(), "javax.")
                    && !StringUtils.startsWith(field.getName(), "java.");
            if (param1 && param2 && referenceCounter.add(value.hashCode())) {
                replace(ObjectConvert.getAllFields(value), value, referenceCounter);
            }
            // 脱敏操作
            setNewValueForField(javaBean, field, value);
        }
    }

    private static void processMap(Set<Integer> referenceCounter, Map<?, ?> value) throws IllegalAccessException {
        Set<?> set = value.entrySet();
        for (Object o : set) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Object mapVal = entry.getValue();
            if (isNotGeneralType(mapVal.getClass(), mapVal, referenceCounter)) {
                replace(ObjectConvert.getAllFields(mapVal), mapVal, referenceCounter);
            }
        }
    }

    private static void processCollection(Set<Integer> referenceCounter, Collection<?> value) throws IllegalAccessException {
        for (Object collectionObj : value) {
            if (isNotGeneralType(collectionObj.getClass(), collectionObj, referenceCounter)) {
                replace(ObjectConvert.getAllFields(collectionObj), collectionObj, referenceCounter);
            }
        }
    }

    private static void processArray(Set<Integer> referenceCounter, Object value) throws IllegalAccessException {
        int len = Array.getLength(value);
        for (int i = 0; i < len; i++) {
            Object arrayObject = Array.get(value, i);
            if (isNotGeneralType(arrayObject.getClass(), arrayObject, referenceCounter)) {
                replace(ObjectConvert.getAllFields(arrayObject), arrayObject, referenceCounter);
            }
        }
    }

    /**
     * 排除基础类型、jdk类型、枚举类型的字段
     *
     * @param clazz clazz
     * @param value value
     * @param referenceCounter referenceCounter
     * @return result
     */
    private static boolean isNotGeneralType(Class<?> clazz, Object value, Set<Integer> referenceCounter) {
        return !clazz.isPrimitive()
                && clazz.getPackage() != null
                && !clazz.isEnum()
                && !StringUtils.startsWith(clazz.getPackage().getName(), "javax.")
                && !StringUtils.startsWith(clazz.getPackage().getName(), "java.")
                && !StringUtils.startsWith(clazz.getName(), "javax.")
                && !StringUtils.startsWith(clazz.getName(), "java.")
                && referenceCounter.add(value.hashCode());
    }

    /**
     * 脱敏操作（按照规则转化需要脱敏的字段并设置新值）
     * 目前只支持String类型的字段，如需要其他类型如BigDecimal、Date等类型，可以添加
     *
     * @param javaBean javaBean
     * @param field field
     * @param value value
     * @throws IllegalAccessException IllegalAccessException
     */
    public static void setNewValueForField(Object javaBean, Field field, Object value) throws IllegalAccessException {
        Desensitized annotation = field.getAnnotation(Desensitized.class);
        if (null == value || null == annotation || !field.getType().equals(String.class)) {
            return;
        }
        // 处理自身的属性
        String valueStr = (String) value;
        switch (annotation.type()) {
            case CHINESE_NAME: {
                field.set(javaBean, DesensitizedUtils.chineseName(valueStr));
                break;
            }
            case ID_CARD: {
                field.set(javaBean, DesensitizedUtils.idCardNum(valueStr, 3, 2));
                break;
            }
            case FIXED_PHONE: {
                field.set(javaBean, DesensitizedUtils.fixedPhone(valueStr));
                break;
            }
            case MOBILE_PHONE: {
                field.set(javaBean, DesensitizedUtils.mobilePhone(valueStr));
                break;
            }
            case ADDRESS: {
                field.set(javaBean, DesensitizedUtils.address(valueStr, 9));
                break;
            }
            case EMAIL: {
                field.set(javaBean, DesensitizedUtils.email(valueStr));
                break;
            }
            case BANK_CARD: {
                field.set(javaBean, DesensitizedUtils.bankCard(valueStr));
                break;
            }
            case PASSWORD: {
                field.set(javaBean, DesensitizedUtils.password(valueStr));
                break;
            }
        }
    }

    /**
     * 【中文姓名】
     *
     * @param fullName fullName
     * @return result
     */
    public static String chineseName(String fullName) {
        if (StringUtils.isBlank(fullName)) {
            return "";
        }
        String name = StringUtils.left(fullName, 1);
        return StringUtils.rightPad(name, StringUtils.length(fullName), "*");
    }

    /**
     * 【身份证号】
     *
     * @param id id
     * @param pre pre
     * @param post post
     * @return result
     */
    public static String idCardNum(String id, int pre, int post) {
        if (StringUtils.isBlank(id) || id.length() <= pre + post) {
            return id;
        }
        // 只保留前三位
        String prefix = StringUtils.substring(id, 0, pre);
        // 只保留末尾3位
        String suffix = StringUtils.right(id, post);
        // 用"*"填充中间部分
        String middle = StringUtils.repeat("*", StringUtils.length(id) - pre - post);
        // 合并前三位、中间部分和末尾3位
        return prefix + middle + suffix;
    }

    /**
     * 【固定电话】
     *
     * @param num num
     * @return result
     */
    public static String fixedPhone(String num) {
        if (StringUtils.isBlank(num)) {
            return "";
        }
        return StringUtils.leftPad(StringUtils.right(num, 4), StringUtils.length(num), "*");
    }

    /**
     * 【手机号码】
     *
     * @param num num
     * @return result
     */
    public static String mobilePhone(String num) {
        if (StringUtils.isBlank(num)) {
            return "";
        }
        return StringUtils.left(num, 3).concat(StringUtils.removeStart(StringUtils.leftPad(StringUtils.right(num, 4), StringUtils.length(num), "*"), "***"));
    }

    /**
     * 【地址】
     *
     * @param address 地址
     * @param remain 只保留前几位
     * @return result
     */
    public static String address(String address, int remain) {
        if (StringUtils.isBlank(address) || address.length() <= remain) {
            return address;
        }
        return StringUtils.rightPad(StringUtils.substring(address, 0, remain), address.length(), "*");
    }

    /**
     * 【电子邮箱】
     *
     * @param email email
     * @return result
     */
    public static String email(String email) {
        if (StringUtils.isBlank(email)) {
            return "";
        }
        int index = StringUtils.indexOf(email, "@");
        if (index <= 1){
            return email;
        }
        return StringUtils.rightPad(StringUtils.left(email, 1), index, "*").concat(StringUtils.mid(email, index, StringUtils.length(email)));
    }

    /**
     * 【银行卡号】
     *
     * @param cardNum cardNum
     * @return result
     */
    public static String bankCard(String cardNum) {
        if (StringUtils.isBlank(cardNum)) {
            return "";
        }
        return StringUtils.left(cardNum, 6).concat(StringUtils.removeStart(StringUtils.leftPad(StringUtils.right(cardNum, 4), StringUtils.length(cardNum), "*"), "******"));
    }

    /**
     * 【密码】
     *
     * @param password password
     * @return result
     */
    public static String password(String password) {
        if (StringUtils.isBlank(password)) {
            return "";
        }
        String pwd = StringUtils.left(password, 0);
        return StringUtils.rightPad(pwd, StringUtils.length(password), "*");
    }

}
