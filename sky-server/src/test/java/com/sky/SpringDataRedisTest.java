package com.sky;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

//@SpringBootTest
public class SpringDataRedisTest {
    @Autowired
    private RedisTemplate redisTemplate;
    @Test
    public void testRedisTemplate(){
        System.out.println(redisTemplate);
        ValueOperations valueOperations=redisTemplate.opsForValue();
        HashOperations hashOperations=redisTemplate.opsForHash();
        ListOperations listOperations=redisTemplate.opsForList();
        SetOperations setOperations=redisTemplate.opsForSet();
        ZSetOperations zSetOperations=redisTemplate.opsForZSet();
    }
    @Test
    public void testString(){
        redisTemplate.opsForValue().set("city","chengdu");
        String city=(String) redisTemplate.opsForValue().get("city");
        System.out.println(city);
        redisTemplate.opsForValue().set("code","1234",2, TimeUnit.MINUTES);
        redisTemplate.opsForValue().setIfAbsent("lock","1");
        redisTemplate.opsForValue().setIfAbsent("lock","1");
    }

    @Test
    public void testHasg(){
        HashOperations hashOperations=redisTemplate.opsForHash();
        hashOperations.put("100","name","tom");
        hashOperations.put("100","age","20");
        hashOperations.get("100","name");
        String name=(String)hashOperations.get("100","name");
        System.out.println(name);
        Set keys=hashOperations.keys("100");
        System.out.println(keys);
        List values=hashOperations.values("100");
        System.out.println(values);

        hashOperations.delete("100","age");
    }

}
