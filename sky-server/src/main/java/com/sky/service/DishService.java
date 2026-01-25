package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public interface DishService {


   void startOrStop(Integer status, Long id);

 /**
     * 新增菜品及口味数据
     * @param dishDTO
     */


    public void saveWithFlavor(DishDTO dishDTO);

    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     *根据主键查询
     * @param ids
     */

    void deleteBatch(List<Long> ids);

    DishVO getByIdWithFlavor(Long id);

    void updateWithFlavor(DishDTO dishDTO);

    List<Dish> list(Long categoyId);

   /**
    * 条件查询菜品和口味
    * @param dish
    * @return
    */
   List<DishVO> listWithFlavor(Dish dish);

    void initStockToRedis();
    Integer getStockFromRedis(Long dishId);
    void setStock(Long dishId, Integer stock);

    List<Dish> listStock();

}
