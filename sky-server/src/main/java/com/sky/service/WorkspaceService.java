package com.sky.service;

import com.sky.vo.BusinessDataVO;
import java.time.LocalDateTime;

public interface WorkspaceService {
    /**
     * 根据时间段统计营业数据
     */
    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);
}