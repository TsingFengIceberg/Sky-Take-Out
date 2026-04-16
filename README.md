# 苍穹外卖 (Sky Take Out) - 企业级餐饮定制系统

## 📚 项目简介
本项目（苍穹外卖）是专门为餐饮企业定制的一款软件产品，采用前后端分离架构。
- **管理端**：提供给餐饮企业内部员工使用，包含分类、菜品、套餐、订单管理及多维度数据统计报表。
- **用户端**：提供给消费者使用，包含微信登录、商品浏览、购物车、微信支付、催单等功能。

---

## 🗂️ 核心业务模块演进 (基于讲义重构)

<details>
<summary><b>PART 1: 项目骨架与基础环境</b></summary>

- 完成前后端分离架构的搭建。
- 配置 Nginx 反向代理与负载均衡。
- 引入 Swagger (Knife4j) 生成在线接口文档。
- 采用 MD5 加密完善员工后台登录功能。
</details>

<details>
<summary><b>PART 2: 员工管理与分类管理</b></summary>

- 实现员工的 CRUD、分页查询（基于 `PageHelper`）及状态启禁用。
- 解决新增员工时由于用户名重复导致的 SQL 异常（通过 `GlobalExceptionHandler` 全局异常捕获处理）。
- 使用 `ThreadLocal` 结合 JWT 拦截器 (`JwtTokenAdminInterceptor`) 动态获取当前登录员工 ID。
- **关联代码**：[`GlobalExceptionHandler.java`](sky-server/src/main/java/com/sky/handler/GlobalExceptionHandler.java), [`BaseContext.java`](sky-common/src/main/java/com/sky/context/BaseContext.java)
</details>

<details>
<summary><b>PART 3: AOP 自动填充与菜品管理</b></summary>

- 借助 **AOP 切面编程** 实现 `@AutoFill` 自定义注解，在新增/修改操作时自动填充 `create_time`、`update_user` 等公共字段，大幅减少冗余代码。
- 接入阿里云 OSS 服务实现图片上传。
- 菜品管理：多表操作（`dish` 与 `dish_flavor`），实现带口味的菜品新增、修改与删除。
- **关联代码**：[`AutoFillAspect.java`](sky-common/src/main/java/com/sky/aspect/AutoFillAspect.java), [`AutoFill.java`](sky-common/src/main/java/com/sky/annotation/AutoFill.java)
</details>

<details>
<summary><b>PART 4: 套餐管理</b></summary>

- 实现套餐的 CRUD 操作，处理套餐与菜品的复杂多对多关联关系（`setmeal_dish`）。
- **关联代码**：[`SetmealController.java`](sky-server/src/main/java/com/sky/controller/admin/SetmealController.java)
</details>

<details>
<summary><b>PART 5 & 6: Redis 缓存入门与微信小程序端接入</b></summary>

- 整合 `Spring Data Redis` 操作缓存。
- 基于 Redis 存储店铺营业状态（1: 营业中, 0: 打烊中）。
- 借助 `HttpClient` 访问微信接口服务，完成小程序用户的授权和自动注册登录。
- **关联代码**：[`RedisConfiguration.java`](sky-server/src/main/java/com/sky/config/RedisConfiguration.java)
</details>

<details>
<summary><b>PART 7: 性能优化：缓存菜品与套餐</b></summary>

- **菜品缓存**：使用 `RedisTemplate` 手动控制缓存逻辑，在后台修改菜品时使用 `dish_*` 通配符一键清理缓存。
- **套餐缓存**：使用 `Spring Cache` (`@Cacheable`, `@CacheEvict`) 实现极其优雅的零代码侵入式缓存。
- **关联代码**：[`DishController.java`](sky-server/src/main/java/com/sky/controller/user/DishController.java), [`SetmealController.java`](sky-server/src/main/java/com/sky/controller/user/SetmealController.java)
</details>

<details>
<summary><b>PART 8, 9 & 10: 核心交易流：购物车、下单与任务调度</b></summary>

- 购物车与地址簿管理。
- 订单支付与流转：商家接单、拒单、派送、完成等核心状态机逻辑。
- 引入 `Spring Task` 定时清理超时未支付订单和一直处于派送中的订单。
- **关联代码**：[`OrderController.java`](sky-server/src/main/java/com/sky/controller/admin/OrderController.java), [`OrderTask.java`](sky-server/src/main/java/com/sky/task/OrderTask.java)
</details>

<details>
<summary><b>PART 11 & 12: 数据统计大屏、POI 报表与 WebSocket</b></summary>

- 使用 ECharts 所需的数据格式（逗号分隔字符串）统计**营业额、用户新增量、有效订单数**。
- 编写复杂的 `JOIN` + `GROUP BY` SQL 语句查询 **销量 Top 10**。
- 引入 **Apache POI**，读取 Excel 模板并动态写入运营数据，实现报表导出下载。
- 整合 **WebSocket** 实现全双工通信，完成后台的“来单提醒”与“客户催单”语音播报功能。
- **关联代码**：[`ReportServiceImpl.java`](sky-server/src/main/java/com/sky/service/impl/ReportServiceImpl.java), [`OrderMapper.xml`](sky-server/src/main/resources/mapper/OrderMapper.xml), [`WebSocketServer.java`](sky-server/src/main/java/com/sky/websocket/WebSocketServer.java)
</details>

