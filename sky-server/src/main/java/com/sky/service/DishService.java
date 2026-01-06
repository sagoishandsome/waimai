package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public interface DishService {

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
    @Select("select * from dish where id=#{id}")
    void deleteBatch(List<Long> ids);
}
