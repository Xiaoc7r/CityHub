package com.hmdp.listener;

import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * RocketMQ 延迟消息消费者：接收超时未支付订单的延迟消息并自动关单。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "order-timeout-topic", consumerGroup = "order-timeout-consumer")
public class OrderTimeoutListener implements RocketMQListener<String> {

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Override
    public void onMessage(String orderIdStr) {
        Long orderId = Long.valueOf(orderIdStr);
        boolean closed = voucherOrderService.closeTimeoutOrder(orderId);
        log.info("超时关单结果 orderId={}, closed={}", orderId, closed);
    }
}
