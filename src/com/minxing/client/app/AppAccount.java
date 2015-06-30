package com.minxing.client.app;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.minxing.client.http.HttpClient;
import com.minxing.client.http.Response;
import com.minxing.client.json.JSONArray;
import com.minxing.client.json.JSONException;
import com.minxing.client.json.JSONObject;
import com.minxing.client.model.Account;
import com.minxing.client.model.ApiErrorException;
import com.minxing.client.model.MxException;
import com.minxing.client.model.MxVerifyException;
import com.minxing.client.model.PostParameter;
import com.minxing.client.model.ShareLink;
import com.minxing.client.ocu.Message;
import com.minxing.client.ocu.TextMessage;
import com.minxing.client.organization.Department;
import com.minxing.client.organization.Network;
import com.minxing.client.organization.User;
import com.minxing.client.utils.HMACSHA1;
import com.minxing.client.utils.UrlEncoder;

public class AppAccount extends Account {

	// protected String appId = AppConfig.getValue("AppID");
	// protected String appSecret = AppConfig.getValue("AppSecret");
	// protected String redirectUri = AppConfig.getValue("redirect_uri");
	// protected String authorizeUrl = AppConfig.getValue("authorize_url");
	//
	// protected String apiPrefix = AppConfig.getValue("api_prefix");
	// protected String accessToken = AppConfig.getValue("access_token");

	private String _token = null;
	private String _loginName;
	private String _serverURL;
	private long _currentUserId = 0;
	private String app_id;
	private String secret;

	private AppAccount(String serverURL, String token) {
		this._serverURL = serverURL;
		this._token = token;
		client.setToken(this._token);
		client.setTokenType("Bearer");
	}

	private AppAccount(String serverURL, String app_id, String secret) {
		this._serverURL = serverURL;
		this.app_id = app_id;
		this.secret = secret;
		client.setTokenType("MAC");
	}

	private AppAccount(String serverURL, String loginName, String password,
			String clientId) {
		this._serverURL = serverURL;
		PostParameter grant_type = new PostParameter("grant_type", "password");
		PostParameter login_name = new PostParameter("login_name", loginName);
		PostParameter passwd = new PostParameter("password", password);
		PostParameter app_id = new PostParameter("app_id", clientId);
		PostParameter[] params = new PostParameter[] { grant_type, login_name,
				passwd, app_id };

		HttpClient _client = new HttpClient();
		Response return_rsp = _client.post(serverURL + "/oauth2/token", params,
				new PostParameter[] {}, false);

		if (return_rsp.getStatusCode() == 200) {

			JSONObject o = return_rsp.asJSONObject();
			try {
				_token = o.getString("access_token");
				client.setToken(this._token);
				client.setTokenType("Bearer");

			} catch (JSONException e) {
				throw new MxException("解析返回值出错", e);
			}
		} else {
			throw new MxException("HTTP " + return_rsp.getStatusCode() + ": "
					+ return_rsp.getResponseAsString());
		}

	}

	/**
	 * 设置API调用的用户身份，消息按照这个身份发出
	 * 
	 * @param loginName
	 *            登录名
	 */
	public void setFromUserLoginName(String loginName) {
		this._loginName = loginName;

	}

	/**
	 * 设置API调用的用户身份，消息按照这个身份发出
	 * 
	 * @param userId
	 *            用户对象的Id.
	 */
	public void setFromUserId(long userId) {
		this._currentUserId = userId;
	}

	/**
	 * 使用用Token登录系统
	 * 
	 * @param serverURL
	 *            服务器的访问地址
	 * @param bearerToken
	 *            bearerToken
	 * @return
	 */
	public static AppAccount loginByAccessToken(String serverURL, String bearerToken) {
		return new AppAccount(serverURL, bearerToken);
	}

	/**
	 * 使用接入端的方式登录系统，
	 * @param serverURL 系统的url.
	 * @param app_id 接入端应用的Id,在接入端管理的页面上可以找到。
	 * @param secret 接入端应用的秘钥，可以在接入端的页面上看到。
	 * @return
	 */
	public static AppAccount loginByAppSecret(String serverURL, String app_id,
			String secret) {
		return new AppAccount(serverURL, app_id, secret);
	}

	/**
	 * 使用用户名密码方式登录系统
	 * 
	 * @param serverURL
	 *            服务器的访问地址
	 * @param loginName
	 *            系统登录名
	 * @param password
	 *            用户密码
	 * @param clientId
	 *            使用的注册客户端，可以设置为4,表示PC的客户端。
	 * @return
	 */
	public static AppAccount loginByPassword(String serverURL,
			String loginName, String password, String clientId) {

		return new AppAccount(serverURL, loginName, password, clientId);
	}

