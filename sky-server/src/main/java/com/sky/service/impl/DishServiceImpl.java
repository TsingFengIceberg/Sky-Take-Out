package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
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

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        // 1. 开始分页
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());

        // 2. 调用 Mapper 查询 (注意：这里返回的是 DishVO，因为里面包含了分类名称 categoryName)
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);

        // 3. 封装并返回
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Autowired
    private SetmealDishMapper setmealDishMapper; // 需要引入套餐菜品关联表的 Mapper

    /**
     * 菜品批量删除
     * @param ids
     */
    @Override
    @Transactional // 涉及多张表的数据修改，必须加事务保护！
    public void deleteBatch(List<Long> ids) {
        // 1. 判断当前菜品是否能够删除 —— 是否有起售中的菜品？
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id); // 这个方法可能你之前还没写，一会补上
            if (dish.getStatus() == StatusConstant.ENABLE) {
                // 当前菜品处于起售中，抛出业务异常
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        // 2. 判断当前菜品是否能够删除 —— 是否被某个套餐关联了？
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0) {
            // 查到了有套餐关联，抛出业务异常
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 3. 校验通过，开始删除菜品表中的数据
        for (Long id : ids) {
            dishMapper.deleteById(id);
            // 4. 连根拔起：删除菜品关联的口味数据
            dishFlavorMapper.deleteByDishId(id);
        }
    }


    /**
     * 根据id查询菜品和对应的口味数据
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 1. 根据id查询菜品基本数据
        Dish dish = dishMapper.getById(id); // 这个方法你在写删除的时候已经写过了！

        // 2. 根据菜品id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        // 3. 将查询到的数据封装到 VO 中
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO); // 属性拷贝神器
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 根据id修改菜品基本信息和对应的口味信息
     * @param dishDTO
     */
    @Override
    @Transactional // 涉及多表，必须开启事务
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        // 1. 修改菜品表基本信息
        dishMapper.update(dish);

        // 2. 删除原有的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        // 3. 重新插入新的口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            // 批量插入
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 条件查询菜品
     * @param dish
     * @return
     */
    @Override
    public List<Dish> list(Dish dish) {
        return dishMapper.list(dish);
    }

}