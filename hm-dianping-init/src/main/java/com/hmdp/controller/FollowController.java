package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserID, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserID, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserID) {
        return followService.isFollow(followUserID);
    }
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long ID) {
        return followService.followCommons(ID);
    }
}
