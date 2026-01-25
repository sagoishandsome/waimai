package com.sky.Message;

import com.sky.dto.OrderMsgDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.SetmealDish;
import com.sky.mapper.*;
import com.sky.entity.ShoppingCart;
import com.sky.constant.StatusConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(topic = "SKY_ORDER_TOPIC", consumerGroup = "sky_order_group")
@Slf4j
public class OrderMsgListener implements RocketMQListener<OrderMsgDTO> {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;


    @Override
    @Transactional
    public void onMessage(OrderMsgDTO msg) {
        if (orderDetailMapper.getByOrderId(msg.getOrderId()).size() > 0) return;
        log.info("开始异步处理订单入库：{}", msg.getOrderNumber());




        // 2. 批量插入 OrderDetail (补全数据)
        List<OrderDetail> detailList = msg.getCartList().stream().map(cart -> {
            OrderDetail detail = new OrderDetail();
            BeanUtils.copyProperties(cart, detail, "id");
            detail.setOrderId(msg.getOrderId()); // 使用主表 ID
            return detail;
        }).collect(Collectors.toList());
        orderDetailMapper.insertBatch(detailList);

        // 3. 扣数据库库存（必须做）
        for (ShoppingCart cart : msg.getCartList()) {

            // 单品：直接扣 dish 库存
            if (cart.getDishId() != null) {
                int rows = dishMapper.decrStock(cart.getDishId(), cart.getNumber());
                if (rows <= 0) {
                    throw new RuntimeException(cart.getName() + " 数据库库存不足");
                }
                continue;
            }

            // 套餐：拆套餐扣每个 dish 库存
            if (cart.getSetmealId() != null) {
                List<SetmealDish> dishList = setmealDishMapper.getBysetmealId(cart.getSetmealId());

                for (SetmealDish sd : dishList) {
                    int need = sd.getCopies() * cart.getNumber();
                    int rows = dishMapper.decrStock(sd.getDishId(), need);
                    if (rows <= 0) {
                        throw new RuntimeException(sd.getName() + " 数据库库存不足");
                    }
                }
            }
        }


        // 3. 清空购物车（按 userId）
    shoppingCartMapper.deleteByUserId(msg.getUserId());
        log.info("异步任务处理完成：{}", msg.getOrderNumber());
    }
}
