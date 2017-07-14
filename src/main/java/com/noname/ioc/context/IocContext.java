package com.noname.ioc.context;


import com.noname.ioc.bean.BeanDefinition;
import com.noname.ioc.bean.BeanDefinitionReader;
import com.noname.ioc.bean.BeanDefinitionRegister;
import com.noname.ioc.bean.BeanFactory;

import java.util.Map;


/**
 * Created by zhy on 4/14/17.
 */
public class IocContext implements BeanDefinitionRegister {

    private BeanFactory beanFactory = new BeanFactory();
    private BeanDefinitionReader beanDefinitionReader;

    public IocContext() {
        beanDefinitionReader = new BeanDefinitionReader(this);
    }

    public IocContext(Class<?> configurationClass) {
        this();
        beanDefinitionReader.configure(configurationClass);
    }


    public void registerBean(String name, Object object) {
        beanFactory.registerBean(name, object);
    }

    public void registerBean(Object object) {
        beanFactory.registerBean(object);
    }

    public <T> T getBean(String name) {
        return beanFactory.getBean(name);
    }

    public <T> T getBean(Class beanClass) {
        return beanFactory.getBean(beanClass);
    }

    public Map<String, BeanDefinition> getDefinitions() {
        return beanFactory.getContainer();
    }

}