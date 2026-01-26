package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.config.DeepSeekClient;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.ShoppingCart;
import com.sky.exception.BaseException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.AddressBookService;
import com.sky.service.AiOrderService;
import com.sky.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AiOrderServiceImpl implements AiOrderService {

    @Autowired
    private DeepSeekClient deepSeekClient;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookService addressBookService;
    @Autowired
    private OrderService orderService;

    private final String systemPrompt = "你是一个外卖助手。请解析需求并返回 JSON 格式：{\"dishName\":\"菜名\", \"addressBookId\":null, \"payMethod\":1, \"remark\":\"备注\"}";

    @Override
    @Transactional // 保证 AI 动作的原子性
    public String processAiOrder(String userText) {
        // 1. 调用 AI 解析意图
        String aiJson = deepSeekClient.chat(systemPrompt, userText);
        log.info("AI 解析结果: {}", aiJson);
        if (aiJson == null) throw new BaseException("AI 解析失败");

        JSONObject json = JSON.parseObject(aiJson);
        String dishName = json.getString("dishName");

        // 2. 自动填装购物车
        String realDishName = this.autoFillCart(dishName);

        // 3. 构建并补全下单 DTO
        OrdersSubmitDTO dto = JSON.parseObject(aiJson, OrdersSubmitDTO.class);
        this.enrichAndValidateOrder(dto);

        // 4. 调用原生下单业务
        orderService.submitOrder(dto);

        return realDishName;
    }

    /**
     * 内部逻辑：搜索菜品并插入购物车
     */
    private String autoFillCart(String dishName) {
        if (dishName == null) throw new BaseException("未能识别到菜品名称");

        List<Dish> dishes = dishMapper.getByLikeName(dishName.trim());
        if (dishes == null || dishes.isEmpty()) {
            throw new BaseException("餐厅没有找到菜品：" + dishName);
        }

        Dish target = dishes.get(0);
        ShoppingCart cart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .dishId(target.getId())
                .name(target.getName())
                .image(target.getImage())
                .amount(target.getPrice())
                .number(1)
                .createTime(LocalDateTime.now())
                .build();

        shoppingCartMapper.insert(cart);
        return target.getName();
    }

    /**
     * 内部逻辑：补全地址、包装费等必填字段
     */
    private void enrichAndValidateOrder(OrdersSubmitDTO dto) {
        // 地址修正
        AddressBook addressSearch = new AddressBook();
        addressSearch.setUserId(BaseContext.getCurrentId());
        List<AddressBook> addressList = addressBookService.list(addressSearch);

        if (addressList == null || addressList.isEmpty()) {
            throw new BaseException("请先添加收货地址");
        }

        // 校验或匹配地址 ID
        boolean exists = addressList.stream().anyMatch(a -> a.getId().equals(dto.getAddressBookId()));
        if (!exists) {
            AddressBook defaultAddr = addressList.stream()
                    .filter(a -> a.getIsDefault() == 1)
                    .findFirst()
                    .orElse(addressList.get(0));
            dto.setAddressBookId(defaultAddr.getId());
        }

        // 补全订单必填项（修复 NPE）
        dto.setPackAmount(0);
        dto.setTablewareNumber(1);
        dto.setTablewareStatus(1);
        dto.setDeliveryStatus(1);
        if (dto.getAmount() == null) dto.setAmount(new BigDecimal("0"));
    }
}