package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    // 🚨 加上这一行注入，消除爆红
    @Autowired
    private WorkspaceService workspaceService;

    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        // 1. 创建日期列表，存放 begin 到 end 范围内的每一天
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 2. 存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            // 查询当天状态为“已完成”的订单金额合计
            // 构造当天的开始时间 00:00:00 和 结束时间 23:59:59
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED); // 只统计状态为 5 (已完成) 的订单

            Double turnover = orderMapper.sumByMap(map);
            // 如果当天没订单，数据库返回 null，我们要转成 0.0
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        // 3. 将 List 转换为逗号分隔的字符串，封装进 VO
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Integer> newUserList = new ArrayList<>(); // 新增用户
        List<Integer> totalUserList = new ArrayList<>(); // 总用户

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            // 查总用户：只要创建时间在今天结束之前就行
            map.put("end", endTime);
            Integer totalUser = userMapper.countByMap(map);

            // 查新增用户：创建时间在今天范围内的
            map.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(map);

            totalUserList.add(totalUser == null ? 0 : totalUser);
            newUserList.add(newUser == null ? 0 : newUser);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 🚨 调用 Mapper 查出排名前 10 的数据（封装在 GoodsSalesDTO 中）
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(beginTime, endTime);

        // 提取名字列表
        List<String> names = goodsSalesDTOList.stream()
                .map(GoodsSalesDTO::getName)
                .collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");

        // 提取销量列表
        List<Integer> numbers = goodsSalesDTOList.stream()
                .map(GoodsSalesDTO::getNumber)
                .collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }


    @Override
    public void exportBusinessData(HttpServletResponse response) {
        // 1. 查询数据库，获取营业数据——查询最近30天的数据
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);

        // 查询概览数据（这里可以封装一个私有方法，查询营业额、订单数、用户数等）
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(
                LocalDateTime.of(dateBegin, LocalTime.MIN),
                LocalDateTime.of(dateEnd, LocalTime.MAX));

        // 2. 通过 POI 将数据写入到 Excel 文件中
        // 使用输入流读取模板文件
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");

        try {
            XSSFWorkbook excel = new XSSFWorkbook(in);
            XSSFSheet sheet = excel.getSheet("Sheet1");

            // 1. 填充概览数据（时间）
            XSSFRow row2 = sheet.getRow(1);
            if (row2 == null) row2 = sheet.createRow(1); // 防灾：如果第2行不存在就创建
            row2.createCell(1).setCellValue("时间：" + dateBegin + " 至 " + dateEnd);

            // 2. 填充第4行概览数据
            XSSFRow row4 = sheet.getRow(3); // 索引3对应第4行
            if (row4 == null) row4 = sheet.createRow(3);
            row4.createCell(2).setCellValue(businessDataVO.getTurnover());
            row4.createCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row4.createCell(6).setCellValue(businessDataVO.getNewUsers());

            // 3. 填充第5行概览数据
            XSSFRow row5 = sheet.getRow(4); // 索引4对应第5行
            if (row5 == null) row5 = sheet.createRow(4);
            row5.createCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row5.createCell(4).setCellValue(businessDataVO.getUnitPrice());

            // 4. 循环填充 30 天的明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = dateBegin.plusDays(i);
                BusinessDataVO dayData = workspaceService.getBusinessData(
                        LocalDateTime.of(date, LocalTime.MIN),
                        LocalDateTime.of(date, LocalTime.MAX));

                // 🚨 这里就是第190行附近最容易报错的地方
                int rowIndex = 7 + i; // 从第8行开始
                XSSFRow row = sheet.getRow(rowIndex);
                if (row == null) {
                    row = sheet.createRow(rowIndex); // 重点：如果模板里没有这行，必须手动创建！
                }

                // 使用 createCell 确保格子一定存在
                row.createCell(1).setCellValue(date.toString());            // 日期
                row.createCell(2).setCellValue(dayData.getTurnover());       // 营业额
                row.createCell(3).setCellValue(dayData.getValidOrderCount());// 有效订单
                row.createCell(4).setCellValue(dayData.getOrderCompletionRate()); // 完成率
                row.createCell(5).setCellValue(dayData.getUnitPrice());      // 客单价
                row.createCell(6).setCellValue(dayData.getNewUsers());       // 新增用户
            }

            // 4. 通过输出流将 Excel 文件下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            // 5. 关闭资源
            out.close();
            excel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}