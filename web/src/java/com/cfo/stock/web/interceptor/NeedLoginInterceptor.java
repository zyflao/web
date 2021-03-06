/**
 * 
 */
package com.cfo.stock.web.interceptor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Properties;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;

import com.cfo.common.constant.XJBGlobals;
import com.cfo.common.session.HttpSessionWrapper;
import com.cfo.common.session.constant.AttributeKeys;
import com.cfo.common.session.constant.SessionConfig;
import com.cfo.common.session.interceptor.SpringMvcSessionInterceptor;
import com.cfo.common.session.utils.CookieUtils;
import com.cfo.common.session.utils.URLUtils;
import com.cfo.stock.web.global.Global;
import com.cfo.stock.web.passport.utils.Constant;
import com.cfo.stock.web.passport.utils.Utility;
import com.cfo.stock.web.util.ActionUtils;
import com.jrj.stocktrade.api.account.UserInfoService;
import com.jrj.stocktrade.api.account.vo.UserInfo;
import com.jrj.stocktrade.api.common.UserStatus;
import com.jrj.stocktrade.api.exception.ServiceException;

/**
 * 验证action必须盈利宝登录
 * NeedLoginInterceptor
 * @history  
 * <PRE>  
 * ---------------------------------------------------------  
 * VERSION       DATE            BY       CHANGE/COMMENT  
 * ---------------------------------------------------------  
 * v1.0         2014-4-15    		yuanlong.wang     create  
 * ---------------------------------------------------------  
 * </PRE> 
 *
 */

