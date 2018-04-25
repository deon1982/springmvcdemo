package com.mvcframework.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mvcframework.annotaton.GPAutowired;
import com.mvcframework.annotaton.GPController;
import com.mvcframework.annotaton.GPRequestMapping;
import com.mvcframework.annotaton.GPRequstParam;
import com.mvcframework.service.IDemoService;

@GPController
public class MvcAction {

	@GPAutowired 
	private IDemoService demoService;
	
	@GPRequestMapping("/query.json")
	public void query(HttpServletRequest req,HttpServletResponse resp,@GPRequstParam("name") String name) {
		String result = demoService.get(name);
		try {
			resp.getWriter().println(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
