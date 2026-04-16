package com.sky.annotation;

import com.sky.enumeration.OperationType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于标识某个方法需要进行功能字段自动填充处理
 */
@Target(ElementType.METHOD) // 这里的 Target 表示这个注解只能加在方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时有效
public @interface AutoFill {
    // 数据库操作类型：UPDATE INSERT
    // 这里的 OperationType 是讲义自带的枚举类，你可以在 com.sky.enumeration 里找到它
    OperationType value();
}