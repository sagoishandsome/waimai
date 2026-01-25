package com.sky.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.BaseException;
import com.sky.mapper.*;
import com.sky.utils.HttpClientUtil;
import com.sky.websocket.WebSocketServer;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
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
    private  SetmealDishMapper setmealDishMapper;

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

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private DefaultRedisScript<Long> stockLuaScript;

    public OrderSubmitVO handleOrderBlock(OrdersSubmitDTO ordersSubmitDTO, BlockException ex) {
        log.warn("触发流控限制，请求被拦截：{}", ex.getMessage());
        // 抛出一个自定义异常，让 GlobalExceptionHandler 捕获并返回“前方拥堵，请稍后再试”
        throw new BaseException("系统繁忙，外卖小哥正在拼命奔跑中，请稍后再试！");
    }


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    @SentinelResource(value = "submitOrder", blockHandler = "handleOrderBlock")
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 1. 地址校验（同步强一致）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 2. 查询当前用户购物车（DB 仍然是真实来源）
        Long userId = BaseContext.getCurrentId();

        ShoppingCart query = new ShoppingCart();
        query.setUserId(userId);
        List<ShoppingCart> cartList = shoppingCartMapper.list(query);

        if (cartList == null || cartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 3. 准备数据
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>(); // 明确使用 String

        for (ShoppingCart item : cartList) {
            if (item.getDishId() != null) {
                keys.add("sky:stock:dish:" + item.getDishId());
                args.add(item.getNumber().toString()); // 转成字符串 "2"
            } else if (item.getSetmealId() != null) {
                List<SetmealDish> dishList = setmealDishMapper.getBysetmealId(item.getSetmealId());
                for (SetmealDish sd : dishList) {
                    keys.add("sky:stock:dish:" + sd.getDishId());
                    // 计算套餐内菜品总量
                    Integer totalNeed = sd.getCopies() * item.getNumber();
                    args.add(totalNeed.toString()); // 转成字符串
                }
            }
        }

// 4. 执行脚本
// 注意：args.toArray() 必须确保传给的是序列化友好的格式
        Long result = stringRedisTemplate.execute(
                stockLuaScript,
                keys,
                args.toArray(new String[0]) // 确保转成 String 数组
        );


        if (result == null || result < 0) {
            throw new BaseException("部分商品库存不足，下单失败");
        }
        // 4. 生成订单号
        String orderNumber = System.currentTimeMillis() + "_" + userId;

        // 4.1 同步插入订单表（主表）
        // 4.1 完善订单对象赋值
        Orders orders = Orders.builder()
                .number(orderNumber)
                .userId(userId)
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .payMethod(ordersSubmitDTO.getPayMethod())
                .amount(ordersSubmitDTO.getAmount())
                .orderTime(LocalDateTime.now())

                .phone(addressBook.getPhone())
                .address(addressBook.getDetail())
                .consignee(addressBook.getConsignee())
                .addressBookId(ordersSubmitDTO.getAddressBookId())
                .remark(ordersSubmitDTO.getRemark())
                .estimatedDeliveryTime(ordersSubmitDTO.getEstimatedDeliveryTime())
                .deliveryStatus(ordersSubmitDTO.getDeliveryStatus())
                .packAmount(ordersSubmitDTO.getPackAmount())
                .tablewareNumber(ordersSubmitDTO.getTablewareNumber())
                .tablewareStatus(ordersSubmitDTO.getTablewareStatus())
                .build();

        orderMapper.insert(orders);






        // 5. 构建下单消息（异步消费）
        OrderMsgDTO msg = OrderMsgDTO.builder()
                .orderId(orders.getId()) // 带着主键 ID 过去
                .orderNumber(orderNumber).userId(userId).cartList(cartList)
                .amount(ordersSubmitDTO.getAmount()).address(addressBook.getDetail())
                .build();
        rocketMQTemplate.convertAndSend("SKY_ORDER_TOPIC", msg);

        // 6. 返回 VO
        return OrderSubmitVO.builder().id(orders.getId())
                .orderNumber(orderNumber)
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();
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

        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new BaseException("订单不存在：" + outTradeNo);
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        Map<String, Object> map = new HashMap<>();
        map.put("type", 1);
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号: " + outTradeNo);
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
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

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders>page=orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO>orderVOList=getOrderVOList(page);
        return new PageResult(page.getTotal(),orderVOList);


    }



    private List<OrderVO> getOrderVOList(Page<Orders> page) {
    List<OrderVO>orderVOList=new ArrayList<>();
    List<Orders>ordersList=page.getResult();
    if(!CollectionUtils.isEmpty(ordersList)){
        for(Orders orders:ordersList){
            OrderVO orderVO=new OrderVO();
            BeanUtils.copyProperties(orders,orderVO);
            String orderDishes=getdishes(orders);
            orderVO.setOrderDishes(orderDishes);
            orderVOList.add(orderVO);
        }
    }
return orderVOList;
    }

    private String getdishes(Orders orders) {
        List<OrderDetail>orderDetailList=orderDetailMapper.getByOrderId(orders.getId());
        List<String>orderDishList=orderDetailList.stream().map(x->{
            String orderDish=x.getName()+"*"+x.getNumber()+";";
            return orderDish;
        }).collect(Collectors.toList());
        return String.join("",orderDishList);
    }

    @Override
    public OrderStatisticsVO statistics() {
        Integer tobeconfirmed=orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed=orderMapper.countStatus(Orders.CONFIRMED);
        Integer DeliverryInProgress=orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        OrderStatisticsVO orderStatisticsVO=new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(tobeconfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(DeliverryInProgress);

        return orderStatisticsVO;
    }

    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders=Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders ordersdb=orderMapper.getById(ordersRejectionDTO.getId());
        if(ordersdb==null||!(ordersdb.getStatus()).equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Integer Paystatus=ordersdb.getPayStatus();
        if(Paystatus==Orders.PAID){
            log.info("退款成功，订单号码{}",ordersdb.getNumber());
        }
        Orders orders=new Orders();
        orders.setId(ordersdb.getId());
        orders.setCancelTime(LocalDateTime.now());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orderMapper.update(orders);

    }

    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {

        Orders ordersdb=orderMapper.getById(ordersCancelDTO.getId());

        Integer Paystatus=ordersdb.getPayStatus();
        if(Paystatus==Orders.PAID){
            log.info("退款成功，订单号码{}",ordersdb.getNumber());
        }
        Orders orders=new Orders();
        orders.setId(ordersdb.getId());
        orders.setCancelTime(LocalDateTime.now());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersCancelDTO.getCancelReason());
        orderMapper.update(orders);


    }

    @Override
    public void delivery(Long id) {
        Orders ordersdb=orderMapper.getById(id);
        if(ordersdb==null||!ordersdb.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders=new Orders();
        orders.setId(ordersdb.getId());
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    @Override
    public void complete(Long id) {
        Orders ordersdb=orderMapper.getById(id);
        if(ordersdb==null||!ordersdb.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders=new Orders();
        orders.setId(ordersdb.getId());
        orders.setStatus(Orders.COMPLETED);

        orderMapper.update(orders);
    }

    @Override
    public void reminder(Long id) {
        Orders ordersdb=orderMapper.getById(id);
        if(ordersdb==null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map=new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","订单号"+ordersdb.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 500000000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }
}
