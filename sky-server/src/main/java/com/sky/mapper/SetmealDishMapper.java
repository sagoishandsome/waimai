package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealDishMapper {
    /**
     * 根据菜品idc查询套餐id
     * @param dishIds
     * @return
     */
    List<Long>getSetmealIdsByDishIds(List<Long>dishIds);

    void insertbatch(List<SetmealDish> setmealDishes);
@Delete("delete from setmeal_dish where setmeal_id=#{id}")
    void deleteById(Long id);
@Select("select * from setmeal_dish where setmeal_id=#{id}")
    List<SetmealDish> getBysetmealId(Long id);
}
