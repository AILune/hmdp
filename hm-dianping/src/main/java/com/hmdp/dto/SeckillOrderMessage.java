package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ClassName: SeckillOrderMessage
 * Package: com.hmdp.dto
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/1/8 20:23
 * @Version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {
    private Long orderId;
    private Long userId;
    private Long voucherId;
}