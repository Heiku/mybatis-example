package com.heiku.mybatisexample.runner;

import com.heiku.mybatisexample.dao.CityDao;
import com.heiku.mybatisexample.entity.City;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @Author: Heiku
 * @Date: 2020/11/12
 */
@Component
public class CityMapperRunner implements CommandLineRunner {

    @Autowired
    private CityDao cityDao;

    @Override
    public void run(String... args) throws Exception {
        City city = new City();
        city.setId(7L);
        city.setName("JieYang");
        cityDao.insert(city);
    }
}
