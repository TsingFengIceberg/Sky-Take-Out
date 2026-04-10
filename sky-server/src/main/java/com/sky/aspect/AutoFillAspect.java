package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面，实现公共字段自动填充处理逻辑
 */
@Aspect     // 告诉 Spring，我是一个切面类（机器人）
@Component  // 把这个机器人交给 Spring 容器管理
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点：规定机器人的“工作范围”
     * 规则：只拦截 mapper 包下的所有方法，并且这些方法上必须盖有 @AutoFill 这个印章
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut(){}

    /**
     * 前置通知：在真正的 SQL 执行前，机器人先动手塞数据
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint){
        log.info("开始进行公共字段自动填充...");

        // 1. 获取到当前被拦截的方法上的操作类型 (到底是 INSERT 还是 UPDATE？)
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 获取方法签名
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class); // 拿到方法上的印章
        OperationType operationType = autoFill.value(); // 拿到印章里写的类型

        // 2. 获取到当前被拦截的方法的参数 (约定：我们要填的实体类对象永远放在第一个参数)
        Object[] args = joinPoint.getArgs();
        if(args == null || args.length == 0){
            return; // 如果方法没有参数，直接走人
        }
        Object entity = args[0]; // 拿到这个实体对象 (可能是 Employee，也可能是 Category)

        // 3. 准备要塞进去的数据：当前时间和当前登录人ID
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        // 4. 根据当前不同的操作类型，通过【反射】强行给属性赋值
        if(operationType == OperationType.INSERT){
            // 如果是新增，4个字段全填
            try {
                // 拿到对应实体的 set 方法 (AutoFillConstant 里面已经写好了常量字符串，比如 "setCreateTime")
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                // 强行执行这些 set 方法，把数据塞进去
                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else if(operationType == OperationType.UPDATE){
            // 如果是修改，只填 2 个修改相关的字段
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}