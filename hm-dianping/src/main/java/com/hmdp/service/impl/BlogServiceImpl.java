package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private FollowServiceImpl  followService;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        for(Blog blog : records){
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询Blog
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记描述不存在");
        }
        //查询用户信息（同时判断用户是否点赞当前博客来让前端展示❤️）
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    public void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    //传入的是BlogId
    @Override
    public Result likeBlog(Long id) {
        //1.查询当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断是否点赞博客
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //3.如果未点赞，则点赞数+1并将用户ID写入Redis
        if(score == null){
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            //4.如果已点赞，则点赞数-1并将用户ID从Redis中移除
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    private void isBlogLiked(Blog blog){
//        Long userId = UserHolder.getUser().getId();
        //由于前端的hot blog接口被拦截器排除在外，因此无需登录也会显示热门blog，同时也会展示当前热门blog是否被用户点赞，
        // 但如果此时用户未登录，调用这个方法的上面这行代码就会出现问题，getUser后为null，自然继续getId就会报错
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //此时表示用户未登录，不走这个方法来查询用户是否点赞
            return;
        }
        Long userId = user.getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //查询出前top5个点赞的用户
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        //这里获取的是member，也就是用户ID
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return  Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //根据id查询用户 ids:1,5  select... where id in (ids) order by field(id, 1 ,5)
//        List<User> users = userService.listByIds(ids);
        List<User> users = userService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        List<Object> userDTO = users
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTO);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("笔记保存失败！");
        }
        //查询当前用户的所有粉丝   查follow表得到Follow即可，其中就包含了当前用户的粉丝ID
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        String key = RedisConstants.FEED_KEY;
        for(Follow follow : follows){
            stringRedisTemplate.opsForZSet().add(key + follow.getUserId(), blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        //2.查询Redis，获取当前用户的follow推送的BlogId
        // 语句：ZREVRANGEBYSCORE key max min [WITHSCORES] [LIMIT offset count]
        String key = RedisConstants.FEED_KEY + userId;
        //方法：reverseRangeByScoreWithScores(K key, double min, double max, long offset, long count)
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok(); //表示没有follow推送了笔记
        }
        int off = 1;
        long minTime = max;
//        List<Blog> blogList = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        //3.解析数据    blogIds：List<>，推送给当前用户的Blog  minTime：Long，最小时间戳 offset：Integer，偏置
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //getValue()获取Blog ID
            String value = typedTuple.getValue();
            ids.add(Long.valueOf(value));
            //getScore()获取时间戳
            long score = typedTuple.getScore().longValue();
            if (score == minTime) {
                off++;
            }
            else{
                off = 1;
                minTime = score;
            }
        }
        off = minTime == max ? off + offset : off;
        //根据BlogID查询BlogList，再根据时间戳更新offset
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogList = query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogList) {
            queryBlogUser(blog);    //查询blog对应的用户昵称与头像
            isBlogLiked(blog);      //查询blog的点赞信息
        }

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(off);
        scrollResult.setMinTime(minTime);

        return  Result.ok(scrollResult);
    }
}
