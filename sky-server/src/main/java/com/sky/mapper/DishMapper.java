package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    @Select("select * from  dish where id =#{id}")
    Dish getById(Long id);
 @Delete("delete from dish where id =#{id}")
    void deleteById(Long id);

    void deleteByIds(List<Long> ids);

    @AutoFill(value = OperationType.UPDATE)
    void update(Dish dish);

    List<Dish> list(Dish dish);

    @Select("select * from dish left join setmeal_dish sd on dish.id = sd.dish_id where setmeal_id=#{id}")
    List<Dish> getBySetmealId(Long id);

    @Select("select id, stock from dish")
    List<Dish> listStock();

    @Select("select stock from dish where id = #{id}")
    Integer getStockById(Long id);

    @Update("update dish set stock = #{stock} where id = #{id}")
    void updateStock(@Param("id") Long id, @Param("stock") Integer stock);

    @Update("update dish set stock = stock - #{num} where id = #{dishId} and stock >= #{num}")
    int decrStock(@Param("dishId") Long dishId, @Param("num") Integer num);

    /**
     * 查询所有在售菜品的库存信息
     * @return
     */
    @Select("select id, name, stock from dish where status = 1")
    List<Dish> listaliveStock();
}
