package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.impl.FollowServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private FollowServiceImpl followService;
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(id, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable("id") Long id){
        return followService.isfollow(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id){
        return  followService.followCommons(id);
    }
}
