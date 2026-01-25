package com.sky.config;



import com.sky.service.DishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StockInitRunner implements CommandLineRunner {

    @Autowired
    private DishService dishService;

    @Override
    public void run(String... args) {
        dishService.initStockToRedis();
    }
}
