package com.eyespeak;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.alicebot.ab.Bot;
import org.alicebot.ab.Category;
import org.alicebot.ab.Chat;

import com.eyespeak.model.ChatResponse;
import com.eyespeak.model.Option;
import com.mysql.jdbc.Statement;

@WebServlet("/chat")
public class ChatServlet extends ServletBase {
	private static final long serialVersionUID = 1L;
	HashMap <String, Bot> bots = new HashMap<>();
	public static DataSource botDatabase = null;

	public ChatServlet() {
		super();
	}

    @Override
    public void init() {
    	super.init();
    	botDatabase = eyespeakDB;
    }
    
    private String cleanUtterance(String utterance) {
    	StringBuilder sb = new StringBuilder();
    	
    	utterance = utterance.toUpperCase();
    	
    	for (int i = 0; i < utterance.length(); i++) {
    		char a = utterance.charAt(i);
    		
    		if ((a >= 'A' && a <= 'Z') || a == ' ') {
    			sb.append(a);
    		}
    	}
    	
    	return sb.toString();
    }
    
    @Override
	protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String userId = getParmString(request, "userId");
		String utterance = getParmString(request, "utterance");
		
		if (userId == null || utterance == null || utterance.length() == 0) {
			sendError(response, "Incorrect parameters! (user id and utterance is required)");
			return;
		}
		
		// get the bot...
		Bot bot = null;
		
		if (getParmInt(request, "reload") != 1) {
			bot = bots.get(userId);
		}
		
		if (bot == null) {
			bot = new Bot(userId, getServletContext().getRealPath(File.separator), "auto");
			bots.put(userId, bot);
		}
		
		Chat chatSession = new Chat(bot);

		ChatResponse chatResponse = new ChatResponse();
		chatResponse.utteranceText = utterance;
		
		String optionString = chatSession.multisentenceRespond(chatResponse.utteranceText);
		String options[] = optionString.split("\\|");
		
		for (String option : options) {
	        Matcher m = Pattern.compile("^:([0-9]+):(.*)$").matcher(option);
        	Option o = new Option();

        	if (m.matches()) {
	        	o.responseId = Integer.parseInt(m.group(1));
	        	o.responseText = m.group(2);
	        }
	        else {
	        	o.responseText = option;
	        }
        	
        	chatResponse.responses.add(o);
		}
		
		chatResponse.utteranceId = bot.getLastUtteranceId();
		
		if (chatResponse.utteranceId == -1) {
			// new utterance - add it
			utterance = cleanUtterance(utterance).trim();

			if (utterance.length() > 0) {
				Connection conn = eyespeakDB.getConnection();
				PreparedStatement stmt = conn.prepareStatement("select userId from utterances where userId = ? limit 1;");
				stmt.setString(1, userId);

				ResultSet rs = stmt.executeQuery();
				if (rs.next()) {

					PreparedStatement insert = conn.prepareStatement("insert into utterances (userId, utterance) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
					insert.setString(1, userId);
					insert.setString(2, utterance);
					insert.execute();

					ResultSet generatedKeys = insert.getGeneratedKeys();
					if (generatedKeys.next()) {
						chatResponse.utteranceId = generatedKeys.getLong(1);
						Category c = new Category(0, utterance, "*", "*", null, "" + chatResponse.utteranceId);
						bot.brain.addCategory(c);
					}

					insert.close();					
				}
				
				stmt.close();
				conn.close();
			}			
		}
		
		sendJSONResponse(response, chatResponse);
	}
}
