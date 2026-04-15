package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-订单接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit") // 👈 就是这个大门！对应前端报404的路径
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);

        // 调用你之前辛辛苦苦写好的下单 Service 逻辑
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);

        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付 (伪造微信支付秒过)
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);

        // 🚨 核心逻辑：直接调用咱们自己写的“支付成功”方法，改数据库状态
        orderService.paySuccess(ordersPaymentDTO.getOrderNumber());

        // 🎁 伪造一个微信支付的返回对象给前端，前端只要拿到这些参数，就会乖乖跳到成功页
        OrderPaymentVO vo = new OrderPaymentVO();
        vo.setNonceStr("mock-nonce-str-666");
        vo.setPaySign("mock-pay-sign-888");
        vo.setPackageStr("prepay_id=mock-package-id");
        vo.setSignType("RSA");
        vo.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));

        return Result.success(vo);
    }

    /**
     * 历史订单查询
     *
     * @param page
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单查询")
    public Result<PageResult> page(int page, int pageSize, Integer status) {
        log.info("历史订单查询：page={}, pageSize={}, status={}", page, pageSize, status);
        PageResult pageResult = orderService.pageQuery4User(page, pageSize, status);
        return Result.success(pageResult);
    }


    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        log.info("查询订单详情，id：{}", id);
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }


    /**
     * 用户取消订单
     *
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result userCancelById(@PathVariable("id") Long id) throws Exception {
        log.info("用户取消订单，id：{}", id);
        orderService.userCancelById(id);
        return Result.success();
    }

}