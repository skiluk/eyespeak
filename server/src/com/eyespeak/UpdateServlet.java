package com.eyespeak;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/update")
public class UpdateServlet extends ServletBase {
	private static final long serialVersionUID = 1L;

    @Override
	protected void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String userId = getParmString(request, "userId");
		String text = getParmString(request, "text");
		int utteranceId = getParmInt(request, "utteranceId");
		int responseId = getParmInt(request, "responseId");
		
		if (userId == null || utteranceId == -1) {
			sendError(response, "Incorrect parameters! (user id and utterance id are required)");
			return;
		}
		
		// does this user/utterance exist ?
		Connection conn = eyespeakDB.getConnection();

		PreparedStatement stmt = conn.prepareStatement("select utteranceId from utterances where userId = ? and utteranceId = ?");
		stmt.setString(1, userId);
		stmt.setInt(2, utteranceId);

		ResultSet rs = stmt.executeQuery();
		if (rs.next()) {
			// exists - ok to update
			if (responseId > 0) {
				// response tapped
				PreparedStatement insert = conn.prepareStatement("update responses set numSelected = numSelected + 1 where utteranceId = ? and responseId = ?");
				insert.setInt(1, utteranceId);
				insert.setInt(2, responseId);
				insert.execute();
				insert.close();
				
				sendJSONResponse(response, "response total updated");
			}
			else if (text != null && text.length() > 0) {
				// new response
				PreparedStatement insert = conn.prepareStatement("insert into responses (userId, utteranceId, response) VALUES (?,?,?)");
				insert.setString(1, userId);
				insert.setInt(2, utteranceId);
				insert.setString(3, text);
				insert.execute();
				insert.close();
				
				sendJSONResponse(response, "response added");
			}
			else {
				sendError(response, "Incorrect parameters! (either text or responseId are required)");
			}			
		}
		else {
			sendError(response, "user/utterance not found!");
		}

		rs.close();
		stmt.close();
		conn.close();
    }
}
