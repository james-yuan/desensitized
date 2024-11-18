package com.actulity.desensiti;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

public class UserTest {

    public static void main(String[] args) {
        User user = new User();
        user.setId(45454511);
        user.setName("张xxxx");
        user.setHomeIp("232.75.33.12");
        user.setYyy("第三方的科技管理的国家的怒然");
        User user1 = ObjectConvert.parseObject(user, User.class);
        System.out.println(JSONObject.toJSONString(user1));
        // https://github.com/Heiffeng/sensitive-util
        // https://github.com/chenhaiyangs/mybatis-encrypt-plugin
    }

    @Data
    static
    class User {

        @Desensitized(type = SensitiveTypeEnum.ID_CARD)
        private int id;


        @Desensitized(type = SensitiveTypeEnum.CHINESE_NAME)
        private String name;

        @Desensitized(type = SensitiveTypeEnum.MOBILE_PHONE)
        private String homeIp;

        @Desensitized(type = SensitiveTypeEnum.ADDRESS)
        private String yyy;
    }

}
