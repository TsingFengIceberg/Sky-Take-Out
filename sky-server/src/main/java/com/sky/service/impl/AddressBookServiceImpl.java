package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询地址
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    @Override
    public void save(AddressBook addressBook) {
        // 1. 获取当前微信用户的id
        Long userId = BaseContext.getCurrentId();
        // 2. 将用户id补充到地址对象中
        addressBook.setUserId(userId);
        // 3. 新增的地址默认设为非默认地址（0：否，1：是）
        addressBook.setIsDefault(0);

        // 4. 插入数据库
        addressBookMapper.insert(addressBook);
    }


    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional // 🚨 核心：必须加事务保证这两步要么全成功，要么全失败
    @Override
    public void setDefault(AddressBook addressBook) {
        // 1. 将当前用户的所有地址修改为非默认地址 (is_default = 0)
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        // 2. 将当前选中的地址修改为默认地址 (is_default = 1)
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

}
