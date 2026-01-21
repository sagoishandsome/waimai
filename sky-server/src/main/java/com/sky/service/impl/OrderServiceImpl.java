package com.sky.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.sky.mapper.*;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
 import com.sky.entity.User;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
//import com.sky.websocket.WebSocketServer;

import lombok.extern.slf4j.Slf4j;

// import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

     @Autowired
     private UserMapper userMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 业务异常处理，地址为空、购物车为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            // 抛出异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> cartList = shoppingCartMapper.list(shoppingCart);
        if (cartList == null || cartList.isEmpty()) {
            // 抛出异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        // 订单详情表插入多条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        cartList.forEach(cart -> {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        });
        orderDetailMapper.insertBatch(orderDetailList);

        // 清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 返回 VO
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
         Long userId = BaseContext.getCurrentId();
         User user = userMapper.getById(userId);

        // 直接调用paySuccess方法，模拟支付成功
        paySuccess(ordersPaymentDTO.getOrderNumber());

//        // 调用微信支付接口，生成预支付交易单
//         JSONObject jsonObject = weChatPayUtil.pay(
//         ordersPaymentDTO.getOrderNumber(), // 商户订单号
//         new BigDecimal(0.01), // 支付金额，单位 元
//         "苍穹外卖订单", // 商品描述
//         user.getOpenid() // 微信用户的openid
//         );
//
//         if (jsonObject.getString("code") != null &&
//         jsonObject.getString("code").equals("ORDERPAID")) {
//         throw new OrderBusinessException("该订单已支付");
//         }
//
//         OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
//         vo.setPackageStr(jsonObject.getString("package"));
        OrderPaymentVO vo = OrderPaymentVO.builder()
                .nonceStr("mock_nonce_str")  // 随机字符串
                .paySign("mock_pay_sign")    // 签名
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000)) // 时间戳
                .signType("RSA")             // 签名算法
                .packageStr("prepay_id=mock_123456") // 预支付会话标识
                .build();

        return vo;


    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 通过websocket通知商家
        Map<String, Object> map = new HashMap<>();
        map.put("type", 1); // 1:来单通知
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号: " + outTradeNo);
        String msg = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(msg);
    }

    /**
     * 查询历史订单
     *
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQueryByUser(int page, int pageSize, Integer status) {
        // 设置分页
        PageHelper.startPage(page, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> pageOrders = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList<>();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (pageOrders != null && pageOrders.getTotal() > 0) {
            for (Orders orders : pageOrders) {
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        long total = Optional.ofNullable(pageOrders).map(Page::getTotal).orElse(0L);
        return new PageResult(total, list);
    }

    @Override
    public OrderVO details(Long id) {

    OrderVO orderVO=new OrderVO();
    Orders orders=orderMapper.getById(id);
    List<OrderDetail>orderDetailList=orderDetailMapper.getByOrderId(id);
    BeanUtils.copyProperties(orders,orderVO);
    orderVO.setOrderDetailList(orderDetailList);
    return orderVO;



    }

    @Override
    public void UsercancelByID(Long id) {
    Orders ordersdb=orderMapper.getById(id);
    if(ordersdb==null)throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
    if(ordersdb.getStatus()>2)throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
    Orders orders=new Orders();
    orders.setId(ordersdb.getId());
    if(ordersdb.getStatus()==Orders.TO_BE_CONFIRMED){
        orders.setStatus(Orders.REFUND);
    }
    orders.setStatus(Orders.CANCELLED);
    orders.setCancelReason("用户取消");
    orders.setCancelTime(LocalDateTime.now());
    orderMapper.update(orders);
    }

    @Override
    public void repetition(Long id) {
        Long userId= BaseContext.getCurrentId();
        List<OrderDetail>orderDetailList=orderDetailMapper.getByOrderId(id);
        List<ShoppingCart>shoppingCartList=orderDetailList.stream().map(x->{
            ShoppingCart shoppingCart=new ShoppingCart();
            BeanUtils.copyProperties(x,shoppingCart,"id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        shoppingCartMapper.insertBatch(shoppingCartList);
    }


}
