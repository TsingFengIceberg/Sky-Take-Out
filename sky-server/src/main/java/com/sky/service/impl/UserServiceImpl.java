package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    // 微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        // 1. 调用微信接口服务，获得当前微信用户的 openid
        String openid = getOpenid(userLoginDTO.getCode());

        // 2. 判断 openid 是否为空，如果为空表示登录失败，抛出业务异常
        if (openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        // 3. 判断当前用户是否为新用户（去我们自己的 user 表里查）
        User user = userMapper.getByOpenid(openid);

        // 4. 如果是新用户，自动完成注册
        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user); // 插入新用户
        }

        // 5. 返回这个用户对象
        return user;
    }

//    /**
//     * 封装调用微信接口的方法
//     * @param code
//     * @return
//     */
//    private String getOpenid(String code) {
//        // 组装请求参数
//        Map<String, String> map = new HashMap<>();
//        map.put("appid", weChatProperties.getAppid());
//        map.put("secret", weChatProperties.getSecret());
//        map.put("js_code", code);
//        map.put("grant_type", "authorization_code");
//
//        // 发送 GET 请求
//        String json = HttpClientUtil.doGet(WX_LOGIN, map);
//
//        // 解析微信返回的 JSON 字符串
//        JSONObject jsonObject = JSON.parseObject(json);
//        return jsonObject.getString("openid");
//    }
    /**
     * 封装调用微信接口的方法
     * @param code
     * @return
     */
    private String getOpenid(String code) {
        // 🚨🚨🚨 测谎仪：看看 Java 到底读到了什么配置！ 🚨🚨🚨
        System.out.println("================= 核心测谎仪 =================");
        System.out.println("Java当前正在使用的 AppID: [" + weChatProperties.getAppid() + "]");
        System.out.println("Java当前正在使用的 Secret: [" + weChatProperties.getSecret() + "]");
        System.out.println("=============================================");

        Map<String, String> map = new HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");

        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        System.out.println("腾讯微信官方返回的真实报错数据：" + json);

        JSONObject jsonObject = JSON.parseObject(json);
        return jsonObject.getString("openid");
    }
}