package com.sky.controller.user;

import com.sky.config.DeepSeekClient;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import com.sky.service.impl.AiOrderServiceImpl;
import com.sky.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/user/ai")
public class AiController {


    @Autowired
    private AiOrderServiceImpl aiOrderService;



    // 定义好系统提示词
    @PostMapping("/order")
    public Result<String> aiOrder(@RequestBody String userText) {
        log.info("用户发起 AI 语音/文字下单: {}", userText);

        // Controller 变得极其清爽，只负责异常捕获和结果返回
        try {
            String dishName = aiOrderService.processAiOrder(userText);
            return Result.success("已为您成功下单：" + dishName);
        } catch (Exception e) {
            log.error("AI 下单失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}
