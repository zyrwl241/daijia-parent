package com.atguigu.daijia.customer.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.alibaba.nacos.common.utils.StringUtils;
import com.atguigu.daijia.customer.mapper.CustomerInfoMapper;
import com.atguigu.daijia.customer.mapper.CustomerLoginLogMapper;
import com.atguigu.daijia.customer.service.CustomerInfoService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.entity.customer.CustomerLoginLog;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerInfoServiceImpl extends ServiceImpl<CustomerInfoMapper, CustomerInfo> implements CustomerInfoService {

    @Autowired
    private WxMaService wxMaService;
    @Autowired
    private CustomerInfoMapper customerInfoMapper;
    @Autowired
    private CustomerLoginLogMapper customerLoginLogMapper;

    //微信小程序登录接口
    @Override
    public Long login(String code) throws WxErrorException {
    //1. 获取code值，使用微信工具包wxMaService对象,获取微信唯一标识openid
        //将openid放到try代码块外面定义，便于调用
        String openid = null;
        //try代码块包裹，抛出异常
        try {
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            //获取微信唯一标识openid
            openid = sessionInfo.getOpenid();
            log.info("[小程序授权]openid = {}", openid);
        }catch (WxErrorException e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    //2. 根据openid查询数据库表，判断是否第一次登录
        //如果openid不存在返回null,如果存在返回一条记录
        //声明LambdaQueryWrapper类对象（MP的Lambda版条件构造器,用于拼接查询条件）
        LambdaQueryWrapper<CustomerInfo> queryWrapper = new LambdaQueryWrapper<>();
        //拼接查询条件：用户信息里获取的WxOpenId 等于 传入的openid
        queryWrapper.eq(CustomerInfo::getWxOpenId,openid);
        //根据拼接好的查询条件去查询用户信息（一条）
        CustomerInfo customerInfo = customerInfoMapper.selectOne(queryWrapper);

    //3.如果第一次登录，添加信息到用户表
        //判断用户是否是第一次登录（是否查询到customerInfo）
        if(customerInfo == null){
            customerInfo = new CustomerInfo();
            //id
            customerInfo.setWxOpenId(openid);
            //设置昵称：将当前系统数据转换为字符串，作为临时昵称使用
            customerInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            //头像地址
            customerInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
            //添加用户
            customerInfoMapper.insert(customerInfo);
            log.info("添加用户：{}", customerInfo);
        }
    //4.记录登录日志
        //声明用户日志对象
        CustomerLoginLog customerLoginLog = new CustomerLoginLog();
        //记录用户id
        customerLoginLog.setCustomerId(customerInfo.getId());
        //设置描述：备注是小程序登录
        customerLoginLog.setMsg("小程序登录");
        //添加用户登录日志
        customerLoginLogMapper.insert(customerLoginLog);

    //5. 返回用户id(不是微信标识，而是数据库在主键策略生成的id)
    return customerInfo.getId();
    }

    @Override
    public CustomerLoginVo getCustomerInfo(Long customerId) {

        //1.根据用户id查询用户信息
        CustomerInfo customerInfo = customerInfoMapper.selectById(customerId);
        log.info("根据用户id:{} 查询用户信息：{}", customerId, customerInfo);

        //2.封装到CustomerLoginVo
        CustomerLoginVo customerLoginVo = new CustomerLoginVo();
        BeanUtils.copyProperties(customerInfo, customerLoginVo);

        //customerLoginVO还缺少属性isBindPhone，是否绑定手机号，这个属性在customerInfo里没有
        String phone = customerInfo.getPhone();
        Boolean isBindPhone = StringUtils.hasText(phone);
        customerLoginVo.setIsBindPhone(isBindPhone);

        //3.CustomerLoginVo返回
        return customerLoginVo;
    }
}
