package com.sky.annotation;

import com.sky.enumeration.OperationType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于标识某个方法需要进行功能字段自动填充处理
 */
@Target(ElementType.METHOD) // 贴在方法上的印章
@Retention(RetentionPolicy.RUNTIME) // 这个印章在代码运行期间一直保留
public @interface AutoFill {

    // ⚠️ 你的报错就是因为系统找不到下面这一行代码！现在把它补上就好了！
    OperationType value();

}