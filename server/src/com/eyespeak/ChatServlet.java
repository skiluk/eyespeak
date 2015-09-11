package com.eyespeak;

import java.io.File;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.alicebot.ab.Bot;
import org.alicebot.ab.Chat;

import com.eyespeak.model.ChatResponse;

@WebServlet("/chat")
public class ChatServlet extends ServletBase {
	private static final long serialVersionUID = 1L;

	public ChatServlet() {
		super();
	}

	protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String botname="alice2";
		String path = getServletContext().getRealPath(File.separator);
		Bot bot = new Bot(botname, path);
		Chat chatSession = new Chat(bot);

		String text = getParmString(request, "text");
		ChatResponse chatResponse = new ChatResponse();

		if (text != null && text.length() > 0) {
			chatResponse.text = text;
		}
		else {
			chatResponse.text = "Hello.";
		}
		
		String responseString = chatSession.multisentenceRespond(chatResponse.text);
		chatResponse.responses = responseString.split("\\|");
		
		sendJSONResponse(response, chatResponse);
	}
}