---

## 💡 开发实战全记录：核心 Q&A 与踩坑日记

本部分记录了在项目开发中后期，我与 AI 结对编程时遇到的核心难点、底层逻辑剖析以及连环 BUG 修复过程。

### 1. 数据统计模块的 SQL 进阶与坑点
**Q：在统计每天的营业额时，为什么要在 Java 代码里生成近 30 天的日期列表做 `for` 循环查询，而不是直接在 SQL 里用 `GROUP BY order_time` 一次性查出来？**
* **A（核心设计逻辑）**：因为 SQL 的 `GROUP BY` 只能查出“数据库里存在记录”的日期。如果餐厅在 4月12日 一单都没卖，直接查数据库会直接缺失 12 日的数据，导致前端 ECharts 折线图发生断层。通过 Java 循环每一天，没查到结果的补 `0.0`，才能保证前端数据的连续性。
* **技术细节**：在 `OrderMapper.xml` 的动态 SQL 中，XML 无法直接解析 `<` 和 `>`，必须使用转义字符 `&lt;` 和 `&gt;`。为了算“客单价”，我们区分了 `sum(amount)`（查营业额）和 `count(id)`（查有效订单数）。

### 2. Apache POI 报表导出的连环 NullPointerException 踩坑实录
**Q：在导出 Excel 报表时，一直报 `Cannot invoke "..." because "row" is null`，后来变成了 `return value of "getCell(int)" is null`，这是为什么？**
* **A（POI 稀疏存储原理）**：Apache POI 为了节省内存，如果 Excel 模板中的某一行或某一个格子没有被修改过（完全空白、无边框），它就不会加载到内存中。
    * 当调用 `sheet.getRow(i)` 时，如果该行不存在，直接返回 `null`。
    * 当调用 `row.getCell(j)` 时，如果格子不存在，也是 `null`。
* **终极解决方案（防空策略）**：不要相信模板！在代码中强制拦截并创建：
  ```java
  XSSFRow row = sheet.getRow(rowIndex);
  if (row == null) row = sheet.createRow(rowIndex);
  // 不要用 getCell()，全部替换为 createCell()，强行创建格子写入数据
  row.createCell(2).setCellValue(turnover);
  ```

### 3. 架构解耦：为什么突然爆红了一个 `workspaceService`？
**场景**：在写 `ReportServiceImpl` 导出 Excel 时，代码提示 `workspaceService` 不存在。
* **原因剖析**：Excel 导出的数据包含了各项指标（营业额、客单价等），这些指标的计算逻辑在“控制台大屏（Workspace）”接口中已经写过一次了。为了遵循 **DRY (Don't Repeat Yourself)** 原则，我们直接在 `ReportService` 中注入了 `WorkspaceService`。
* **修复**：补齐了 `WorkspaceServiceImpl`，并在其内部复用了 `OrderMapper` 的 `sumByMap` 和 `countByMap`，完美实现了代码的复用。接口不匹配时报的“方法不会覆盖超类型方法”错误，也通过在 `ReportService` 接口中补充声明得以解决。

### 4. 灵魂拷问：`@Autowired` 到底比 `new` 好在哪？
**Q：如果在别处写了一个 Service，我自己 `new XXXServiceImpl()` 也可以调用，为什么要用 `@Autowired` 自动装配？Spring 又是怎么传参把对象造出来的？**
* **A（IoC 容器的精髓）**：
    1. **解耦与单例**：手动 `new` 的对象散落在各处，如果类的构造函数变了，所有 `new` 的地方都会报错。而 `@Autowired` 把对象的生命周期交给了 Spring（管家），全国统一分发一个单例对象，省内存且极其好维护。
    2. **代理失效（最致命）**：在本项目中，我们写了 `@AutoFill` AOP 切面（机器人）来自动填入时间。如果对象是你自己 `new` 的，它就是一个“野生对象”，Spring 根本监控不到它，切面就会**完全失效**！只有交由 Spring 容器管理（通过反射构建）的对象，才能在执行前被 AOP 成功拦截拦截并篡改。

### 5. 缓存策略：RedisTemplate 与 Spring Cache 的“组合拳”
**Q：为什么“菜品缓存”我们要手写 Redis 的 `opsForValue`，而“套餐缓存”只要加一个 `@Cacheable` 就行了？清理缓存为什么要用 `dish_*`？**
* **A（精细化与工业化）**：
    * **Spring Cache (`@Cacheable`, `@CacheEvict`)**：属于自动挡，一行代码不写就能实现“查库前看缓存，改库时删缓存”，非常适合标准 CRUD 场景（如套餐查询）。
    * **RedisTemplate**：属于手动挡，用于复杂场景。比如老板改了任意一个菜品，我们为了保证用户绝对不会看到错乱的旧数据，采用了**“宁可错杀一千，不可放过一个”**的策略，直接通过 `cleanCache("dish_*")` 利用通配符一口气删除了所有菜品分类的缓存。这种操作，用纯注解是很难完美覆盖的。
```