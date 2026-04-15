package com.sky.service.impl; // 👇 1. 补上它自己的户口本！必须在第一行！

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.*;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;       // 👇 2. 提前补上这两个 Mapper 的进口许可证
import com.sky.mapper.OrderDetailMapper; // 👇 3. 避免一会儿接着报错

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional // 🚨 核心：必须保证原子性！
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 1. 处理业务异常（地址簿为空、购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 2. 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID); // 未支付
        orders.setStatus(Orders.PENDING_PAYMENT); // 待付款
        orders.setNumber(String.valueOf(System.currentTimeMillis())); // 简单用时间戳当订单号
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        // 👇 补上这关键的一行！
        // 在苍穹外卖业务中，新订单的派送状态默认为“待付款/未开始”，通常给 0 或 1
        // 这里我们直接给它一个初始状态，防止数据库报非空约束错误
        orders.setDeliveryStatus(1);

        // 🚨 顺便检查一下：如果你数据库里 estimated_delivery_time 也是非空，
        orders.setEstimatedDeliveryTime(LocalDateTime.now().plusHours(1));

        orderMapper.insert(orders); // 🚨 这里插入后要记得主键回填！因为后面明细表要用到这个订单ID

        // 3. 向订单明细表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail(); // 订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId()); // 设置关联的订单ID
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList); // 批量插入提高效率

        // 4. 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 5. 封装VO结果并返回
        OrderSubmitVO vo = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return vo;
    }

    /**
     * 订单支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {
        // 根据前端传来的订单号，查出这个订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        if (ordersDB != null) {
            // 组装要修改的数据，把状态改成 2（待接单），支付状态改成 1（已支付）
            Orders orders = Orders.builder()
                    .id(ordersDB.getId())
                    .status(Orders.TO_BE_CONFIRMED)
                    .payStatus(Orders.PAID)
                    .checkoutTime(LocalDateTime.now())
                    .build();

            // 执行更新
            orderMapper.update(orders);
        }
    }


    /**
     * 用户端订单分页查询
     *
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int page, int pageSize, Integer status) {
        // 1. 开启 Mybatis 的 PageHelper 分页插件
        PageHelper.startPage(page, pageSize);

        // 2. 组装查询条件 (DTO)
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId()); // 🚨 只能查当前登录用户自己的订单！
        ordersPageQueryDTO.setStatus(status);

        // 3. 执行主表查询
        Page<Orders> pageQuery = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList<>();

        // 4. 查出订单列表后，遍历每一个订单，去查它对应的菜品明细
        if (pageQuery != null && pageQuery.getTotal() > 0) {
            for (Orders orders : pageQuery) {
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                // 将 Orders 的属性复制到 OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails); // 🚨 把查出来的明细塞进去

                list.add(orderVO);
            }
        }

        // 5. 封装成分页结果返回
        return new PageResult(pageQuery.getTotal(), list);
    }

    public OrderVO details(Long id) {
        // 1. 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 2. 查询该订单对应的菜品明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 3. 封装成VO返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        // 🚨 注意：这里用你之前在前端对齐的名字，建议统一叫 orderDetails
        // 在 OrderServiceImpl.java 里，把红色的那行改成：
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }


    /**
     * 订单搜索
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态需要返回菜品简信，将 Orders 转为 OrderVO
        List<OrderVO> orderVOList = page.getResult().stream().map(orders -> {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            // 封装订单菜品信息字符串（便于管理后台直接展示）
            String orderDishes = getOrderDishesStr(orders);
            orderVO.setOrderDishes(orderDishes);
            return orderVO;
        }).collect(Collectors.toList());

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各个状态订单数量统计
     */
    public OrderStatisticsVO statistics() {
        // 根据状态统计数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED) // 状态改为 3
                .build();
        orderMapper.update(orders);
    }

    /**
     * 派送订单
     */
    public void delivery(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        // 校验：只有状态为“已接单(3)”的订单才能派送
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS) // 状态改为 4
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成订单
     */
    public void complete(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        // 校验：只有状态为“派送中(4)”的订单才能完成
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED) // 状态改为 5
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    // 私有辅助方法：将订单详情拼接成字符串，如 "梅菜扣肉*1; 馒头*4"
    private String getOrderDishesStr(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            return x.getName() + "*" + x.getNumber() + ";";
        }).collect(Collectors.toList());
        return String.join("", orderDishList);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 2. 校验：只有订单存在且状态为“待接单(2)”才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 3. 状态流转（如果是已支付订单，此处理论上应调用微信退款接口，但在练习中通常跳过或模拟）
        // if (ordersDB.getPayStatus().equals(Orders.PAID)) { ... }

        // 4. 更新订单状态为“已取消(6)”，并记录拒单原因
        Orders orders = new Orders();
        orders.setId(ordersRejectionDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        // 2. 状态流转
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }


    /**
     * 用户取消订单
     */
    public void userCancelById(Long id) throws Exception {
        // 1. 查询订单状态
        Orders ordersDB = orderMapper.getById(id);

        // 2. 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 3. 校验订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        // 如果状态 > 2 (即商家已经接单或派送)，则不能直接取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 4. 更新订单状态为“已取消”
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户主动取消");
        orders.setCancelTime(LocalDateTime.now());

        // 如果是已支付(待接单)状态，这里理论上要调用微信退款接口
        // if (ordersDB.getPayStatus().equals(Orders.PAID)) { ... }

        orderMapper.update(orders);
    }


}