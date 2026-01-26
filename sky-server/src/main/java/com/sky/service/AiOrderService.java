package com.sky.service;



public interface AiOrderService {
    /**
     * AI 智能下单全流程
     * @param userText 用户原始话语
     * @return 匹配到的菜品名称
     */
    String processAiOrder(String userText);
}
