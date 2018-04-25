package com.mvcframework.service.impl;

import com.mvcframework.annotaton.GPService;
import com.mvcframework.service.IDemoService;
@GPService
public class DemoServiceImpl implements IDemoService{

	public String get(String name) {
		return "my name is "+name;
	}
}
