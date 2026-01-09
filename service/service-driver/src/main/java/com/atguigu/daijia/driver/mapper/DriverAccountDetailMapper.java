package com.atguigu.daijia.driver.mapper;

import com.atguigu.daijia.model.entity.driver.DriverAccountDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;

@Mapper
public interface DriverAccountDetailMapper extends BaseMapper<DriverAccountDetail> {


    void add(Long driverId, BigDecimal amount);
}
