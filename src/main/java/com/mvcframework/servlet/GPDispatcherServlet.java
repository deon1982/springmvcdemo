package com.mvcframework.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mvcframework.annotaton.GPAutowired;
import com.mvcframework.annotaton.GPController;
import com.mvcframework.annotaton.GPRequestMapping;
import com.mvcframework.annotaton.GPRequstParam;
import com.mvcframework.annotaton.GPService;

public class GPDispatcherServlet extends HttpServlet{

	private static final long serialVersionUID = 3788279972938793265L;
	
	private Properties p = new Properties();
	
	private List<String> classNames = new ArrayList<>();
	
	private Map<String,Object> ioc = new HashMap<>();
	
	private List<Handler> handlerMapping = new ArrayList<>();
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		super.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			doDispatch(req, resp);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		//加载配置文件
//		doLoadConfig(config.getInitParameter("contextLocation"));
		//扫描所有相关的类
//		doScanner(p.getProperty("scanPackage"));
		doScanner("com.mvcframework");
		//将相关的class初始化，并将其保存到ioc容器
		doInstance();
		//自动化依赖注入
		doAutowired();
		//初始化handlerMapping
		initHandlerMapping();
		//
//		doDispatch();
		
	}

	private void doLoadConfig(String location) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
		try {
			p.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(null !=is)
			{
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	private void doScanner(String packageName) {
		URL url = this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.", "/"));
		File classesDir = new File(url.getFile());
		for(File file : classesDir.listFiles()) {
			if(file.isDirectory()) {
				doScanner(packageName+"."+file.getName());
			}else {
				String className = packageName+"."+file.getName().replace(".class", "");
				classNames.add(className);
			}
			
			
		}
		
		
	}
	private void doInstance() {
		if(classNames.isEmpty()) {return;}
		try {
			for(String className : classNames) {
				
				Class<?> clazz = Class.forName(className);
				if(clazz.isAnnotationPresent(GPController.class)) {
					
					String beanName = lowerFirst(clazz.getSimpleName());
					
					ioc.put(beanName, clazz.newInstance());
					
				}else if(clazz.isAnnotationPresent(GPService.class)){
					//beanName beanId
					//1默认采用类名的首字母小写
					//2如果是自己定义了名字的话,优先使用自己定义的名字
					//根据类型匹配
					GPService service =clazz.getAnnotation(GPService.class);
					String beanName = service.value();
					if("".equals(beanName.trim())) {
						beanName = lowerFirst(clazz.getSimpleName());
					}
					
					Object instance = clazz.newInstance();
					ioc.put(beanName,instance);
					
					Class<?>[] interfaces = clazz.getInterfaces();
					for(Class i : interfaces) {
						ioc.put(i.getName(), instance);
					}
					
				}else {
					continue;
				}
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	private void doAutowired() {
		if(ioc.isEmpty()) {return;}
		for(Entry<String,Object> entry : ioc.entrySet()) {
			
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for(Field filed : fields) {
				if(!filed.isAnnotationPresent(GPAutowired.class)) {continue;}
				//时间 ：01:20:20
				GPAutowired autowired = filed.getAnnotation(GPAutowired.class);
				String beanName = autowired.value().trim();
				if("".equals(beanName)) {//如果是接口
					beanName = filed.getType().getName();
				}
				
				filed.setAccessible(true);
				try {
					filed.set(entry.getValue(),ioc.get(beanName));
				}catch(Exception e) {
					e.printStackTrace();
					continue;
				}
				
			}
			
		}
		
		
		
	}
	private void initHandlerMapping() {
		if(ioc.isEmpty()) {return;}
		
		for(Entry<String,Object> entry : ioc.entrySet()) {
			
			Class<?> clazz = entry.getValue().getClass();
			if(!clazz.isAnnotationPresent(GPController.class)) {continue;}
			String url = "";
			//获得controller的url配置
			if(clazz.isAnnotationPresent(GPRequestMapping.class)) {
				GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
				url = requestMapping.value();	
			}
			//获取method的url配置
			Method[] methods = clazz.getMethods();
			for(Method method : methods) {
				
				//没有加GPRequestMapping注解的直接忽略
				if(!method.isAnnotationPresent(GPRequestMapping.class)) {continue;}
				//影射url
				GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
				String regex = ("/"+url+requestMapping.value()).replaceAll("/+","/");
				Pattern pattern = Pattern.compile(regex);
				handlerMapping.add(new Handler(pattern,entry.getValue(),method));
				
			}
		}
	}
	private void doDispatch(HttpServletRequest req,HttpServletResponse resp) throws Exception{
		try {
			Handler handler = getHandler(req);
			if(null ==handler) {
				resp.getWriter().write("404 not found");
				return;
			}
			//获取方法的参数列表
			Class<?>[] paramTypes = handler.method.getParameterTypes();
			//保存所有需要自动赋值的参数
			Object[] paramVales = new Object[paramTypes.length];
			
			Map<String,String[]> params = req.getParameterMap();
			for(Entry<String,String[]> param : params.entrySet()) {
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
				//如果找到匹配的对象，则开始填充参数值
				if(!handler.paramIndexMapping.containsKey(param.getKey())) {continue;}
				int index = handler.paramIndexMapping.get(param.getKey());
				paramVales[index] = convert(paramTypes[index],value);
			}
			//设置方法中的request和response对象
			int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramVales[reqIndex] = req;
			int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramVales[respIndex] = resp;	
			
			handler.method.invoke(handler.controller,paramVales);
			
		}catch(Exception e) {
			
		}
		
	}

	private String lowerFirst(String str) {
		char[] chars = str.toCharArray();
		chars[0]+=32;
		return String.valueOf(chars);
	}
	
	/**记录controller中的requestmapping和method的对应关系
	 * @author Administrator
	 *
	 */
	private class Handler{
		protected Object controller;//保存方法对应的实例
		protected Method method;//保存映射的方法
		protected Pattern pattern;//
		protected Map<String,Integer> paramIndexMapping;//参数顺序
		
		/**
		 * @param pattern
		 * @param controller
		 * @param method
		 */
		protected Handler(Pattern pattern,Object controller,Method method) {
			this.controller=controller;
			this.pattern=pattern;
			this.method=method;
			paramIndexMapping = new HashMap<>();
			putParamIndexMapping(method);
		}
		
		private void putParamIndexMapping(Method method) {
			//提取方法中加了注解的参数
			Annotation [] [] pa = method.getParameterAnnotations();
			for(int i = 0;i<pa.length;i++) {
				for(Annotation a : pa[i]) {
					if(a instanceof GPRequstParam) {
						String paramName = ((GPRequstParam) a).value();
						if(!"".equals(paramName.trim())) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			//提取方法中request和response参数
			Class<?> [] paramTypes = method.getParameterTypes();
			for(int i=0;i<paramTypes.length;i++) {
				Class<?> type = paramTypes[i];
				if(type==HttpServletRequest.class||
				   type==HttpServletResponse.class) {
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
	}
	
	private Handler getHandler(HttpServletRequest req) throws Exception{
		if(handlerMapping.isEmpty()) {return null;}
		
		String url = req.getRequestURI();
		
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
		for(Handler handler : handlerMapping) {
			try {
				Matcher matcher = handler.pattern.matcher(url);
				//没有匹配继续匹配下个
				if(!matcher.matches()) {continue;}
				return handler;
			}catch (Exception e) {
				throw e;
			}
			
		}
		return null;
	}
	
	private Object convert(Class<?> type ,String value) {
		if(Integer.class==type) {
			return Integer.valueOf(value);
		}
		return value;
	}
}
