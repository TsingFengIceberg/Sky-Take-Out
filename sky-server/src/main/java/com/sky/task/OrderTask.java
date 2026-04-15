package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时未支付订单
     * 每分钟执行一次
     */
    //@Scheduled(cron = "0/5 * * * * ?")
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        log.info("⏰ 开始定时处理超时未支付订单：{}", LocalDateTime.now());

        // 1. 查询：状态为“待付款(1)” 且 下单时间超过 15 分钟的订单
        // select * from orders where status = 1 and order_time < (当前时间 - 15分钟)
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
                log.info("🚫 订单 {} 已超时，系统已自动取消", orders.getId());
            }
        }
    }

    /**
     * 处理一直处于派送中状态的订单
     * 每天凌晨 1 点执行一次
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder() {
        log.info("⏰ 开始定时处理由于用户忘点确认导致的派送中订单：{}", LocalDateTime.now());

        // 查询：状态为“派送中(4)” 且 下单时间是上个工作周期的（比如前一天）
        LocalDateTime time = LocalDateTime.now().plusHours(-1); // 为了方便测试，可以设置成1小时
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED); // 自动改为已完成
                orderMapper.update(orders);
                log.info("✅ 订单 {} 已自动完成", orders.getId());
            }
        }
    }
}