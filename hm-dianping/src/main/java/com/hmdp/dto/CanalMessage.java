package com.hmdp.dto;

/**
 * ClassName: CanalMessage
 * Package: com.hmdp.dto
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/3/20 20:59
 * @Version 1.0
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Canal Binlog 消息传输对象
 * 用于在 Canal Client → MQ → 消费者 之间传递变更数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanalMessage {

    /**
     * 表名（如：tb_shop）
     */
    private String table;

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 操作类型：INSERT/UPDATE/DELETE
     */
    private String type;

    /**
     * 时间戳（用于顺序校验）
     */
    private Long timestamp;

    /**
     * 额外数据（可选，如变更的字段）
     */
    private String extraData;

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getExtraData() {
        return extraData;
    }

    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }
}
