package com.atguigu.daijia.driver.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.atguigu.daijia.rules.client.ProfitsharingRuleFeignClient;
import com.atguigu.daijia.rules.client.RewardRuleFeignClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;
    @Autowired
    private NewOrderFeignClient newOrderFeignClient;
    @Autowired
    private LocationFeignClient locationFeignClient;
    @Autowired
    private MapFeignClient mapFeignClient;
    @Autowired
    private FeeRuleFeignClient feeRuleFeignClient;
    @Autowired
    private RewardRuleFeignClient rewardRuleFeignClient;
    @Autowired
    private ProfitsharingRuleFeignClient profitsharingRuleFeignClient;

    //根据订单id获取订单状态
    @Override
    public Integer getOrderStatus(Long orderId) {
        return orderInfoFeignClient.getOrderStatus(orderId).getData();
    }

    // 查询司机新订单数据
    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        return newOrderFeignClient.findNewOrderQueueData(driverId).getData();
    }

    //司机抢单
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        Boolean flag = orderInfoFeignClient.robNewOrder(driverId, orderId).getData();
        return flag;
    }

    //司机端查找当前订单
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        return orderInfoFeignClient.searchDriverCurrentOrder(driverId).getData();
    }

    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long driverId) {
        //订单信息
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        if(orderInfo.getDriverId().longValue() != driverId.longValue()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //账单信息
        OrderBillVo orderBillVo = null;
        //分账信息
        OrderProfitsharingVo orderProfitsharing = null;
        if (orderInfo.getStatus().intValue() >= OrderStatus.END_SERVICE.getStatus().intValue()) {
            orderBillVo = orderInfoFeignClient.getOrderBillInfo(orderId).getData();

            //获取分账信息
            orderProfitsharing = orderInfoFeignClient.getOrderProfitsharing(orderId).getData();
        }

        //封装订单信息
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        orderInfoVo.setOrderId(orderId);
        BeanUtils.copyProperties(orderInfo, orderInfoVo);
        orderInfoVo.setOrderBillVo(orderBillVo);
        orderInfoVo.setOrderProfitsharingVo(orderProfitsharing);
        return orderInfoVo;
    }

    //司机到达代驾起始地点
    @Override
    public Boolean driverArriverStartLocation(Long orderId, Long driverId) {
        //防止刷单，计算司机的经纬度与代驾的起始经纬度是否在1公里范围内
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        OrderLocationVo orderLocationVo = locationFeignClient.getCacheOrderLocation(orderId).getData();
        //司机的位置与代驾起始点位置的距离
        double distance = LocationUtil.getDistance(orderInfo.getStartPointLatitude().doubleValue(), orderInfo.getStartPointLongitude().doubleValue(), orderLocationVo.getLatitude().doubleValue(), orderLocationVo.getLongitude().doubleValue());
        if(distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_START_LOCATION_DISTION_ERROR);
        }
        return orderInfoFeignClient.driverArriveStartLocation(orderId, driverId).getData();
    }

    //更新代驾车辆信息
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        Boolean flag = orderInfoFeignClient.updateOrderCart(updateOrderCartForm).getData();
        System.out.println("更新车辆信息" + flag);
        return flag;
    }

    //计算最佳驾驶路线
    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        return mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();
    }

    //开始服务
    @Override
    public Boolean startDrive(StartDriveForm startDriveForm) {
        return orderInfoFeignClient.startDrive(startDriveForm).getData();
    }

    //代驾服务：获取订单服务最后一个位置信息
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        return locationFeignClient.getOrderServiceLastLocation(orderId).getData();
    }

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @SneakyThrows
    @Override
    public Boolean endDrive(OrderFeeForm orderFeeForm) {
        // ===================== 1. 异步获取订单信息 + 核心校验 =====================
        CompletableFuture<OrderInfo> orderInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
                    OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId()).getData();
                    // 【新增】orderInfo判空
                    if (orderInfo == null) {
                        log.error("结束代驾失败：订单信息不存在，orderId={}", orderFeeForm.getOrderId());
                        throw new GuiguException(ResultCodeEnum.DATA_NOT_EXIST);
                    }
                    // 【新增】driverId匹配校验（旧版核心逻辑）
                    if (orderInfo.getDriverId().longValue() != orderFeeForm.getDriverId().longValue()) {
                        throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
                    }
                    return orderInfo;
                }, threadPoolExecutor)
                // 【新增】异步任务异常处理（避免单个任务失败导致整体流程挂掉）
                .exceptionally(ex -> {
                    log.error("获取订单信息异步任务失败：", ex);
                    throw new GuiguException(ResultCodeEnum.SYSTEM_ERROR);
                });

        // ===================== 2. 异步获取司机最后位置 + 判空 =====================
        CompletableFuture<OrderServiceLastLocationVo> orderServiceLastLocationVoCompletableFuture = CompletableFuture.supplyAsync(() -> {
                    OrderServiceLastLocationVo orderServiceLastLocationVo = locationFeignClient.getOrderServiceLastLocation(orderFeeForm.getOrderId()).getData();
                    // 【新增】位置信息判空
                    if (orderServiceLastLocationVo == null) {
                        log.error("结束代驾失败：司机最后位置信息不存在，orderId={}", orderFeeForm.getOrderId());
                        throw new GuiguException(ResultCodeEnum.DATA_NOT_EXIST);
                    }
                    return orderServiceLastLocationVo;
                }, threadPoolExecutor)
                .exceptionally(ex -> {
                    log.error("获取司机最后位置异步任务失败：", ex);
                    throw new GuiguException(ResultCodeEnum.SYSTEM_ERROR);
                });

        // 等待订单信息+位置信息异步任务完成
        CompletableFuture.allOf(orderInfoCompletableFuture, orderServiceLastLocationVoCompletableFuture).join();

        // ===================== 3. 刷单校验（距离判断） =====================
        OrderInfo orderInfo = orderInfoCompletableFuture.get();
        OrderServiceLastLocationVo orderServiceLastLocationVo = orderServiceLastLocationVoCompletableFuture.get();
        // 司机的位置与代驾终点位置的距离
        double distance = LocationUtil.getDistance(
                orderInfo.getEndPointLatitude().doubleValue(),
                orderInfo.getEndPointLongitude().doubleValue(),
                orderServiceLastLocationVo.getLatitude().doubleValue(),
                orderServiceLastLocationVo.getLongitude().doubleValue()
        );
        if (distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }

        // ===================== 4. 异步计算订单实际里程 + 判空 =====================
        CompletableFuture<BigDecimal> realDistanceCompletableFuture = CompletableFuture.supplyAsync(() -> {
                    BigDecimal realDistance = locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId()).getData();
                    // 【新增】实际里程判空兜底
                    if (realDistance == null) {
                        log.warn("结束代驾：订单实际里程为空，orderId={}，兜底为0", orderFeeForm.getOrderId());
                        realDistance = BigDecimal.ZERO;
                    }
                    log.info("结束代驾，订单实际里程：{}", realDistance);
                    return realDistance;
                }, threadPoolExecutor)
                .exceptionally(ex -> {
                    log.error("计算实际里程异步任务失败：", ex);
                    return BigDecimal.ZERO; // 兜底，避免流程中断
                });

        // ===================== 5. 异步计算代驾费用（依赖实际里程） =====================
        CompletableFuture<FeeRuleResponseVo> feeRuleResponseVoCompletableFuture = realDistanceCompletableFuture.thenApplyAsync(realDistance -> {
                    FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
                    feeRuleRequestForm.setDistance(realDistance);
                    feeRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());

                    // 【新增】startTime判空
                    if (orderInfo.getStartServiceTime() == null) {
                        throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
                    }

                    // 等候时间计算
                    Integer waitMinute = Math.abs((int) ((orderInfo.getArriveTime().getTime() - orderInfo.getAcceptTime().getTime()) / (1000 * 60)));
                    feeRuleRequestForm.setWaitMinute(waitMinute);
                    log.info("结束代驾，费用参数：{}", JSON.toJSONString(feeRuleRequestForm));

                    FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm).getData();
                    // 【新增】费用计算结果判空
                    if (feeRuleResponseVo == null) {
                        log.error("结束代驾：代驾费用计算结果为空，orderId={}", orderFeeForm.getOrderId());
                        throw new GuiguException(ResultCodeEnum.SYSTEM_ERROR);
                    }

                    // 订单总金额 = 基础费用 + 路桥费 + 停车费 + 其他费用 + 乘客好处费
                    BigDecimal totalAmount = feeRuleResponseVo.getTotalAmount()
                            .add(orderFeeForm.getTollFee() == null ? BigDecimal.ZERO : orderFeeForm.getTollFee())
                            .add(orderFeeForm.getParkingFee() == null ? BigDecimal.ZERO : orderFeeForm.getParkingFee())
                            .add(orderFeeForm.getOtherFee() == null ? BigDecimal.ZERO : orderFeeForm.getOtherFee())
                            .add(orderInfo.getFavourFee() == null ? BigDecimal.ZERO : orderInfo.getFavourFee());
                    feeRuleResponseVo.setTotalAmount(totalAmount);
                    log.info("费用明细：{}", JSON.toJSONString(feeRuleResponseVo));
                    return feeRuleResponseVo;
                }, threadPoolExecutor)
                .exceptionally(ex -> {
                    log.error("计算代驾费用异步任务失败：", ex);
                    throw new GuiguException(ResultCodeEnum.SYSTEM_ERROR);
                });

        // ===================== 6. 异步获取订单数 + 判空兜底 =====================
        CompletableFuture<Long> orderNumCompletableFuture = CompletableFuture.supplyAsync(() -> {
                    String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
                    String endTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 24:00:00";
                    Long orderNum = orderInfoFeignClient.getOrderNumByTime(startTime, endTime).getData();

                    // 【新增】订单数判空兜底（旧版逻辑）
                    if (orderNum == null) {
                        log.warn("结束代驾：订单数查询结果为空，orderId={}", orderFeeForm.getOrderId());
                        orderNum = 0L;
                    }
                    return orderNum;
                }, threadPoolExecutor)
                .exceptionally(ex -> {
                    log.error("获取订单数异步任务失败：", ex);
                    return 0L; // 兜底，避免流程中断
                });

        // ===================== 7. 异步计算系统奖励（依赖订单数） =====================
        CompletableFuture<RewardRuleResponseVo> rewardRuleResponseVoCompletableFuture = orderNumCompletableFuture.thenApplyAsync(orderNum -> {
                    RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
                    rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
                    rewardRuleRequestForm.setOrderNum(orderNum);

                    // 【新增】startTime判空（旧版逻辑）
                    if (rewardRuleRequestForm.getStartTime() == null) {
                        throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
                    }

                    RewardRuleResponseVo rewardRuleResponseVo = rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm).getData();
                    log.info("结束代驾，系统奖励：{}", JSON.toJSONString(rewardRuleResponseVo));

                    // 【新增】奖励结果判空初始化（旧版逻辑）
                    if (rewardRuleResponseVo == null) {
                        log.warn("结束代驾：系统奖励规则计算结果为空，使用默认值，orderId={}", orderFeeForm.getOrderId());
                        rewardRuleResponseVo = new RewardRuleResponseVo(); // 初始化空对象
                    }
                    return rewardRuleResponseVo;
                }, threadPoolExecutor)
                .exceptionally(ex -> {
                    log.error("计算系统奖励异步任务失败：", ex);
                    log.warn("结束代驾：系统奖励计算失败，使用默认值，orderId={}", orderFeeForm.getOrderId());
                    return new RewardRuleResponseVo(); // 兜底初始化
                });

        // ===================== 8. 异步计算分账信息（依赖费用+订单数） =====================
        CompletableFuture<ProfitsharingRuleResponseVo> profitsharingRuleResponseVoCompletableFuture = feeRuleResponseVoCompletableFuture
                .thenCombineAsync(orderNumCompletableFuture, (feeRuleResponseVo, orderNum) -> {
                    ProfitsharingRuleRequestForm profitsharingRuleRequestForm = new ProfitsharingRuleRequestForm();
                    profitsharingRuleRequestForm.setOrderAmount(feeRuleResponseVo.getTotalAmount());
                    profitsharingRuleRequestForm.setOrderNum(orderNum);

                    ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleFeignClient
                            .calculateOrderProfitsharingFee(profitsharingRuleRequestForm).getData();
                    log.info("结束代驾，分账信息：{}", JSON.toJSONString(profitsharingRuleResponseVo));

                    // 【新增】分账结果判空初始化（旧版逻辑）
                    if (profitsharingRuleResponseVo == null) {
                        log.warn("结束代驾：分账规则计算结果为空，使用默认值，orderId={}", orderFeeForm.getOrderId());
                        profitsharingRuleResponseVo = new ProfitsharingRuleResponseVo(); // 初始化空对象
                    }
                    return profitsharingRuleResponseVo;
                }, threadPoolExecutor)
                .exceptionally(ex -> {
                    log.error("计算分账信息异步任务失败：", ex);
                    log.warn("结束代驾：分账计算失败，使用默认值，orderId={}", orderFeeForm.getOrderId());
                    return new ProfitsharingRuleResponseVo(); // 兜底初始化
                });

        // ===================== 9. 等待所有异步任务完成 =====================
        CompletableFuture.allOf(
                realDistanceCompletableFuture,
                feeRuleResponseVoCompletableFuture,
                orderNumCompletableFuture,
                rewardRuleResponseVoCompletableFuture,
                profitsharingRuleResponseVoCompletableFuture
        ).join();

        // ===================== 10. 获取异步任务结果 =====================
        BigDecimal realDistance = realDistanceCompletableFuture.get();
        FeeRuleResponseVo feeRuleResponseVo = feeRuleResponseVoCompletableFuture.get();
        RewardRuleResponseVo rewardRuleResponseVo = rewardRuleResponseVoCompletableFuture.get();
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleResponseVoCompletableFuture.get();

        // ===================== 11. 封装更新账单对象 =====================
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());

        // 【新增】基础费用字段判空兜底
        updateOrderBillForm.setTollFee(orderFeeForm.getTollFee() == null ? BigDecimal.ZERO : orderFeeForm.getTollFee());
        updateOrderBillForm.setParkingFee(orderFeeForm.getParkingFee() == null ? BigDecimal.ZERO : orderFeeForm.getParkingFee());
        updateOrderBillForm.setOtherFee(orderFeeForm.getOtherFee() == null ? BigDecimal.ZERO : orderFeeForm.getOtherFee());
        updateOrderBillForm.setFavourFee(orderInfo.getFavourFee() == null ? BigDecimal.ZERO : orderInfo.getFavourFee());

        updateOrderBillForm.setRealDistance(realDistance);
        // 订单奖励信息（已判空，无空指针）
        BeanUtils.copyProperties(rewardRuleResponseVo, updateOrderBillForm);
        // 代驾费用信息（已判空，无空指针）
        BeanUtils.copyProperties(feeRuleResponseVo, updateOrderBillForm);
        // 分账相关信息（已判空，无空指针）
        BeanUtils.copyProperties(profitsharingRuleResponseVo, updateOrderBillForm);
        updateOrderBillForm.setProfitsharingRuleId(profitsharingRuleResponseVo.getProfitsharingRuleId());

        log.info("结束代驾，更新账单信息：{}", JSON.toJSONString(updateOrderBillForm));

        // ===================== 12. 结束代驾更新账单 =====================
        orderInfoFeignClient.endDrive(updateOrderBillForm);
        return true;
    }

    @Override
    public PageVo findDriverOrderPage(Long driverId, Long page, Long limit) {
        return orderInfoFeignClient.findDriverOrderPage(driverId, page, limit).getData();
    }

}
