package com.atguigu.daijia.common.service;


import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RabbitService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    //发送信息
    public Boolean sendMessage(String exchangeOrder, String routingPaySuccess, String orderNo) {
        rabbitTemplate.convertAndSend(exchangeOrder, routingPaySuccess, orderNo);
        return true;
    }
}
