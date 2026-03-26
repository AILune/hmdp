package com.hmdp.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }


    /**
     * ✅ 新增：专门处理 Sentinel 热点参数限流异常
     */
    @ExceptionHandler(ParamFlowException.class)
    public Result handleParamFlowException(ParamFlowException e) {
        // 记录警告日志，不要打 ERROR，因为这是正常限流
        log.warn("【Sentinel 热点限流】触发限流：", e);
        return Result.fail("操作太频繁，请稍后再试");
    }

    /**
     * ✅ 新增：处理 Sentinel 普通流控异常
     */
    @ExceptionHandler(FlowException.class)
    public Result handleFlowException(FlowException e) {
        log.warn("【Sentinel 流控】触发限流", e);
        return Result.fail("系统繁忙，请稍后再试");
    }
}