public class NeedLoginInterceptor extends SpringMvcSessionInterceptor {
	private static final String LOGIN_RESULT = Global.PASSPORT_LOGIN_URL;
	private static final String ERROR_REQUEST ="/stock/error.jspa";
	private static final String AJAX_LOGIN_RESULT = "_login";
	private static final String cdomin = Constant.ZQT_DOMAIN;
	@Autowired
	UserInfoService userInfoService;
	
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
			Object handler) throws Exception {
		if(log.isDebugEnabled()){
			log.debug("===i'm in=="+request.getContextPath());
		}
		if(handler instanceof HandlerMethod){
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			NeedLogin m_annotation = handlerMethod.getMethod()
					.getAnnotation(NeedLogin.class);
			NeedLogin c_annotation=handlerMethod.getBeanType().getAnnotation(NeedLogin.class);
			if(m_annotation==null&&c_annotation==null){
				return true;
			}
			
			int code = checkUserId(request, response);
			if(log.isDebugEnabled()){
				log.debug("===check userid =="+ code);
			}
			if(code == 1){
				return true;
			}else{
				String isajax   = request.getHeader("x-requested-with");
				if(isajax != null){
					sendAjax("text/plain", AJAX_LOGIN_RESULT, response);
					return false;
				}else{			
					if(request.getRequestURI().startsWith("/stock")){
						// 单独处理from参数
						String from = request.getParameter("from");
						//渠道码
						String tgqdcode = request.getParameter("tgqdcode");
						if(StringUtils.isNotBlank(from)){
							from = URLUtils.ParamterFilter(from,'\0');
						}else {
							from = "ZQT"; // 默认from
						}
						if(StringUtils.isBlank(tgqdcode)){
							Cookie[] cookies = request.getCookies();
							if(cookies!=null){    
							    for(Cookie c : cookies){    
							       if(c.getName().equalsIgnoreCase("channelCode")){
							    	   tgqdcode = c.getValue();    
							        }
							    }
							}
						}
						log.info("redirect  path : " +  LOGIN_RESULT+"?ReturnURL="+ URLEncoder.encode(ActionUtils.getCurrentURL(request), "UTF-8") + "&from=" + from + "&fromId=ZQT&tgqdcode="+URLUtils.ParamterFilter(tgqdcode,'\0'));
						
						if(StringUtils.isNotBlank(tgqdcode)){
							response.sendRedirect(LOGIN_RESULT+"?ReturnURL="+URLEncoder.encode(ActionUtils.getCurrentURL(request), "UTF-8") + "&from=" + from + "&fromId=ZQT&tgqdcode="+URLUtils.ParamterFilter(tgqdcode,'\0'));
						}else{
							response.sendRedirect(LOGIN_RESULT+"?ReturnURL="+URLEncoder.encode(ActionUtils.getCurrentURL(request), "UTF-8") + "&from=" + from + "&fromId=ZQT");
						}
						

					}
				return false;
				}
			}
			
		}else{
			if(request.getRequestURI().startsWith("/stock")){
				response.sendRedirect(ERROR_REQUEST+"?redirect="+ActionUtils.getCurrentURL(request));
			}
			return false;
		}
		
	}
	/**
	 * 验证登录状态
	 * @param request
	 * @param response
	 * @param sessionId
	 */
	private int checkUserId(HttpServletRequest request, HttpServletResponse response){
		HttpSessionWrapper session=getSession(request,response);
		if(session != null){
			
			//调试使用
			if("1".equals(Global.DEBUG_SSO) ) {
				InputStream in = getClass().getClassLoader().getResourceAsStream("debugsso.properties");
				Properties props = new Properties();
				try {
					props.load(in);
					String ssoId = props.getProperty("ssoid");
					session.setAttribute(AttributeKeys.USER_ID, ssoId);
					session.setAttribute(AttributeKeys.LOGIN,
							XJBGlobals.XJB_LOGIN_OK);
					String ssocookie = ActionUtils.getCookie(Constant.SSO_COOKIE_KEY, request);
					String ssiodMd5 = DigestUtils.md5Hex(ssoId + ssocookie+Constant.SSOID_KEY);
					ActionUtils.addCookie("JRJ.SSOPASSPORT", ssiodMd5, cdomin, response);
					ActionUtils.addCookie("zqt_md5", ssiodMd5, cdomin, response);
					ActionUtils.addCookie("zqt_userid", ssoId, cdomin, response);
					return 1;
				} catch (IOException e) {
					log.error("exception",e);
				}
			}//调试使用end
			
			String loginOK=request.getHeader(Global.COOKIE_LOGIN_OK_KEY);
			log.info("获取到header里的登录信息为==>" + loginOK);
			if(!Global.LOGIN_OK.equalsIgnoreCase(loginOK)){//登录标记为不合法
				logOut(session, response);
				return 0;
			}
			
			String cookieUserId=CookieUtils.getCookieByName(request, Global.COOKIE_USER_ID_KEY);
			if(cookieUserId == null){//cookie没有用户ID
				logOut(session, response);
				return 0;
			}
			
			String userId = session.getAttribute(AttributeKeys.USER_ID, String.class);
			if(userId == null){//session中无用户ID，说明未登录证券通
				
				//如果有用户ID，插入用户信息表
				recordUserInfo(cookieUserId);
				session.setAttribute(AttributeKeys.USER_ID, cookieUserId);
				session.setAttribute(AttributeKeys.LOGIN,
						XJBGlobals.XJB_LOGIN_OK);
				
			} else {//已经登录证券通
				if(!userId.equals(cookieUserId)){//判断session中的用户ID不等于cookie中的用户ID
					session.deleteSession();//登出证券通
					recordUserInfo(userId);//如果有用户ID，插入用户信息表
					session.setAttribute(AttributeKeys.USER_ID, cookieUserId);//重新设置登录
					session.setAttribute(AttributeKeys.LOGIN,
							XJBGlobals.XJB_LOGIN_OK);
				}
			}
			
			/*//检验是否有登录标示
			String login =mysession.getAttribute(AttributeKeys.LOGIN, String.class);
			if(login==null||!SessionConfig.LOGIN_OK.equals(login)){
				mysession.deleteSession();
				return 0;
			}
			*/
			//检查当前是否过期
			long lastaccesstime=session.getLastAccessedTime();
			long now=new Date().getTime();
			if(now-lastaccesstime>SessionConfig.expire_time){
				logOut(session, response);
				return 0;
			}else{
				session.refreshSession();
				return 1;
			}
		}
		return 0;
	}

	/**
	 * 如果有用户ID，插入用户信息表
	 * @param userId
	 */
	private void recordUserInfo(String userId){
		if(userId == null) return;
		
		try {
			UserInfo userInfo = userInfoService.queryUserInfo(userId);
			if(userInfo == null){
				userInfoService.createUserInfo(userId, UserStatus.INCOMPLETE);
			}
		} catch (ServiceException e) {
			log.error("#recordUserInfo ==>",e);
		}
	}
	
	
	/**
	 * 发送ajax响应给客户端
	 * 
	 * @param str
	 */
	public void sendAjax(String ContentType, String str,HttpServletResponse response) {
		try {
			ContentType = ContentType == null ? "text/html" : ContentType;
			response.setContentType(ContentType + "; charset=UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			response.getWriter().write(str + "");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 登出方法
	 * @param session
	 * @param response
	 */
	private void logOut(HttpSessionWrapper session, HttpServletResponse response){
		session.deleteSession();
		// 删除passport的cookie
		Utility.delCookie(Constant.ZQT_UID, Constant.ZQT_DOMAIN, response);
		Utility.delCookie(Constant.ZQT_MD5, Constant.ZQT_DOMAIN, response);
		// FIXME: 应该passport去删除cookie
		Utility.delCookie("JRJ.SSOPASSPORT", "jrj.com.cn", response);
	}
	
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface NeedLogin{
		boolean value() default true;
	}
	
}