	////////////////////////////////////////////////////////////////////////////
	/**
	 *  before request.
	 */
	@Override
	protected String beforeRequest(String url, List<PostParameter> paramsList,
			List<PostParameter> headersList) {

		if (this._currentUserId != 0L) {
			PostParameter as_user = new PostParameter("X_AS_USER",
					this._currentUserId);
			headersList.add(as_user);
		} else if (this._loginName != null && this._loginName.length() > 0) {
			PostParameter as_user = new PostParameter("X_AS_USER",
					this._loginName);
			headersList.add(as_user);
		}

		String _url = "";
		
		if (url.trim().startsWith("http://")
				|| url.trim().startsWith("https://")) {
			_url = url;
		} else {
			if (!url.trim().startsWith("/")) {
				url = "/" + url.trim();
			}
			// url = rootUrl + apiPrefix + url;
			url = _serverURL + url;
			_url = url;
		}

		long time = System.currentTimeMillis();
		String token = UrlEncoder.encode(this.app_id
				+ ":"
				+ HMACSHA1.getSignature(_url + "?timestamp=" + time,
						this.secret));
		client.setToken(token);
		client.setTokenType("MAC");
		headersList.add(new PostParameter("timestamp", "" + time));

		return _url;
	}
	
	//////////////////////////////////////////////////////////////////////

	public JSONObject get(String url, Map<String, String> params)
			throws MxException {
		PostParameter[] pps = createParams(params);
		return this.get(url, pps);
	}

	public JSONObject post(String url, Map<String, String> params,
			Map<String, String> headers) throws MxException {
		PostParameter[] pps = createParams(params);
		PostParameter[] hs = createParams(headers);
		return this.post(url, pps, hs, true);
	}

	public JSONArray post(String url, Map<String, String> params,
			Map<String, String> headers, File file) throws MxException {
		PostParameter[] pps = createParams(params);
		PostParameter[] hs = createParams(headers);
		return this.post(url, pps, hs, file, true);
	}

	public JSONObject put(String url, Map<String, String> params)
			throws MxException {
		PostParameter[] pps = createParams(params);
		return this.put(url, pps);
	}

	public JSONObject delete(String url, Map<String, String> params)
			throws MxException {
		PostParameter[] pps = createParams(params);
		return this.delete(url, pps);
	}

	private PostParameter[] createParams(Map<String, String> params) {
		if (params == null) {
			return new PostParameter[0];
		}
		PostParameter[] pps = new PostParameter[params.size()];
		int i = 0;
		for (Iterator<String> it = params.keySet().iterator(); it.hasNext();) {
			String key = it.next();
			String value = params.get(key);
			PostParameter p = new PostParameter(key, value);
			pps[i++] = p;
		}
		return pps;
	}

	public long[] uploadConversationFile(String conversation_id, File file) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("conversation_id", conversation_id);
		Map<String, String> headers = new HashMap<String, String>();

		JSONArray arr = null;
		long[] filesArray = new long[] {};
		try {
			arr = this.post("api/v1/uploaded_files", params, headers, file);
			filesArray = new long[arr.length()];
			for (int i = 0; i < arr.length(); i++) {
				JSONObject o = arr.getJSONObject(i);
				filesArray[i] = o.getLong("id");
			}

		} catch (Exception e) {
			throw new MxException(e);
		}

