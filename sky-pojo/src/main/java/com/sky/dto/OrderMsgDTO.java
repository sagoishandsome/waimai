package com.sky.dto;

import com.sky.entity.ShoppingCart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMsgDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long orderId;
    // 订单基础信息
    private String orderNumber;      // 预生成的订单号
    private Long userId;             // 用户ID
    private Long addressBookId;      // 地址ID
    private String address;          // 详细地址快照（防止地址簿被删）
    private String consignee;        // 收货人
    private String phone;            // 手机号

    // 支付相关
    private BigDecimal amount;       // 订单金额
    private Integer payMethod;       // 支付方式

    // 核心快照信息
    private List<ShoppingCart> cartList; // 购物车商品快照（非常重要！）

    // 其他备注
    private String remark;           // 备注
}