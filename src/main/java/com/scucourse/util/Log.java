package com.scucourse.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.text.SimpleDateFormat;
import java.util.Date;


public class Log{
    JdbcTemplate jdbcTemplate;

    public Log () {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://localhost:3306/scucourse?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&zeroDateTimeBehavior=convertToNull");
        dataSource.setUsername("dbuser");
        dataSource.setPassword("258319TBlade!");

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void logAction(String user_id, String course_id, String content) {

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String sql = String.format("INSERT INTO log(user_id, course_id, time, content) VALUES(%s, %s, \"%s\", \"%s\")",
                user_id, course_id, sdf.format(date), content);
        jdbcTemplate.update(sql);
    }
}
