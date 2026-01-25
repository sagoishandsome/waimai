package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j

@RestController
@RequestMapping("admin/dish")
@Api(tags="菜品相关接口")

public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;




    @PostMapping("/stock/init")
    public void initStock() {
        dishService.initStockToRedis();
    }

    @GetMapping("/stock/{dishId}")
    public Integer getStock(@PathVariable Long dishId) {
        return dishService.getStockFromRedis(dishId);
    }

    @PutMapping("/stock/{dishId}/{stock}")
    public void setStock(@PathVariable Long dishId, @PathVariable Integer stock) {
        dishService.setStock(dishId, stock);
    }

    @PostMapping
    @ApiOperation("新增菜品{}")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);


        String key="dish_"+dishDTO.getCategoryId();
        redisTemplate.delete(key);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult>page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品分页查询{}",dishPageQueryDTO);
       PageResult pageResult= dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping
    @ApiOperation("菜品批量删除")
    public Result delete(@RequestParam List<Long> ids){
        log.info("菜品批量删除",ids);
        dishService.deleteBatch(ids);


        //清理所有菜品缓存
        Set keys=redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品")
    public Result<DishVO>getById(@PathVariable Long id){
        log.info("根据id查询菜品{}",id);
        DishVO dishVO= dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }
@PutMapping
@ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("update dish",dishDTO);
        dishService.updateWithFlavor(dishDTO);
    Set keys=redisTemplate.keys("dish_*");
    redisTemplate.delete(keys);

        return Result.success();
    }
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<Dish>>getByCategoryId(@RequestParam Long categoryId){
        log.info("根据分类id查询菜品");
        List<Dish>list=dishService.list(categoryId);
        return Result.success(list);
    }

    @PostMapping("/status/{status}")
    @ApiOperation("start or stop sale")
    public Result<String> startOrStop(@PathVariable Integer status,Long id){
        log.info("start or stop{}{}",status ,id);
        dishService.startOrStop(status,id);

        Set keys=redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);
        return Result.success();
    }
}
