package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.annotation.AutoFill;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {
@Autowired
private SetmealDishMapper setmealDishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private DishMapper dishMapper;
    @Override
    public void save(SetmealDTO setmealDTO) {
        Setmeal setmeal=new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.insert(setmeal);
        Long setmealId=setmeal.getId();
        List<SetmealDish>setmealDishes=setmealDTO.getSetmealDishes();
        if(setmealDishes!=null&&setmealDishes.size()>0){
            setmealDishes.forEach(setmealDish -> {setmealDish.setSetmealId(setmealId);});
            setmealDishMapper.insertbatch(setmealDishes);
        }


    }

    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO>page=setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());


    }

    @Override
    public void deleteBatch(List<Long> ids) {

for (Long id:ids){
    Setmeal setmeal=setmealMapper.getByid(id);
    if(setmeal.getStatus()== StatusConstant.ENABLE)
    {
        throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
    }
}
ids.forEach(id->{
    setmealMapper.deleteById(id);
    setmealDishMapper.deleteById(id);
});

    }

    @Override
    public SetmealVO getById(Long id) {
        Setmeal setmeal=setmealMapper.getByid(id);
List<SetmealDish>setmealDishes=setmealDishMapper.getBysetmealId(id);
SetmealVO setmealVO=new SetmealVO();
BeanUtils.copyProperties(setmeal,setmealVO);
setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    @Override
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal=new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.update(setmeal);
        setmealDishMapper.deleteById(setmealDTO.getId());
        List<SetmealDish>setmealDishes=setmealDTO.getSetmealDishes();
        if(setmealDishes!=null&&setmealDishes.size()>0){
            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(setmealDTO.getId());
            });
            setmealDishMapper.insertbatch(setmealDishes);
        }
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        if(status==StatusConstant.ENABLE){
            List<Dish>dishes=dishMapper.getBySetmealId(id);
            if(dishes!=null&&dishes.size()>0){
                dishes.forEach(dish -> {
                    if(dish.getStatus()==StatusConstant.DISABLE)
                        throw new  SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                });
            }
        }
        Setmeal setmeal=Setmeal.builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.update(setmeal);


    }
}
