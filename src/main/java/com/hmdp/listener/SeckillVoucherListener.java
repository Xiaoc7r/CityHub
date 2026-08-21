package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * RocketMQ 秒杀订单消费者：
 * 1. 异步落单（保存订单 + 扣减 MySQL 库存）；
 * 2. 发送延迟消息，用于超时未支付订单自动关闭。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "seckill-order-topic", consumerGroup = "seckill-order-consumer")
public class SeckillVoucherListener implements RocketMQListener<String> {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private VoucherOrderServiceImpl voucherOrderService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String msg) {
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        log.info("RocketMQ 秒杀订单消费: {}", voucherOrder);
        // 保存订单
        voucherOrderService.save(voucherOrder);
        // 数据库秒杀库存减一
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        // 发送 RocketMQ 延迟消息：约 1 分钟后检查订单是否仍未支付
        // RocketMQ 默认 delayLevel: 1s 5s 10s 30s 1m 2m ... 这里用 5 表示 1 分钟
        rocketMQTemplate.syncSend(
                "order-timeout-topic",
                MessageBuilder.withPayload(String.valueOf(voucherOrder.getId())).build(),
                3000,
                5
        );
    }
}