		return filesArray;
	}

	/**
	 * 获得某个用户的Id.
	 * 
	 * @param networkname
	 *            如果是全网管理员身份，请给出账户用户所在的网络。
	 * @param loginname
	 *            用户登录名
	 * @return 用户的Id.
	 */
	public Long getIdByLoginname(String networkname, String loginname) {

		try {
			JSONObject o = this.get("/api/v1/users/" + loginname
					+ "/by_login_name?network_name=" + networkname);
			return o.getLong("id");
		} catch (Exception e) {
			throw new MxException(e);
		}
	}

	public User findUserByLoginname(String loginname) {

		try {
			JSONObject o;

			o = this.get("/api/v1/users/"
					+ URLEncoder.encode(loginname, "UTF-8") + "/by_login_name");

			User user = new User();
			user.setId(o.getLong("id"));
			user.setLoginName(o.getString("login_name"));
			user.setPassword(o.getString("password"));
			user.setEmail(o.getString("email"));
			user.setName(o.getString("name"));
			user.setTitle(o.getString("login_name"));
			user.setCellvoice1(o.getString("cellvoice1"));
			user.setCellvoice2(o.getString("cellvoice2"));
			user.setWorkvoice(o.getString("workvoice"));
			user.setEmpCode(o.getString("emp_code"));
			user.setDeptCode(o.getString("dept_code"));

			user.setHidden(o.getString("hidden"));
			user.setSuspended(o.getString("suspended"));

			return user;
		} catch (JSONException e) {
			throw new MxException("解析Json出错.", e);
		} catch (UnsupportedEncodingException e) {
			throw new MxException("编码URL出错.", e);
		}

	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////
	// Send messages
	//

	/**
	 * 
	 * @param sender_login_name
	 *            发送用户的账户名字，该账户做为消息的发送人
	 * @param conversation_id
	 *            会话的Id
	 * @param message
	 *            消息内容
	 * @return
	 */
	public TextMessage sendConversationMessage(String conversation_id,
			String message) {
		// 会话id，web上打开一个会话，从url里获取。比如社区管理员创建个群聊，里面邀请几个维护人员进来

		Map<String, String> params = new HashMap<String, String>();
		params.put("body", message);
		Map<String, String> headers = new HashMap<String, String>();

		JSONObject return_json = this.post("/api/v1/conversations/"
				+ conversation_id + "/messages", params, headers);

		return TextMessage.fromJSON(return_json);

	}

	public TextMessage sendConversationFileMessage(String conversation_id,
			File file) {
		long[] file_ids = uploadConversationFile(conversation_id, file);
		Map<String, String> params = new HashMap<String, String>();
		for (int i = 0; i < file_ids.length; i++) {
			params.put("attached[]",
					String.format("uploaded_file:%d", file_ids[i]));
		}
		Map<String, String> headers = new HashMap<String, String>();

		JSONObject return_json = this.post("/api/v1/conversations/"
				+ conversation_id + "/messages", params, headers);
		return TextMessage.fromJSON(return_json);
	}

	public TextMessage sendTextMessageToGroup(long groupId, String message) {
		return sendTextMessageToGroup(groupId, message, null);
	}

	public TextMessage sendSharelinkToGroup(long groupId, String message,
			ShareLink shareLink) {
		return sendTextMessageToGroup(groupId, message, shareLink.toJson());
	}

	public TextMessage sendTextMessageToGroup(long groupId, String message,
			String story) {

		Map<String, String> params = new HashMap<String, String>();
		params.put("group_id", String.valueOf(groupId));
		params.put("body", message);

		if (story != null) {
			params.put("story", story);
		}

		Map<String, String> headers = new HashMap<String, String>();

		JSONObject new_message = this.post("/api/v1/messages", params, headers);
		return TextMessage.fromJSON(new_message);

	}

	public TextMessage sendMessageToUser(User u, String message) {
		// 会话id，web上打开一个会话，从url里获取。比如社区管理员创建个群聊，里面邀请几个维护人员进来
		if (u.getId() == null || u.getId() == 0) {
			String login_name = u.getLoginName();
			if (login_name == null) {
				throw new MxException("User参数缺少id或者loginName属性.");
			}
			User user = findUserByLoginname(login_name);
			if (user == null) {
				throw new MxException("找不到对应" + login_name + "的用户。");
			}
			System.out.println("=>" + user);
			u.setId(user.getId());
		}

		return sendMessageToUser(u.getId(), message);

	}

	public TextMessage sendMessageToUser(long toUserId, String message) {
		// 会话id，web上打开一个会话，从url里获取。比如社区管理员创建个群聊，里面邀请几个维护人员进来

		Map<String, String> params = new HashMap<String, String>();
		params.put("body", message);
		Map<String, String> headers = new HashMap<String, String>();

		JSONObject new_message = this.post("/api/v1/conversations/to_user/"
				+ toUserId, params, headers);
		return TextMessage.fromJSON(new_message);
	}

	/**
	 * 发送公众号消息
	 * 
	 * @param toUserIds
	 *            用户的login_name列表，用逗号分割,例如id1,id2
	 * @param message
	 *            消息对象数据，可以是复杂文本，也可以是简单对象
	 * @param ocuId
	 *            公众号的id
	 * @param ocuSecret
	 *            公众号的秘钥，校验是否可以发送
	 * @return
	 */
	public int sendOcuMessageToUsers(String[] toUserIds, Message message,
			String ocuId, String ocuSecret) {
		// 会话id，web上打开一个会话，从url里获取。比如社区管理员创建个群聊，里面邀请几个维护人员进来

		Map<String, String> params = new HashMap<String, String>();
		params.put("body", message.getBody());
		params.put("content_type", String.valueOf(message.messageType()));

		StringBuffer sb = new StringBuffer();
		int i = 0;
		for (String id : toUserIds) {
			sb.append(id);
			if (i > 0) {
				sb.append(",");
			}
			i++;
		}

		String direct_to_user_ids = sb.toString();

		params.put("direct_to_user_ids", direct_to_user_ids);
		params.put("ocu_id", ocuId);
		params.put("ocu_secret", ocuSecret);
		Map<String, String> headers = new HashMap<String, String>();

		JSONObject result_json = this.post(
				"/api/v1/conversations/ocu_messages", params, headers);

		try {
			return result_json.getInt("count");
		} catch (JSONException e) {
			throw new MxException("解析Json出错.", e);
		}

	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////
	//
	//

	/**
	 * 创建任意用户的Web端 SSOToken,使用这个API，需要接入端能够拥有创建SSOToken的权限
	 * 
	 * @param loginName
	 *            需要创建token的账户loginName.
	 * @return 正常调用将返回 Web端的SSOToken.
	 */
	public String createMXSSOToken(String loginName) {

		Map<String, String> params = new HashMap<String, String>();
		params.put("login_name", loginName);

		Map<String, String> headers = new HashMap<String, String>();

		try {
			JSONObject json = this.post("/api/v1/oauth/mx_sso_token", params,
					headers);
			return json.getString("token");

		} catch (JSONException e) {
			throw new MxException(e);
		}

	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////
	//
	//

	/**
	 * 向移动设备推送自定义的消息
	 * 
	 * @param user_ids
	 *            发送的消息，文本格式，使用','分割，例如'1,2,3'
	 * @param message
	 *            发送的消息，文本格式，可以自定内容的编码，系统会将内容发送到接受的移动设备上。
	 * @param alert
	 *            iOS通知栏消息，对Android无效，走Apple的Apn发送出去。文本格式,例如'您收到一条新消息'
	 * @param alert_extend
	 *            iOS apn推送的隐藏字段，放在custom字段,
	 *            json的字段,例如:"{'a': '1920-10-11 11:20'}"。
	 * @return 实际发送了多少个用户，user_ids中有无效的用户将被剔除。
	 * @throws ApiErrorException
	 *             当调用数据出错时抛出。
	 */
	public int pushMessage(String user_ids, String message, String alert,
			String alert_extend) throws ApiErrorException {

		try {

			HashMap<String, String> params = new HashMap<String, String>();
			params.put("user_ids", user_ids);
			params.put("message", message);
			params.put("alert", alert);
			params.put("alert_extend", alert_extend);

			Map<String, String> headers = new HashMap<String, String>();

			JSONObject json_result = post("/api/v1/push", params, headers);
			int send_to = json_result.getInt("send_count");

			return send_to;

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	//
	// Department Api
	//
	public Department createDepartment(Department departement)
			throws ApiErrorException {

		try {

			HashMap<String, String> params = departement.toHash();
			Map<String, String> headers = new HashMap<String, String>();

			JSONObject json_result = post("/api/v1/departments", params,
					headers);
			int code = json_result.getInt("code");

			if (code > 0 && code != 200 && code != 201) {

				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}

			departement.setId(json_result.getLong("id"));
			departement.setNetworkId(json_result.getLong("network_id"));

			return departement;

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	public void updateDepartment(Department departement)
			throws ApiErrorException {

		try {

			HashMap<String, String> params = departement.toHash();

			JSONObject json_result = put("/api/v1/departments", params);

			int code = json_result.getInt("code");

			if (code != 200 && code != 201) {

				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	public void deleteDepartment(String departmentCode, boolean deleteWithUsers)
			throws ApiErrorException {

		try {

			HashMap<String, String> params = new HashMap<String, String>();
			if (deleteWithUsers) {
				params.put("force", "true");
			}

			JSONObject json_result = delete("/api/v1/departments/"
					+ departmentCode, params);
			int code = json_result.getInt("code");

			if (code != 200 && code != 201) {

				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	//
	// User Api
	//
	public User addNewUser(User user) throws ApiErrorException {

		try {

			HashMap<String, String> params = user.toHash();
			Map<String, String> headers = new HashMap<String, String>();

			JSONObject json_result = post("/api/v1/users", params, headers);
			int code = json_result.getInt("code");

			if (code > 0 && code != 200 && code != 201) {

				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}
			user.setId(json_result.getLong("id"));
			return user;

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	public void updateUser(User user) throws ApiErrorException {

		HashMap<String, String> params = user.toHash();
		put("/api/v1/users/" + user.getId(), params);

	}

	public void deleteUser(User user) throws ApiErrorException {
		deleteUser(user, false);
	}

	public void deleteUserWithAccount(User user) throws ApiErrorException {
		deleteUser(user, true);
	}

	public void deleteUserByLoginName(String loginName)
			throws ApiErrorException {
		User u = new User();
		u.setLoginName(loginName);
		deleteUser(u, false);
	}

	private void deleteUser(User user, boolean withDeleteAccount)
			throws ApiErrorException {

		try {

			HashMap<String, String> params = new HashMap<String, String>();
			params.put("login_name", user.getLoginName());
			if (withDeleteAccount) {
				params.put("with_account", "true");
			}

			JSONObject json_result = delete("/api/v1/users/" + user.getId(),
					params);
			int code = json_result.getInt("code");

			if (code != 200 && code != 201) {

				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	//
	// Network Api
	//
	public Network createNetwork(Network network) throws ApiErrorException {

		try {

			HashMap<String, String> params = network.toHash();
			Map<String, String> headers = new HashMap<String, String>();

			JSONObject json_result = post("/api/v1/networks", params, headers);
			int code = json_result.getInt("code");

			if (code > 0 && code != 200 && code != 201) {

				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}

			network.setId(json_result.getLong("id"));
			return network;

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	public void updateNetwork(Network network) throws ApiErrorException {

		try {

			HashMap<String, String> params = network.toHash();

			JSONObject json_result = put("/api/v1/networks", params);

			int code = json_result.getInt("code");

			if (code != 200 && code != 201) {

				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	public void deleteNetwork(String name) throws ApiErrorException {

		try {

			HashMap<String, String> params = new HashMap<String, String>();
			params.put("name", name);

			JSONObject json_result = delete("/api/v1/networks", params);
			int code = json_result.getInt("code");

			if (code != 200 && code != 201) {
				String msg = json_result.getString("message");
				throw new ApiErrorException(code, msg);

			}

		} catch (JSONException e) {
			throw new ApiErrorException("返回JSON错误", 500, e);
		}

	}

	/**
	 * 校验应用商店的应用携带的SSOTOken是否有效，通过连接minxing服务器，检查token代表的敏行用户的身份。
	 * 
	 * @param token
	 *            客户端提供的SSO Token.
	 * @param app_id
	 *            校验客户端提供的Token是不是来自这个app_id产生的，如果不是，则校验失败。
	 * @return 如果校验成功，返回token对应的用户信息
	 * @throws MxVerifyException
	 *             校验失败，则抛出这个异常.
	 */
	public User verifyAppSSOToken(String token, String app_id)
			throws MxVerifyException {

		try {
			JSONObject o = this.get("/api/v1/oauth/user_info/" + token);

			User user = new User();
			user.setId(o.getLong("user_id"));
			user.setLoginName(o.getString("login_name"));

			user.setEmail(o.getString("email"));
			user.setName(o.getString("name"));
			user.setTitle(o.getString("login_name"));
			user.setCellvoice1(o.getString("cell_phone"));
			user.setCellvoice2(o.getString("preferred_mobile"));

			user.setEmpCode(o.getString("emp_code"));
			user.setNetworkId(o.getLong("network_id"));

			return user;
		} catch (JSONException e) {
			throw new MxVerifyException("校验Token:" + token + "错误", e);
		}

	}

	/**
	 * 校验公众号消息打开时携带的 SSOTOken，通过连接minxing服务器，检查token代表的敏行用户的身份。
	 * 
	 * @param token
	 *            客户端提供的SSO Token.
	 * @param app_id
	 *            校验客户端提供的Token是不是来自这个app_id产生的，如果不是，则校验失败。
	 * @return 如果校验成功，返回token对应的用户信息
	 * @throws MxVerifyException
	 *             校验失败，则抛出这个异常.
	 */

	public User verifyOcuSSOToken(String token, String ocu_id) throws MxVerifyException {

		try {
			JSONObject o = this.get("/api/v1/oauth/user_info/" + token);

			User user = new User();
			user.setId(o.getLong("user_id"));
			user.setLoginName(o.getString("login_name"));

			user.setEmail(o.getString("email"));
			user.setName(o.getString("name"));
			user.setTitle(o.getString("login_name"));
			user.setCellvoice1(o.getString("cell_phone"));
			user.setCellvoice2(o.getString("preferred_mobile"));

			user.setEmpCode(o.getString("emp_code"));
			user.setNetworkId(o.getLong("network_id"));

			return user;
		} catch (Exception e) {
			throw new MxVerifyException("校验Token:" + token + "错误", e);
			
		}

	}

}