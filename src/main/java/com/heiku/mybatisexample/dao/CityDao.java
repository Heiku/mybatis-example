package com.heiku.mybatisexample.dao;

import com.heiku.mybatisexample.entity.City;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * CityMapper
 *
 * @Author: Heiku
 * @Date: 2020/11/12
 */
@Mapper
public interface CityDao {

    @Insert("insert into city(id, name) values(#{id}, #{name})")
    void insert(City city);
}
