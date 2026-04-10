package com.sky.service.impl;

import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.DishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    /**
     * 新增菜品和对应的口味
     * @param dishDTO
     */
    @Transactional // 开启事务控制，保证多表操作的数据一致性
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        // 1. 向菜品表（dish）插入 1 条数据
        dishMapper.insert(dish);

        // ⚠️ 极其关键的一步：获取刚才 insert 语句自动生成的主键 ID
        Long dishId = dish.getId();

        // 2. 获取传过来的口味列表
        List<DishFlavor> flavors = dishDTO.getFlavors();

        // 3. 判断有没有填口味，如果填了，向口味表（dish_flavor）插入 N 条数据
        if (flavors != null && flavors.size() > 0) {
            // 遍历口味列表，给每一个口味都赋上刚才拿到的 dishId
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            // 调用批量插入方法
            dishFlavorMapper.insertBatch(flavors);
        }
    }
}