package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;

import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.entity.Employee;
import org.springframework.beans.BeanUtils;
import org.springframework.util.DigestUtils;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 密码比对
        // TODO 已解决：对前端传过来的明文密码进行 MD5 加密处理
        password = DigestUtils.md5DigestAsHex(password.getBytes());

        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }


    /**
     * 新增员工
     * @param employeeDTO
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        // 1. 创建实体对象 (数据库真正需要的格式)
        Employee employee = new Employee();

        // 2. 对象属性拷贝
        // 企业级规范：如果属性名一致，千万不要像 C++ 那样用 employee.setName(dto.getName()) 挨个去写。
        // 用 Spring 提供的工具类，一行代码自动把 DTO 里的数据复制到 Entity 里。
        BeanUtils.copyProperties(employeeDTO, employee);

        // 3. 补全后端特有的业务属性
        // 设置账号的状态，默认正常状态 (1表示正常 0表示锁定)
        employee.setStatus(StatusConstant.ENABLE); // StatusConstant 是项目里预设好的常量类

        // 设置密码，默认密码123456，且必须经过 MD5 加密
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));

        // 设置当前记录的创建时间和修改时间
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());

        // 设置当前记录创建人id和修改人id
        // (注：这里暂时写死为10L（L代表Long类型），因为我们还没讲到如何动态获取当前登录人的ID，后面再优化)
        // employee.setCreateUser(10L); （删掉或注释掉）
        // employee.setUpdateUser(10L); （删掉或注释掉）

        // 从 ThreadLocal 中动态获取当前登录人的 ID
        employee.setCreateUser(BaseContext.getCurrentId());
        employee.setUpdateUser(BaseContext.getCurrentId());

        // 4. 调用刚才写的 Mapper 存入数据库
        employeeMapper.insert(employee);
    }


    /**
     * 分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        // 1. 开始分页查询 (这行代码是 PageHelper 的核心，底层会利用 ThreadLocal 拦截下一次执行的 SQL 并自动拼接 limit)
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());

        // 2. 调用 Mapper 查数据库。注意返回值类型是 Page，它是 PageHelper 提供的一个继承了 ArrayList 的类
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        // 3. 封装出前端需要的数据格式 (总记录数, 当前页的数据集合)
        long total = page.getTotal();
        List<Employee> records = page.getResult();

        return new PageResult(total, records);
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        // 这是一个 update 操作：update employee set status = ? where id = ?
        // 按照企业规范，我们把要修改的字段封装成一个 Employee 实体类传给 Mapper
        // 这里用到了 @Builder 构建者模式，写起来非常优雅
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();

        employeeMapper.update(employee);
    }

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        // 企业级细节：出于安全考虑，密码绝对不能传回给前端，直接把它抹掉
        employee.setPassword("****");
        return employee;
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        // 1. 创建实体类
        Employee employee = new Employee();

        // 2. 将 DTO 的数据拷贝到实体类中
        BeanUtils.copyProperties(employeeDTO, employee);

        // 3. 补全更新时间和更新人
        employee.setUpdateTime(LocalDateTime.now());
        //employee.setUpdateUser(10L); // ⚠️ 注意这里：又出现了写死的 10L，一会咱们就干掉它！
        employee.setUpdateUser(BaseContext.getCurrentId());

        // 4. 调用 Mapper 进行更新
        employeeMapper.update(employee);
    }

}
