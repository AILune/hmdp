package com.hmdp.utils;

/**
 * ClassName: ILock
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author Jason Yee
 * @Create 2025/12/28 21:09
 * @Version 1.0
 */
public interface ILock {
    boolean tryLock(long timeoutSec);

    void unlock();
}
