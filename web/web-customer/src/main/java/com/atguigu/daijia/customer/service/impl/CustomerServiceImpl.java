package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import jodd.time.TimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerServiceImpl implements CustomerService {

    //注入远程调用接口
    @Autowired
    private CustomerInfoFeignClient client;
    @Autowired
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public String login(String code) {

        //1.拿着code进行远程调用，返回用户id
        Result<Long> result = customerInfoFeignClient.login(code);

        //2.判断如果返回失败，直接返回错误提示
        if (result.getCode() != 200) {
            throw new GuiguException(result.getCode(), result.getMessage());
        }

        //3.获取远程调用返回用户id
        Long customerId = result.getData();

        //4.判断返回的用户id是否为空，如果为空，返回错误提示
        if(customerId == null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //5.生成token字符串
        String token = UUID.randomUUID().toString().replaceAll("-", "");

        //6.把用户id放到Redis，设置过期时间
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, customerId.toString(), 30, TimeUnit.MINUTES);

        return token;
    }

    @Override
    public CustomerLoginVo getCustomerLoginInfo(Long customerId) {

        //2.根据用户id进行远程调用，得到用户信息
        Result<CustomerLoginVo> customerLoginVoResult =
                customerInfoFeignClient.getCustomerLoginInfo(customerId);

        Integer code = customerLoginVoResult.getCode();
        //判断是否查询成功，成功会返回代码200
        if(code != 200){
            //查询失败，抛出错误提示
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //封装customerLoginVo
        CustomerLoginVo customerLoginVo = customerLoginVoResult.getData();
        if(customerLoginVo == null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        return customerLoginVo;
    }
}
