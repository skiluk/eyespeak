package com.eyespeak;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.lang3.math.NumberUtils;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.GroupGrantee;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Permission;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.eyespeak.model.ReturnMessage;
import com.eyespeak.model.User;

public class ServletBase extends HttpServlet {
	private static final long serialVersionUID = 1L;
	protected DataSource bibleDB;
	private SimpleDateFormat localFormat = null, gmtFormat = null;
	private int defaultTimeZoneOffset = 0; 
	private ExecutorService cacheService = null;

	// aws - user: ?
	private static final String ACCESS_KEY_ID = "";
	private static final String SECRET_KEY = "";

	protected static int ErrorNone = 200;
	protected static int ErrorUnauthorized = 401;
	protected static int ErrorNotActivated = 423;

    public ServletBase() {
        super();
    }
    
    @Override
    public void init() {
    	defaultTimeZoneOffset = TimeZone.getDefault().getOffset(new Date().getTime());
		TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
		
		try {
			Context initContext  = new InitialContext();
			bibleDB = (DataSource)initContext.lookup("java:/comp/env/jdbc/bible-mysql");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
        localFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);
        gmtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);
        gmtFormat.setTimeZone(TimeZone.getTimeZone("GMT")); 
    }
    
    public int getTimeZoneOffset() {
    	return defaultTimeZoneOffset;
    }
    
	protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
		
	}

	public byte[] compress(byte[] data) throws IOException { 
		Deflater deflater = new Deflater(); 
		deflater.setInput(data); 
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);  
		deflater.finish(); 
		byte[] buffer = new byte[1024];  
		while (!deflater.finished()) { 
			int count = deflater.deflate(buffer); // returns the generated code... index 
			outputStream.write(buffer, 0, count);  
		} 
		outputStream.close(); 
		byte[] output = outputStream.toByteArray(); 
		return output; 
	} 
	
	public byte[] decompress(byte[] data) throws IOException, DataFormatException { 
		Inflater inflater = new Inflater();  
		inflater.setInput(data); 
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length); 
		byte[] buffer = new byte[1024]; 
		while (!inflater.finished()) { 
			int count = inflater.inflate(buffer); 
			outputStream.write(buffer, 0, count); 
		} 
		outputStream.close(); 
		byte[] output = outputStream.toByteArray(); 
		return output; 
	} 
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (!isValidKey(request, response)) {
			return;
		}
		
		try {
			get(request, response);
		}
		catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	protected void put(HttpServletRequest request, HttpServletResponse response) throws Exception {
		
	}

	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (!isValidKey(request, response)) {
			return;
		}
		
		try {
			put(request, response);
		}
		catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	protected void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
		
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (!isValidKey(request, response)) {
			return;
		}
		
		try {
			post(request, response);
		}
		catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
	
	protected String getParmString(HttpServletRequest request, String name) {
		return getParmString(request, name, null);
	}
	
	protected User getUser(String email) throws SQLException {
		User u = null;
		
		Connection c = bibleDB.getConnection();
		PreparedStatement stmt = c.prepareStatement("select userId, active from users where email = ?");

		stmt.setString(1, email);
		ResultSet rs = stmt.executeQuery();
		if (rs.next()) {
			u = new User();
			u.userId = rs.getInt(1);
			u.active = (rs.getInt(2) > 0) ? true : false;
		}
		rs.close();
		stmt.close();
		c.close();

		return u;
	}
	
	protected ReturnMessage checkUser(String email, String password, String externalId) throws SQLException, UnsupportedEncodingException {
		ReturnMessage msg = new ReturnMessage();
		msg.code = ErrorUnauthorized;
		msg.message = "Not Authenticated";

		Connection c = bibleDB.getConnection();
		PreparedStatement stmt;
		boolean verified = false;
		int active = 0;
		
		if (externalId != null && externalId.length() > 0) {
			stmt = c.prepareStatement("select userId, active from users where externalId = ?");
			stmt.setString(1, externalId);
			
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				verified = true;
				msg.userId = rs.getInt(1);
				active = rs.getInt(2);
			}
			rs.close();
			stmt.close();
		}
		else {
			stmt = c.prepareStatement("select userId, password, salt, active from users where email = ?");
			stmt.setString(1, email);
			
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				String dbPassword = rs.getString(2);
				String salt = rs.getString(3);
				
				if (dbPassword != null && salt != null && password != null) {
					// check password 
					String encPassword = encode(password, salt);

					if (encPassword.compareTo(dbPassword) == 0) {
						verified = true;
						msg.userId = rs.getInt(1);
						active = rs.getInt(4);
					}
				}				
			}
			rs.close();
			stmt.close();
		}

		if (verified) {
			if (active == 1) {
				msg.code = ErrorNone;
				msg.message = "Authenticated";
			}
			else {
				msg.code = ErrorNotActivated;
				msg.message = "Authenticated, but not active. Check E-mail for activation link.";					
			}
		}

		c.close();

		return msg;
	}
	
    protected Date parseDateString(String datestring) throws ParseException { // 2011-01-19 00:20:19 +0000
        return localFormat.parse(datestring);
    }

    protected String toDateString(Date date) {
        return gmtFormat.format(date);
    }
    
	protected String getParmString(HttpServletRequest request, String name, String defaultValue) {
		String value = defaultValue;
		
		String parm = request.getParameter(name);
		if (parm != null && parm.length() > 0) {
			value = parm;
		}
		
		return value;
	}

	protected Long getParmLong(HttpServletRequest request, String name) {
		return getParmLong(request, name, -1L);
	}

	protected Long getParmLong(HttpServletRequest request, String name, Long defaultValue) {
		Long value = defaultValue;
		
		String parm = request.getParameter(name);
		if (parm != null && parm.length() > 0) {
			value = Long.parseLong(parm);
		}
		
		return value;	
	}

	protected int getParmInt(HttpServletRequest request, String name) {
		return getParmInt(request, name, -1);
	}
	
	protected int getParmInt(HttpServletRequest request, String name, int defaultValue) {
		int value = defaultValue;
		
		String parm = request.getParameter(name);
		if (parm != null && parm.length() > 0) {
			value = Integer.parseInt(parm);
		}
		
		return value;
	}

	protected Gson getUTCGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Date.class, new GmtDateTypeAdapter());

		return builder.create();
	}
	
	private String getJSON(Object object){
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Date.class, new GmtDateTypeAdapter());

		Gson gson = builder.create();
		return gson.toJson(object);
	}

	private static class GmtDateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
		private final DateFormat dateFormat;
		
		private GmtDateTypeAdapter() {
			dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZ", Locale.US);
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		}

		@Override
		public synchronized JsonElement serialize(Date date, Type type, JsonSerializationContext jsonSerializationContext) {
			synchronized (dateFormat) {
				String dateFormatAsString = dateFormat.format(date);
				return new JsonPrimitive(dateFormatAsString);
			}
		}

		@Override
		public synchronized Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
			try {
				synchronized (dateFormat) {
					return dateFormat.parse(jsonElement.getAsString());
				}
			} catch (ParseException e) {
				throw new JsonSyntaxException(jsonElement.getAsString(), e);
			}
		}
	}
	
	protected void cacheResponse(String cacheKey, String json) {
		if (cacheKey != null && cacheKey.length() > 0 && json != null && json.length() > 0) {
			if (cacheService == null) {
				cacheService = Executors.newSingleThreadExecutor();
			}
			
			cacheService.execute(new Runnable() {

				@Override
				public void run() {
					// save results to S3
					try {
						byte[] buffer = new byte[100000];

						ByteArrayOutputStream baos = new ByteArrayOutputStream(100000);
						GZIPOutputStream gzos =  new GZIPOutputStream(baos);
						ByteArrayInputStream in =  new ByteArrayInputStream(json.getBytes("UTF-8"));

						int len;
						while ((len = in.read(buffer)) > 0) {
							gzos.write(buffer, 0, len);
						}

						in.close();

						gzos.finish();
						gzos.close();

						byte[] bytes = baos.toByteArray();
						TransferManager tx = new TransferManager(new BasicAWSCredentials(ACCESS_KEY_ID, SECRET_KEY));
						ObjectMetadata omd = new ObjectMetadata();
						omd.setContentType("text/plain");
						omd.setContentEncoding("gzip");
						omd.setContentLength(bytes.length);

						AccessControlList acl = new AccessControlList();
						acl.grantPermission(GroupGrantee.AllUsers, Permission.Read);

						Upload myUpload = tx.upload(new PutObjectRequest("tecarta-stream", "cache/" + cacheKey + ".gz", new ByteArrayInputStream(bytes), omd).withAccessControlList(acl));

						// Or you can block the current thread and wait for your transfer to
						// to complete. If the transfer fails, this method will throw an
						// AmazonClientException or AmazonServiceException detailing the reason.
						myUpload.waitForCompletion();

						// After the upload is complete, call shutdownNow to release the resources.
						tx.shutdownNow();	
					} catch(Exception ex){
						ex.printStackTrace();
					}
				}});
		}		
	}
	
	protected void sendJSONResponseAndCache(HttpServletResponse response, Object object, String cacheKey) {
		try {
			String json = getJSON(object);
			
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().print(json);
		
			cacheResponse(cacheKey, json);
		}
		catch (Exception e) {
			sendError(response, e.getMessage());
		}
	}
	
	protected void sendJSONResponse(HttpServletResponse response, Object object) {
		sendJSONResponseAndCache(response, object, null);
	}
	
	protected String byteToBase64(byte[] bt) {
		Base64.Encoder encoder = Base64.getEncoder();
		String returnString = encoder.encodeToString(bt);
		return returnString;
	}
	
	private String readFile( String file ) throws IOException {
	    BufferedReader reader = new BufferedReader( new InputStreamReader( getServletContext().getResourceAsStream(file)));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();
	    String         ls = System.getProperty("line.separator");

	    while( ( line = reader.readLine() ) != null ) {
	        stringBuilder.append( line );
	        stringBuilder.append( ls );
	    }

	    reader.close();
	    
	    return stringBuilder.toString();
	}
	
	protected String getEmail(String emailType) {
		String email = "";
		
		try {
			String prefix = getServletContext().getContextPath();
			if (!prefix.endsWith("/")) {
				prefix += "/";
			}

			Properties properties = new Properties();
			InputStream input = getServletContext().getResourceAsStream("/templates/email." + emailType + ".xml");
			properties.loadFromXML(input);
			input.close();
						
			email = readFile("/templates/email.master.html");
			
			Enumeration <Object> keys = properties.keys();
			while (keys.hasMoreElements()) {
				String key = (String)keys.nextElement();
				email = email.replace("$" + key + "$", properties.getProperty(key));
			}
			
			email = email.replace("$year$", "" + Calendar.getInstance().get(Calendar.YEAR));
		}
		catch (Exception e) {
			getServletContext().log(e.getMessage());
		}

		return email;
	}
	
	protected synchronized String encode(String password, String saltKey) {
		String encodedPassword = null;
		try {
			byte[] plainBytes = password.getBytes("US-ASCII");
            byte[] saltBytes = saltKey.getBytes("US-ASCII");

            byte[] data = new byte[plainBytes.length + saltBytes.length];

            for (int i = 0; i < plainBytes.length; i++) {
            	data[i] = plainBytes[i];
            }
            
            for (int j = 0; j < saltBytes.length; j++) {
            	data[plainBytes.length + j] = saltBytes[j];
            }
            
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.reset();

			byte[] btPass = digest.digest(data);

			encodedPassword = byteToBase64(btPass);
		}
		catch (Exception e) {
		}

		return encodedPassword;
	}
	
	protected void sendError(HttpServletResponse response, String msg) {
		try {
			response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, msg);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public int getApiVersion(HttpServletRequest request) {
		return (int)request.getAttribute("apiVersion");
	}
	
	protected boolean isValidKey(HttpServletRequest request, HttpServletResponse response) {
		boolean ok = false;
		
		String version = request.getParameter("version");
		int apiVersion = (version == null || !NumberUtils.isNumber(version)) ? 0 : Integer.parseInt(version);
		
		if (apiVersion < 1) {
			try {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Minimum version of API is 1!");
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else {
			request.setAttribute("apiVersion", apiVersion);
			ok = true;
		}
		
	    return ok;
	}
}
